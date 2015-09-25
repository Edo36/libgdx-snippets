package com.badlogic.gdx.utils;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;

/**
 * Array utility functions.
 */
public class ArrayUtils {

	/**
	 * Returns an {@link Iterable} interface wrapped around an array, in ascending order.
	 */
	public static <T> Iterable<T> asIterable(T[] array) {
		return asIterable(array, false);
	}

	/**
	 * Returns an {@link Iterable} interface wrapped around an array, in ascending or descending order.
	 *
	 * <pre>
	 * {@code
	 * Object[] someArray = ...;
	 * ArrayUtils.asIterable(someArray, false).forEach(element -> {});
	 * }
	 * </pre>
	 */
	public static <T> Iterable<T> asIterable(T[] array, boolean descending) {
		return () -> descending ? new DescendingArrayIterator<>(array) : new ArrayIterator<>(array);
	}

	/**
	 * Returns a second interface to the {@link Iterable} passed, in ascending order.
	 */
	@SuppressWarnings("unchecked")
	public static <T> Iterable<T> asIterable(Iterable<T> iterable) {
		return asIterable(iterable, false);
	}

	/**
	 * Returns a second interface to the {@link Iterable} passed.
	 * <p>
	 * The argument must be the result of a previous call to this function, or to
	 * {@link ArrayUtils#asIterable(Object[], boolean)}.
	 */
	@SuppressWarnings("unchecked")
	public static <T> Iterable<T> asIterable(Iterable<T> iterable, boolean descending) {
		if (iterable instanceof ArrayIterator<?>) {
			return () -> descending
					? new DescendingArrayIterator<>((AbstractArrayIterator<T>) iterable)
					: new ArrayIterator<>((AbstractArrayIterator<T>) iterable);
		}
		throw new IllegalArgumentException("Iterable must be of equal type.");
	}

	private abstract static class AbstractArrayIterator<T> implements Iterator<T> {

		protected final T[] array;
		protected int index = 0;

		AbstractArrayIterator(T[] array) {
			this.array = array;
		}

	}

	private static class ArrayIterator<T> extends AbstractArrayIterator<T> {

		ArrayIterator(T[] array) {
			super(array);
		}

		ArrayIterator(AbstractArrayIterator<T> other) {
			super(other.array);
		}

		@Override
		public boolean hasNext() {
			return index < array.length;
		}

		@Override
		public T next() {
			return array[index++];
		}
	}

	private static class DescendingArrayIterator<T> extends AbstractArrayIterator<T> {

		DescendingArrayIterator(T[] array) {
			super(array);
			index = array.length - 1;
		}

		DescendingArrayIterator(AbstractArrayIterator<T> other) {
			super(other.array);
			index = other.array.length - 1;
		}

		@Override
		public boolean hasNext() {
			return index >= 0;
		}

		@Override
		public T next() {
			return array[index--];
		}
	}

	/**
	 * Returns a {@link Collection} interface wrapped around an array.
	 * <p>
	 * The collection returned does not support any operation manipulating the array. Its main purpose (and
	 * difference to {@link ArrayUtils#asIterable(Object[])}) is to expose the {@link Collection#size()} function.
	 */
	public static <T> Collection<T> asCollection(T[] array) {
		return new ArrayCollection<>(array);
	}

	/**
	 * Returns a second interface to the {@link Collection} passed.
	 * <p>
	 * The argument must be the result of a previous call to this function, or to
	 * {@link ArrayUtils#asCollection(Object[])}.
	 */
	public static <T> Collection<T> asCollection(Collection<T> collection) {
		if (collection instanceof ArrayCollection<?>) {
			return new ArrayCollection<>((ArrayCollection<T>) collection);
		}
		throw new IllegalArgumentException("Collection must be of equal type.");
	}

	private static class ArrayCollection<T> extends AbstractCollection<T> {

		private final T[] array;

		ArrayCollection(T[] array) {
			this.array = array;
		}

		ArrayCollection(ArrayCollection<T> other) {
			this.array = other.array;
		}

		@Override
		public Iterator<T> iterator() {
			return new ArrayIterator<>(array);
		}

		@Override
		public int size() {
			return array.length;
		}
	}

}
