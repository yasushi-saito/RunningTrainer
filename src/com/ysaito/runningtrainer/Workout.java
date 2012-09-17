package com.ysaito.runningtrainer;

import java.util.ArrayList;

public class Workout {
	public static class Entry {
		// Empty base class
	}
	
	static public final double NO_FAST_TARGET_PACE = 0.0;
	static public final double NO_SLOW_TARGET_PACE = 9999.0;
	
	static public boolean hasFastTargetPace(double pace) {
		return pace > NO_FAST_TARGET_PACE;
	}
	static public boolean hasSlowTargetPace(double pace) {
		return pace < NO_SLOW_TARGET_PACE;
	}
	
	public static class Interval extends Entry {
		// At most one of duration or distance is positive.
		// If duration >= 0, the interval ends at the specified time
		// If distance >= 0, the interval ends at the specified distance
		// Else, the interval ends once the user presses the "Lap" button.
		public double duration = -1.0; 
		public double distance = -1.0;
		
		// The fast and slow ends of the pace range.
		public double fastTargetPace = NO_FAST_TARGET_PACE;
		public double slowTargetPace = NO_SLOW_TARGET_PACE;
	}
	
	public static class Repeat extends Entry {
		public int repeats = 0;
		public final ArrayList<Interval> entries = new ArrayList<Interval>();
	}
	
	public ArrayList<Entry> entries = new ArrayList<Entry>();
}
