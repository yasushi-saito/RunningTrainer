/**
 * @author saito
 *
 */

package com.ysaito.runningtrainer.test;

import com.ysaito.runningtrainer.HealthGraphClient;
import com.ysaito.runningtrainer.RecordManager;
import com.ysaito.runningtrainer.RecordSummary;

import android.test.InstrumentationTestCase;

public class RecordManagerTest extends InstrumentationTestCase {
	public void testBasename() {
		HealthGraphClient.JsonActivity a = new HealthGraphClient.JsonActivity();
		a.total_distance = 12345.0;
		a.duration = 2345.0;
		String basename = RecordManager.generateBasename(345678, a);
		RecordSummary s = RecordManager.parseBasename(basename);
		assertEquals(345678, s.startTime);
		assertEquals(basename, s.basename);
		assertEquals(12345.0, s.totalDistance);
		assertEquals(2345.0, s.duration);
	}
	
	public void testDateFormatting() {
		// TODO: this test assumes PDT timezone
		final long now = 1347077029507L;
		assertEquals("Fri, 7 Sep 2012 21:03:49", HealthGraphClient.generateStartTimeString(now));
	}
}