package com.ysaito.runningtrainer;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class RecordReplayFragment extends MapWrapperFragment {
	private HealthGraphClient.JsonActivity mRecord;
	
	protected Class<?> getActivityClass() {
		return RecordReplayActivity.class;
	}

	@Override 
    public View onCreateView(
    		LayoutInflater inflater, 
    		ViewGroup container,
            Bundle savedInstanceState) {
		View view = super.onCreateView(inflater, container, savedInstanceState);
		
		if (mRecord != null) {
			RecordReplayActivity activity = (RecordReplayActivity)getChildActivity();
			activity.setRecord(mRecord);
		}
		return view;
	}

	final MainActivity.OnBackPressedListener mOnBackPressedListener = new MainActivity.OnBackPressedListener() {
		public boolean onBackPressed() {
			MainActivity activity = (MainActivity)getActivity();
			activity.setFragmentForTab("Log",
					activity.findOrCreateFragment("com.ysaito.runningtrainer.RecordListFragment"));
			return true;
		}
	};
	
	@Override public void onResume() {
		Log.d(TAG, "REPLAY RESUME");
		((MainActivity)getActivity()).registerOnBackPressedListener(mOnBackPressedListener);
		super.onResume();
	}

	@Override public void onPause() {
		Log.d(TAG, "REPLAY PAUSE");
		((MainActivity)getActivity()).unregisterOnBackPressedListener(mOnBackPressedListener);
		super.onPause();
	}
	
	public void setRecord(HealthGraphClient.JsonActivity record) {
		mRecord = record;
		RecordReplayActivity activity = (RecordReplayActivity)getChildActivity();
		if (activity != null) {
			activity.setRecord(record);
		} else {
			// onCreateView hasn't been called yet. Delay the call to setRecord until later
		}
	}
}
