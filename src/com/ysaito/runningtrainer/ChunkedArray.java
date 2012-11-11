package com.ysaito.runningtrainer;

import java.util.Iterator;

/**
 * ChunkedArray is like LinkedList, but stores up to 128 objects in one block. It is more memory-efficient and faster than
 * LinkedList.
 *
 */
public class ChunkedArray<T> implements Iterable<T> {
	static private final int MAX_CHUNK_SIZE = 128; 
	static class Chunk {
		Chunk() { size = 0; values = new Object[MAX_CHUNK_SIZE]; next = null; }
		int size;
		Object[] values;
		Chunk prev, next;
	}

	private int mSize;
	private Chunk mHead, mTail;
	
	public ChunkedArray() {
		mHead = new Chunk();
		mTail = mHead;
		mSize = 0; 
	}
	
	public final void clear() {
		mTail = mHead;
		mSize = 0;
		mHead.size = 0;
		mHead.prev = null;
		mHead.next = null;
	}
	
	public final int size() { return mSize; }
	
	/**
	 * 
	 * @return The first element in the array
	 * @pre size() > 0
	 */
	public final T front() {
		if (mSize == 0) {
			throw new IndexOutOfBoundsException("front() called on empty ChunkedArray");
		}
		return (T)mHead.values[0];
	}
	
	/**
	 * 
	 * @return The last element in the array
	 * @pre size() > 0
	 */
	public final T back() {
		if (mSize == 0) {
			throw new IndexOutOfBoundsException("back() called on empty ChunkedArray");
		}
		return (T)mTail.values[mTail.size - 1];
	}

	/**
	 * Append an object to the end of the list.
	 */
	public final void add(T object) {
		if (mTail.size >= MAX_CHUNK_SIZE) {
			Chunk chunk = new Chunk();
			mTail.next = chunk;
			chunk.prev = mTail;
			mTail = chunk;
		}
		mTail.values[mTail.size] = object;
		++mTail.size;
		++mSize;
	}

	/**
	 * Remove the last object in the list
	 * @pre size() > 0
	 */
	public final void removeLast() {
		if (mSize == 0) {
			throw new IndexOutOfBoundsException("blah");
		}
		--mSize;
		if (mSize == 0) {
			clear();
		} else {
			--mTail.size;
			if (mTail.size == 0) {
				mTail = mTail.prev;
				mTail.next = null;
			}
		}
	}
	
	private final class ForwardIterator implements Iterator<T> {
		private Chunk mChunk;
		private int mIndex;
		ForwardIterator() {
			mChunk = mHead;
			mIndex = 0;
			if (mSize == 0) {
				mChunk = null;  // hasNext becomes false.
			}
		}
		public T next() {
			T value = (T)mChunk.values[mIndex];
			++mIndex;
			if (mIndex >= mChunk.size) {
				mChunk = mChunk.next;
				mIndex = 0;
			}
			return value;
		}
		public boolean hasNext() {
			return mChunk != null;
		}
		public void remove() {
			;
		}
	}

	/**
	 * Get the index'th object. Beware, this function incurs O(size()) cost.
	 * @pre index < size()
	 */
	public final T getAtIndex(int index) {
		if (index < 0 || index >= mSize) {
			throw new IndexOutOfBoundsException("getAtIndex: index=" + index + " size=" + mSize);
		}
		Chunk c = mHead;
		for (;;) {
			if (index < c.size) {
				return (T)c.values[index];
			}
			index -= c.size;
			c = c.next;
		}
	}

	private final class ReverseIterator implements Iterator<T> {
		private Chunk mChunk;
		private int mIndex;
		ReverseIterator() {
			if (mSize == 0) {
				mChunk = null;  // hasNext becomes false.
			} else {
				mChunk = mTail;
				mIndex = mChunk.size - 1;
			}
		}
		public final T next() {
			T value = (T)mChunk.values[mIndex];
			--mIndex;
			if (mIndex < 0) {
				mChunk = mChunk.prev;
				if (mChunk != null) mIndex = mChunk.size - 1;
			}
			return value;
		}
		public final boolean hasNext() {
			return mChunk != null;
		}
		public final void remove() {
			;
		}
	}

	/**
	 * @return an iterator that returns objects in order
	 */
	public final Iterator<T> iterator() {
		return new ForwardIterator();
	}
	
	/**
	 * @return an iterator that returns objects in reverse order
	 */
	public final Iterator<T> reverseIterator() {
		return new ReverseIterator();
	}
}
