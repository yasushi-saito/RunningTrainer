package com.ysaito.runningtrainer;

import java.util.Stack;

import android.util.Log;

public class WorkoutIterator {
	private static class Entry {
		public Entry(JsonWorkout w) {
			workout = w;
			remainingRepeats = w.repeats;
		}
		public JsonWorkout workout;
		public int remainingRepeats;
	}
	private static final String TAG = "WorkoutIterator";
	private JsonWorkout mRoot;
	private final Stack<Entry> mStack = new Stack<Entry>();
	
	public WorkoutIterator(JsonWorkout workout) { 
		mRoot = workout; 
		addEntry(mRoot);
	}
	
	public void addEntry(JsonWorkout w) {
		if (w.type == JsonWorkout.TYPE_REPEATS) {
			if (w.children == null || w.children.length == 0 || w.repeats <= 0) {
				Log.d(TAG, "Invalid repeat spec: " + w.toString());
				return;
			}
			mStack.add(new Entry(w));
			addEntry(w.children[0]);
		} else {
			if (w.type != JsonWorkout.TYPE_INTERVAL) Util.crash(null, "Invalid workout" + w.toString());
			mStack.add(new Entry(w));
		}
	}
	
	public JsonWorkout getWorkout() {
		JsonWorkout w = mStack.get(mStack.size() - 1).workout;
		
		// The bottommost entry should be always an interval.
		if (Util.ASSERT_ENABLED && w.type != JsonWorkout.TYPE_INTERVAL) 
			Util.crash(null, "Invalid workout" + w.toString());
		return mStack.get(mStack.size() - 1).workout;
	}
	
	public boolean done() {
		return mStack.empty();
	}

	/**
	 * Find the position within parent.workout.children[] in which the child appears.
	 * The caller must guarantee that the child is in the list.
	 */
	private int findChildPosition(Entry parent, Entry child) {
		for (int i = 0; i < parent.workout.children.length; ++i) {
			if (parent.workout.children[i] == child.workout) return i;
		}
		Util.crash(null, "Child not found");
		return -1;
	}
	
	public void next() {
		Entry w = mStack.pop();
		if (mStack.empty()) {
			// all done
			return;
		}
		Entry parent = mStack.peek();
		
		int position = findChildPosition(parent, w);
		if (position < parent.workout.children.length - 1) {
			// do the next child
			addEntry(parent.workout.children[position + 1]);
		} else {
			// the parent is done
			--parent.remainingRepeats;
			if (parent.remainingRepeats > 0) {
				addEntry(parent.workout.children[0]);
			} else {
				next();
			}
		}
	}
}
