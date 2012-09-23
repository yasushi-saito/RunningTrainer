/**
 * @author saito
 *
 */

package com.ysaito.runningtrainer.test;

import com.ysaito.runningtrainer.HealthGraphClient;
import com.ysaito.runningtrainer.FileManager;
import com.ysaito.runningtrainer.RecordSummary;
import com.ysaito.runningtrainer.Workout;
import com.ysaito.runningtrainer.WorkoutIterator;

import android.test.InstrumentationTestCase;

public class WorkoutIteratorTest extends InstrumentationTestCase {
	private Workout newRepeats(int numChildren) {
		Workout w = new Workout();
		w.type = Workout.TYPE_REPEATS;
		w.repeats = 1;
		w.children = new Workout[numChildren];
		return w;	
	}

	private Workout newInterval(double duration, double distance, double fast, double slow) {
		Workout w = new Workout();
		w.type = Workout.TYPE_INTERVAL;
		w.duration = duration;
		w.distance = distance;
		w.fastTargetPace = fast;
		w.slowTargetPace = slow;
		return w;	
		
	}

	public String iteratorToString(WorkoutIterator iter) {
		StringBuilder b = new StringBuilder();
		while (!iter.done()) {
			b.append(iter.getWorkout().toString());
			b.append("\n");
			iter.next();
		}
		return b.toString();
	}

	
	public void testEmpty() {
		Workout w = newRepeats(0);
		WorkoutIterator iter = new WorkoutIterator(w);
		assertEquals("", iteratorToString(iter));
	}
	
	public void testSimple() {
		Workout root = newRepeats(2);
		root.children[0] = newInterval(1.0, 2.0, 3.0, 4.0);
		root.children[1] = newInterval(2.0, 3.0, 4.0, 5.0);		
		WorkoutIterator iter = new WorkoutIterator(root);
		assertEquals(
				"Interval: duration=1.0, distance=2.0, fast=3.0, slow=4.0\n" +
				"Interval: duration=2.0, distance=3.0, fast=4.0, slow=5.0\n",
				iteratorToString(iter));
	}

	public void testRepeats() {
		Workout root = newRepeats(3);
		root.children[0] = newInterval(1.0, 2.0, 3.0, 4.0);
		
		Workout repeat = newRepeats(2);
		repeat.repeats = 2;
		repeat.children[0] = newInterval(2.0, 3.0, 4.0, 5.0);
		repeat.children[1] = newInterval(4.0, 5.0, 6.0, 7.0);
		root.children[1] = repeat;

		root.children[2] = newInterval(3.0, 4.0, 5.0, 6.0);
		
		WorkoutIterator iter = new WorkoutIterator(root);
		assertEquals(
				"Interval: duration=1.0, distance=2.0, fast=3.0, slow=4.0\n" +
				"Interval: duration=2.0, distance=3.0, fast=4.0, slow=5.0\n" +
				"Interval: duration=4.0, distance=5.0, fast=6.0, slow=7.0\n" +
				"Interval: duration=2.0, distance=3.0, fast=4.0, slow=5.0\n" +
				"Interval: duration=4.0, distance=5.0, fast=6.0, slow=7.0\n" +				
				"Interval: duration=3.0, distance=4.0, fast=5.0, slow=6.0\n",
				iteratorToString(iter));
	}
	
}