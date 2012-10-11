package com.ysaito.runningtrainer;

import android.os.Bundle;
import android.preference.PreferenceFragment;

public class SettingsFragment extends PreferenceFragment {
	private final static String TAG = "Settings";
	
    @Override public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	Plog.d(TAG, "onCreate");
    	addPreferencesFromResource(R.xml.preferences);
    }
}
