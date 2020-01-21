package one.microstream.viewer;

import one.microstream.persistence.binary.types.ViewerException;
import one.microstream.persistence.binary.types.ViewerObjectDescription;
import one.microstream.storage.types.EmbeddedStorageManager;

public class StorageRestAdapter2 extends EmbeddedStorageRestAdapter
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	private final StorageViewDataConverter converter;

	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public StorageRestAdapter2(final EmbeddedStorageManager storage)
	{
		super(storage);
		this.converter = new StorageViewDataConverter();
	}

	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	public String getObject(
		final long objectId,
		final long dataOffset,
		final long dataLength,
		final boolean resolveReferences,
		final long referenceOffset,
		final long referenceLength)
	{

		if(dataOffset < 0) throw new ViewerException("invalid parameter dataOffset");
		if(dataLength < 1) throw new ViewerException("invalid parameter dataLength");
		if(referenceOffset < 0) throw new ViewerException("invalid parameter referenceOffset");
		if(referenceLength < 1) throw new ViewerException("invalid parameter referenceLength");


		final ViewerObjectDescription description = super.getStorageObject(objectId);
		if(resolveReferences)
		{
			description.resolveReferences(referenceOffset, referenceLength, this);
		}

		final SimpleObjectDescription preprocessed = description.postProcess(dataOffset, dataLength);
		return this.converter.convert(preprocessed);
	}

	public String getUserRoot()
	{
		final ViewerRootDescription root = super.getRoot();
		return this.converter.convert(root);
	}

	@Override
	public String getTypeDictionary()
	{
		return super.getTypeDictionary();		 
	}
}
