/**
 * @author saito
 *
 */

package com.ysaito.runningtrainer.test;

import com.ysaito.runningtrainer.Plog;

import android.test.ActivityTestCase;
import android.util.Log;

public class PlogTest extends ActivityTestCase {
	private String TAG = "PlogTest";
	public void testBasic() {
		Plog.Init(getActivity(), 0.5);
		Plog.d(TAG, "FOOBAR");
		Log.d(TAG, "Wrote log in "+ Plog.getLogFile().getPath());
		// TODO: check the log file contents.
		try {
			Thread.sleep(4000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}