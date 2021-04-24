package com.xeno.goo.library;

import net.minecraftforge.common.util.NonNullConsumer;

import java.lang.ref.WeakReference;
import java.util.function.BiConsumer;

/**
 * Implementation of {@link NonNullConsumer} that weakly references a parent object.
 * Designed for use in {@link net.minecraftforge.common.util.LazyOptional#addListener(NonNullConsumer)},
 * to prevent the capability owner from keeping a reference to the listener TE and preventing garbage collection.
 */
public abstract class WeakConsumerWrapper {

	/**
	 * Creates a new weak consumer wrapper
	 *
	 * @param te
	 * 		Object to wrap in a WeakReference and passed to the first parameter of the BiConsumer if non-null at the time of calling
	 * @param consumer
	 * 		Consumer using the TE and the consumed value. Should not use a lambda reference to an object that may need to be garbage collected
	 */
	public static <TE, C> NonNullConsumer<C> of(TE te, BiConsumer<TE, C> consumer) {

		final WeakReference<TE> ref = new WeakReference<>(te);
		return (c) -> {
			TE that = ref.get();
			if (that != null) {
				consumer.accept(that, c);
			}
		};
	}
}
