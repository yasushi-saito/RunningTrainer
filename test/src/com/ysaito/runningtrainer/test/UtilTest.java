/**
 * @author saito
 *
 */

package com.ysaito.runningtrainer.test;

import com.ysaito.runningtrainer.Settings;
import com.ysaito.runningtrainer.Util;
import android.test.InstrumentationTestCase;
import android.util.Log;

public class UtilTest extends InstrumentationTestCase {
	public void testDurationSpeech() {
		Settings.unit = Settings.Unit.METRIC;
		assertEquals("0.15 kilometers", Util.distanceToSpeechText(150.0));
		assertEquals("0.15 kilometers", Util.distanceToSpeechText(155.0));
		assertEquals("1.55 kilometers", Util.distanceToSpeechText(1555.0));
		assertEquals("15.5 kilometers", Util.distanceToSpeechText(15555.0));
		assertEquals("155 kilometers", Util.distanceToSpeechText(155555.0));
	}
	
	public void testPathAggregator() {
		Util.PathAggregator aggr = new Util.PathAggregator();
		Util.PathAggregatorResult r = aggr.addLocation(0.0, 100.0, 100.0, 0.0);
		assertEquals(Util.PauseType.RUNNING, r.pauseType);
		assertEquals(0.0, r.absTime);
		
		r = aggr.addLocation(2.0, 100.0, 100.0, 0.0);
		assertEquals(Util.PauseType.RUNNING, r.pauseType);
		assertEquals(0.0, r.deltaDistance);
		assertEquals(2.0, r.absTime);

		// pause detection window is 3 seconds. So if the user doesn't move for 3.1 seconds, 
		// addr should detect a pause going back to time 0.0.
		r = aggr.addLocation(3.1, 100.0, 100.0, 0.0);
		assertEquals(Util.PauseType.PAUSE_STARTED, r.pauseType);
		assertEquals(0.0, r.deltaDistance);
		assertEquals(0.0, r.absTime);

		r = aggr.addLocation(4.1, 100.00002, 100.0, 0.0);
		assertEquals(Util.PauseType.PAUSE_CONTINUING, r.pauseType);
		assertEquals(0.0, r.deltaDistance);

		r = aggr.addLocation(5.1, 100.00005, 100.0, 0.0);  // 0.00005 is roughly equal to 6 meters, which is more than the pause threshold
		assertEquals(Util.PauseType.PAUSE_ENDED, r.pauseType);
		assertTrue(r.deltaDistance > 5.58);
		assertTrue(r.deltaDistance < 5.60);		

		r = aggr.addLocation(6.0, 100.00006, 100.0, 0.0);  // 0.00005 is roughly equal to 6 meters, which is more than the pause threshold
		assertEquals(Util.PauseType.RUNNING, r.pauseType);
		assertTrue(r.deltaDistance > 1.11);
		assertTrue(r.deltaDistance < 1.12);		
	}
}