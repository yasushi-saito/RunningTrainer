/**
 * @author saito
 *
 */

package com.ysaito.runningtrainer.test;

import com.ysaito.runningtrainer.Settings;
import com.ysaito.runningtrainer.Util;

import android.test.InstrumentationTestCase;

public class UtilTest extends InstrumentationTestCase {
	public void testDurationSpeech() {
		Settings.unit = Settings.METRIC;
		assertEquals("0.15 kilometers", Util.distanceToSpeechText(150.0));
		assertEquals("0.15 kilometers", Util.distanceToSpeechText(155.0));
		assertEquals("1.55 kilometers", Util.distanceToSpeechText(1555.0));
		assertEquals("15.5 kilometers", Util.distanceToSpeechText(15555.0));
		assertEquals("155 kilometers", Util.distanceToSpeechText(155555.0));
	}
}