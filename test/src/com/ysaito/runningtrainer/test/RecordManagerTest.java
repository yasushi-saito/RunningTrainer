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
		String basename = RecordManager.generateBasename(345678, 12345.0, 2345, null);
		RecordSummary s = RecordManager.parseBasename(basename);
		assertEquals(345678, s.startTime);
		assertEquals(basename, s.basename);
		assertEquals(12345.0, s.distance);
		assertEquals(2345.0, s.duration);
	}

	public void testBasenameWithRunkeeperPath() {
		String basename = RecordManager.generateBasename(345678, 12345.0, 2345, "/foobar/52");
		RecordSummary s = RecordManager.parseBasename(basename);
		assertEquals(345678, s.startTime);
		assertEquals(basename, s.basename);
		assertEquals(12345.0, s.distance);
		assertEquals(2345.0, s.duration);
		assertEquals("/foobar/52", s.runkeeperPath);		
	}
	
	public void testDateFormatting() {
		// TODO: this test assumes PDT timezone
		final long now = 1347077029507L;
		assertEquals("Fri, 7 Sep 2012 21:03:49", HealthGraphClient.generateStartTimeString(now));
	}
	
	public void doTestSanitize(String s) throws Exception {
		String encoded = RecordManager.sanitizeString(s);
		assertFalse(encoded.contains("/"));
		assertFalse(encoded.contains(":"));
		assertEquals(s, RecordManager.unsanitizeString(encoded));
	}
	public void testSanitize() throws Exception {
		doTestSanitize("/");		
		doTestSanitize("");
		doTestSanitize("a");
		doTestSanitize("aoeu234");
		doTestSanitize("/aoeu234");		
		doTestSanitize("aoeu234:");		
		doTestSanitize("/aoeu:234%");				
		doTestSanitize("/aoeu/234/");						
	}
}