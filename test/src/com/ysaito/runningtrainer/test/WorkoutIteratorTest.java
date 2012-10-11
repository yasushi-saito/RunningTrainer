/**
 * @author saito
 *
 */

package com.ysaito.runningtrainer.test;

import com.ysaito.runningtrainer.HealthGraphClient;
import com.ysaito.runningtrainer.FileManager;
import com.ysaito.runningtrainer.RecordSummary;
import com.ysaito.runningtrainer.JsonWorkout;
import com.ysaito.runningtrainer.WorkoutIterator;

import android.test.InstrumentationTestCase;

public class WorkoutIteratorTest extends InstrumentationTestCase {
	private JsonWorkout newRepeats(int numChildren) {
		JsonWorkout w = new JsonWorkout();
		w.type = JsonWorkout.TYPE_REPEATS;
		w.repeats = 1;
		w.children = new JsonWorkout[numChildren];
		return w;	
	}

	private JsonWorkout newInterval(double duration, double distance, double fast, double slow) {
		JsonWorkout w = new JsonWorkout();
		w.type = JsonWorkout.TYPE_INTERVAL;
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
		JsonWorkout w = newRepeats(0);
		WorkoutIterator iter = new WorkoutIterator(w);
		assertEquals("", iteratorToString(iter));
	}
	
	public void testSimple() {
		JsonWorkout root = newRepeats(2);
		root.children[0] = newInterval(1.0, 2.0, 3.0, 4.0);
		root.children[1] = newInterval(2.0, 3.0, 4.0, 5.0);		
		WorkoutIterator iter = new WorkoutIterator(root);
		assertEquals(
				"Interval: duration=1.0, distance=2.0, fast=3.0, slow=4.0\n" +
				"Interval: duration=2.0, distance=3.0, fast=4.0, slow=5.0\n",
				iteratorToString(iter));
	}

	public void testRepeats() {
		JsonWorkout root = newRepeats(3);
		root.children[0] = newInterval(1.0, 2.0, 3.0, 4.0);
		
		JsonWorkout repeat = newRepeats(2);
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