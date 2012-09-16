package com.ysaito.runningtrainer;

import java.util.ArrayList;

public class Workout {
	public static class Entry {
		// Empty base class
	}
	
	public static class Interval extends Entry {
		// At most one of duration or distance is positive.
		// If duration >= 0, the interval ends at the specified time
		// If distance >= 0, the interval ends at the specified distance
		// Else, the interval ends once the user presses the "Lap" button.
		public double duration = -1.0; 
		public double distance = -1.0;
		
		// The pace range lowTargetPace is the faster end of the range.
		public double lowTargetPace = 0.0;
		public double highTargetPace = 0.0;		
	}
	
	public static class Repeat extends Entry {
		public int repeats = 0;
		public final ArrayList<Interval> entries = new ArrayList<Interval>();
	}
	
	public ArrayList<Entry> entries = new ArrayList<Entry>();
}
