package one.microstream.afs.hazelcast;

import static one.microstream.X.checkArrayRange;
import static one.microstream.X.notNull;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.projection.Projections;
import com.hazelcast.query.Predicates;
import com.hazelcast.query.QueryConstants;

import one.microstream.afs.blobstore.BlobStoreConnector;
import one.microstream.afs.blobstore.BlobStorePath;
import one.microstream.exceptions.IORuntimeException;
import one.microstream.io.ByteBufferInputStream;
import one.microstream.io.LimitedInputStream;


public interface HazelcastConnector extends BlobStoreConnector
{

	public static HazelcastConnector New(
		final HazelcastInstance hazelcast
	)
	{
		return new Default(
			notNull(hazelcast)
		);
	}


	public static class Default
	extends    BlobStoreConnector.Abstract<List<Object>>
	implements HazelcastConnector
	{
		/*
		 * Blobs are managed as Lists:
		 * LinkedList[                // needed to get projection working ("this.getFirst")
		 *     ArrayList[             // metadata
		 *         String identifier
		 *         Long   start
		 *         Long   end
		 *     ],
		 *     byte[] data
		 * ]
		 */

		private static List<Object> createBlob(
			final String identifier,
			final Long   start     ,
			final Long   end       ,
			final byte[] data
		)
		{
			final List<Object> metadata = new ArrayList<>(3);
			metadata.add(identifier);
			metadata.add(start);
			metadata.add(end);

			final List<Object> blob = new LinkedList<>();
			blob.add(metadata);
			blob.add(data);
			return blob;
		}

		private static byte[] data(
			final List<Object> blob
		)
		{
			return (byte[])blob.get(1);
		}

		private static String identifier(
			final List<Object> metadata
		)
		{
			return (String)metadata.get(0);
		}

		private static Long start(
			final List<Object> metadata
		)
		{
			return (Long)metadata.get(1);
		}

		private static Long end(
			final List<Object> metadata
		)
		{
			return (Long)metadata.get(2);
		}

		private final static long MAX_UPLOAD_BLOB_BYTES = 10_000_000L;

		private final HazelcastInstance hazelcast;

		Default(
			final HazelcastInstance hazelcast
		)
		{
			super();
			this.hazelcast = hazelcast;
		}

		private IMap<String, List<Object>> map(final BlobStorePath file)
		{
			return this.hazelcast.getMap(file.container());
		}

		@Override
		protected String key(
			final List<Object> metadata
		)
		{
			return identifier(metadata);
		}

		@Override
		protected long size(
			final List<Object> metadata
		)
		{
			return end(metadata) - start(metadata) + 1L;
		}

		@Override
		protected Stream<List<Object>> blobs(
			final BlobStorePath file
		)
		{
			return this.map(file).project(
				Projections.<Entry<String, List<Object>>, List<Object>>singleAttribute(
					QueryConstants.THIS_ATTRIBUTE_NAME.value() + ".getFirst"
				),
				Predicates.regex(
					QueryConstants.KEY_ATTRIBUTE_NAME.value(),
					blobKeyRegex(toBlobKeyPrefix(file))
				)
			)
			.stream()
			.sorted(this.blobComparator())
			;
		}

		@Override
		protected void readBlobData(
			final BlobStorePath file        ,
			final List<Object>  metadata    ,
			final ByteBuffer    targetBuffer,
			final long          offset      ,
			final long          length
		)
		{
			final byte[] data = data(
				this.map(file).get(identifier(metadata))
			);
			targetBuffer.put(
				data,
				checkArrayRange(offset),
				checkArrayRange(length)
			);
		}

		@Override
		protected boolean internalDeleteFile(
			final BlobStorePath file
		)
		{
			final IMap<String, List<Object>> map = this.map(file);
			final AtomicBoolean deleted = new AtomicBoolean();
			this.blobs(file).forEach(
				metadata -> {
					map.delete(identifier(metadata));
					deleted.set(true);
				}
			);
			return deleted.get();
		}

		@Override
		protected long internalWriteData(
			final BlobStorePath                  file         ,
			final Iterable<? extends ByteBuffer> sourceBuffers
		)
		{
			      long                       nextBlobNr         = this.nextBlobNr(file);
			final long                       totalSize          = this.totalSize(sourceBuffers);
			final IMap<String, List<Object>> map                = this.map(file);
			final ByteBufferInputStream      buffersInputStream = ByteBufferInputStream.New(sourceBuffers);
			      long                       offset             = this.fileSize(file);
			      long                       available          = totalSize;
			while(available > 0)
			{
				final long currentBatchSize = Math.min(
					available,
					MAX_UPLOAD_BLOB_BYTES
				);

				try(LimitedInputStream limitedInputStream = LimitedInputStream.New(
					new BufferedInputStream(buffersInputStream),
					currentBatchSize
				))
				{
					final int    batchSize = checkArrayRange(currentBatchSize);
					final byte[] batch     = new byte[batchSize];
					      int    read      = 0;
					do
					{
						read += limitedInputStream.read(batch, read, batch.length - read);
					}
					while(read < batchSize);

					final String       identifier = toBlobKey(file, nextBlobNr++);
					final List<Object> blob       = createBlob(
						identifier,
						offset,
						offset + currentBatchSize - 1,
						batch
					);
					map.set(identifier, blob);
				}
				catch(final IOException e)
				{
					throw new IORuntimeException(e);
				}

				offset    += currentBatchSize;
				available -= currentBatchSize;
			}

			return totalSize;
		}

		@Override
		protected void internalMoveFile(
			final BlobStorePath sourceFile,
			final BlobStorePath targetFile
		)
		{
			final IMap<String, List<Object>> sourceMap = this.map(sourceFile);
			final IMap<String, List<Object>> targetMap = this.map(targetFile);
			final AtomicInteger              nr        = new AtomicInteger();
			this.blobs(sourceFile).forEach(metadata -> {
				this.copyBlob(metadata, targetFile, sourceMap, targetMap, nr);
				sourceMap.delete(identifier(metadata));
			});
		}

		@Override
		protected long internalCopyFile(
			final BlobStorePath sourceFile,
			final BlobStorePath targetFile
		)
		{
			final IMap<String, List<Object>> sourceMap = this.map(sourceFile);
			final IMap<String, List<Object>> targetMap = this.map(targetFile);
			final AtomicInteger              nr        = new AtomicInteger();
			final AtomicLong                 size      = new AtomicLong();
			this.blobs(sourceFile).forEach(metadata ->{
				this.copyBlob(metadata, targetFile, sourceMap, targetMap, nr);
				size.addAndGet(this.size(metadata));
			});
			return size.get();
		}

		private void copyBlob(
			final List<Object>               metadata  ,
			final BlobStorePath              targetFile,
			final IMap<String, List<Object>> sourceMap ,
			final IMap<String, List<Object>> targetMap ,
			final AtomicInteger              nr
		)
		{
			final List<Object> blob             = sourceMap.get(identifier(metadata));
			final String       targetIdentifier = toBlobKey(targetFile, nr.getAndIncrement());
			final List<Object> newBlob          = createBlob(
				targetIdentifier,
				start(metadata),
				end(metadata),
				data(blob)
			);
			targetMap.put(targetIdentifier, newBlob);
		}

		@Override
		protected synchronized void internalClose()
		{
			this.hazelcast.shutdown();
		}

	}

}
