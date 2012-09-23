package com.ysaito.runningtrainer;

public class Workout {
	static public final double NO_FAST_TARGET_PACE = 0.0;
	static public final double NO_SLOW_TARGET_PACE = 9999.0;
	
	static public boolean hasFastTargetPace(double pace) {
		return pace > NO_FAST_TARGET_PACE;
	}
	static public boolean hasSlowTargetPace(double pace) {
		return pace < NO_SLOW_TARGET_PACE;
	}
	
	public Workout() {
		
	}
	
	public Workout(Workout other) {
		id = other.id;
		name = other.name;
		type = other.type;
		repeats = other.repeats;
		duration = other.duration;
		distance = other.distance;
		if (other.children != null) {
			children = new Workout[other.children.length];
			for (int i = 0; i < other.children.length; ++i) {
				children[i] = new Workout(other.children[i]);
			}
		}
	}
	
	@Override public String toString() {
		StringBuilder b = new StringBuilder();
		if (type == TYPE_REPEATS) {
			b.append("Repeats: repeats=");
			b.append(repeats);
			b.append(", #child=" + children.length);
		} else {
			b.append("Interval: duration=");
			b.append(duration);
			b.append(", distance=");
			b.append(distance);
			b.append(", fast=");
			b.append(fastTargetPace);
			b.append(", slow=");
			b.append(slowTargetPace);
		}
		return b.toString();
	}

	// Unique id of this object. The value is the time (seconds since 1970) of creation.
	public long id = 0;
	
	// The name displayed in the workout list screen. May not be unique.
	public String name = "foo";

	static public int TYPE_REPEATS = 1;
	static public int TYPE_INTERVAL = 2;
	
	// There are three types of Workout objects:
	//
	// "Root" is at the root of a workout tree. It simply contains the list of
	// child Workout objects. There is exactly one Root node in any workout tree (naturally at the root position) 
	// 
	// "Repeats" contains a sequence of
	// child workouts, with a repeat count. 
	//
	// "Interval" is one interval workout entry. It specifies either a distance or duration, and
	// the target pace range during the distance or duration.
	//
	// Note that this class is translated to and from JSON strings, so we don't want to use
	// inheritance to represent these types of objets.
	public int type;  // One of TYPE_XXX.

	// Meaningful only when type=="Root" or type=="Repeats"
	public Workout[] children;

    // Number of the times the children[] are repeated, sequentially.
	// INVARIANT: >0 if type==TYPE_REPEATS
	// INVARIANT: ==0 if type!=TYPE_REPEATS
	public int repeats = 0;

	// The remaining fields are meaningful only when type=="Interval"
	
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
