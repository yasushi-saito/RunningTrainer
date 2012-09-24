/**
 * @author saito
 *
 */

package com.ysaito.runningtrainer.test;

import com.ysaito.runningtrainer.HealthGraphClient;
import com.ysaito.runningtrainer.FileManager;
import com.ysaito.runningtrainer.RecordSummary;

import android.test.InstrumentationTestCase;

public class RecordManagerTest extends InstrumentationTestCase {
	public void testBasename() {
		FileManager.ParsedFilename summary = new FileManager.ParsedFilename();
		summary.putLong("foo", 12345);
		summary.putString("bar", "str");
		summary.putString("x", "k");		
		String basename = summary.getBasename();
		FileManager.ParsedFilename f = FileManager.parseBasename(basename);
		assertEquals(12345, f.getLong("foo", -1));
		assertEquals("str", f.getString("bar", ""));
		assertEquals("k", f.getString("x", ""));
	}

	public void testDateFormatting() {
		// TODO: this test assumes PDT timezone
		final double now = 1347077029.507;
		assertEquals("Fri, 7 Sep 2012 21:03:49", HealthGraphClient.generateStartTimeString(now));
	}
	
	public void doTestSanitize(String s) throws Exception {
		String encoded = FileManager.sanitizeString(s);
		assertFalse(encoded.contains("/"));
		assertFalse(encoded.contains(":"));
		assertEquals(s, FileManager.unsanitizeString(encoded));
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