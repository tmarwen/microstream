package one.microstream.com.binarydynamic;

import java.nio.ByteOrder;

import one.microstream.collections.types.XGettingEnum;
import one.microstream.com.ComConnection;
import one.microstream.com.ComPersistenceAdaptor;
import one.microstream.com.binary.ComPersistenceAdaptorBinary;
import one.microstream.persistence.binary.types.BinaryPersistenceFoundation;
import one.microstream.persistence.types.PersistenceIdStrategy;
import one.microstream.util.BufferSizeProvider;

public final class ComPersistenceAdaptorBinaryDynamicCreator extends ComPersistenceAdaptorBinary.Creator.Abstract<ComConnection>
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	protected ComPersistenceAdaptorBinaryDynamicCreator(
		final BinaryPersistenceFoundation<?> foundation        ,
		final BufferSizeProvider             bufferSizeProvider
	)
	{
		super(foundation, bufferSizeProvider);
	}
		
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	@Override
	public ComPersistenceAdaptor<ComConnection> createPersistenceAdaptor(
		final PersistenceIdStrategy  hostIdStrategyInitialization,
		final XGettingEnum<Class<?>> entityTypes                 ,
		final ByteOrder              hostByteOrder               ,
		final PersistenceIdStrategy  hostIdStrategy
	)
	{
		return ComPersistenceAdaptorBinaryDynamic.New(
			this.foundation()           ,
			this.bufferSizeProvider()   ,
			hostIdStrategyInitialization,
			entityTypes                 ,
			hostByteOrder               ,
			hostIdStrategy
		);
	}
}