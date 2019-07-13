package one.microstream.entity;

import java.util.function.Consumer;

import one.microstream.collections.BulkList;
import one.microstream.collections.types.XCollection;
import one.microstream.collections.types.XIterable;

/*
 * Concept to separate the basic aspects of what defines an entity into separate instances of different layers:
 * - identity (a never to be replaced instance representing an entity in terms of references to it)
 * - logic (nestable in an arbitrary number of dynamically created logic layers, e.g. logging, locking, versioning, etc.)
 * - data (always immutable)
 * 
 * Entity graphs are constructed by stricly only referencing identity instances (the "outer shell" of an entity),
 * while every inner layer instance is unshared. This also allows the actual data instance to be immutable, while
 * at the same time leaving referencial integrity of an entity graph intact.
 * 
 * Everything starting with a _ could be synthetic, meaning generated by an IDE plugin.
 * The "_" is just an arbitrary prefix, of course and could be replaced by anything else or simple be configurable.
 * 
 * The concept could even be used to manage concurrency:
 * Multiple currently reading threads would query and use the current data instance of an entity.
 * A modification would replace the data instance with a new one without immediate effect to the other threads.
 * Version meta values (like a sequence number or a timestamp) could even be kept and checked in an intermediate
 * layer without poluting the business entity with technical values.
 * 
 * While the layers admittedly introduce considerable technical complexity and runtime overhead,
 * this concept might be the holy grail of entity handling for nearly all requirements regarding
 * cross cutting concerns / aspects.
 *
 */

/**
 * A mutable entity. Mutations of the entity's data only happen by providing another instance of that entity
 * that contains the desired new data.
 * 
 * @author TM
 */
public interface Entity extends ReadableEntity
{
	@SuppressWarnings("unchecked")
	public static <E> E identity(final E instance)
	{
		return instance instanceof Entity
			? (E)((Entity)instance).$entity()
			: instance
		;
	}
	
	@SuppressWarnings("unchecked")
	public static <E> E data(final E instance)
	{
		return instance instanceof Entity
			? (E)((Entity)instance).$data()
			: instance
		;
	}
	
	public static <E> boolean updateData(E entity, E data)
	{
		return entity instanceof Entity && data instanceof Entity
			? ((Entity)entity).$updateData((Entity)data)
			: false;
	}
	
	public default boolean isSameIdentity(final Entity newData)
	{
		return newData.$entity() == this.$entity();
	}
	
	public default void validateIdentity(final Entity newData)
	{
		// empty default implementation
		if(!isSameIdentity(newData))
		{
			// (10.12.2017 TM)EXCP: proper exception
			throw new RuntimeException("Entity identity mismatch.");
		}
	}
	
	@Override
	public Entity $entity();
	
	@Override
	public Entity $data();
	
	boolean $updateData(Entity data);
	

	
	public interface Creator<E extends Entity, C extends Creator<E, C>> extends XIterable<EntityLayerProvider>
	{
		public E create();
		
		public E createData(E identityInstance);
		
		public E createData();
		
		public C entity(E identity);
		
		public C copy(E other);
		
		@SuppressWarnings("unchecked")
		public default C $()
		{
			return (C)this;
		}
		
		
		public default C $addLayer(final EntityLayerProvider layerProvider)
		{
			synchronized(this)
			{
				final XCollection<EntityLayerProvider> layerProviders = this.$layers();
				synchronized(layerProviders)
				{
					layerProviders.add(layerProvider);
				}
			}
			
			return $();
		}
		
		public default C $addLayer(final EntityLayerProviderProvider layerProviderProvider)
		{
			return this.$addLayer(layerProviderProvider.provideEntityLayerProvider());
		}
		
		@Override
		public default <P extends Consumer<? super EntityLayerProvider>> P iterate(final P procedure)
		{
			synchronized(this)
			{
				final XCollection<EntityLayerProvider> layerProviders = this.$layers();
				synchronized(layerProviders)
				{
					layerProviders.iterate(procedure);
				}
			}
			
			return procedure;
		}
				
		public XCollection<EntityLayerProvider> $layers();
				
		
		
		public abstract class Abstract<E extends Entity, C extends Creator<E, C>>
		implements Entity.Creator<E, C>
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////
			
			private final BulkList<EntityLayerProvider> layerProviders = BulkList.New();
			private       E                             entityIdentity                 ;

			
			
			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////

			@Override
			public XCollection<EntityLayerProvider> $layers()
			{
				return this.layerProviders;
			}
			
			protected Entity dispatchDataInstance(final Entity dataInstance)
			{
				Entity innerLayer = dataInstance;
				for(final EntityLayerProvider lp : this.layerProviders)
				{
					innerLayer = lp.provideEntityLayer(innerLayer);
				}
				
				return innerLayer;
			}
			
			@SuppressWarnings("unchecked")
			@Override
			public E create()
			{
				final EntityLayerIdentity entity = this.createEntityInstance();
				
				final Entity data          = this.createData((E)entity.$entity());
				final Entity innerInstance = this.dispatchDataInstance(data);
				
				entity.$setInner(innerInstance);
				
				return (E)entity.$entity();
			}
			
			@Override
			public C entity(final E entity)
			{
				this.entityIdentity = entity;
				return $();
			}
			
			@SuppressWarnings("unchecked")
			@Override
			public E createData()
			{
				return this.createData((E)this.entityIdentity.$entity());
			}
			
			protected abstract EntityLayerIdentity createEntityInstance();
			
			
			@Override
			public <P extends Consumer<? super EntityLayerProvider>> P iterate(final P procedure)
			{
				return Entity.Creator.super.iterate(procedure);
			}
						
		}
		
	}
	
}
