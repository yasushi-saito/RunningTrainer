package com.ysaito.runningtrainer;

import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class SettingsFragment extends PreferenceFragment {
	private final static String TAG = "Settings";
	
    @Override public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	Plog.d(TAG, "onCreate");
    	addPreferencesFromResource(R.xml.preferences);
    }
    
    @Override
    public View onCreateView(
    		LayoutInflater inflater, 
    		ViewGroup container,
            Bundle savedInstanceState) {
    	Plog.d(TAG, "onCreateView");
    	return super.onCreateView(inflater,  container, savedInstanceState);
    }
}
