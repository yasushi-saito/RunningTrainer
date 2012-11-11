package com.ysaito.runningtrainer;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

public class RecordReplayFragment extends MapWrapperFragment {
	private JsonActivity mRecord;
    private Menu mMenu = null;
	
	public RecordReplayFragment() { 
		super("RecordReplayFragment");
	}
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	setHasOptionsMenu(true);
    }

	@Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.recording_options_menu, menu);
    }
	
	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		if (MapMode.currentMode == MapMode.MAP) {
			menu.findItem(R.id.recording_map_view).setChecked(true);
			menu.findItem(R.id.recording_satellite_view).setChecked(false);		
		} else {
			menu.findItem(R.id.recording_map_view).setChecked(false);
			menu.findItem(R.id.recording_satellite_view).setChecked(true);		
		}
		mMenu = menu;
	}	

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.recording_map_view:
			if (mMenu.findItem(R.id.recording_map_view).isChecked()) {
				MapMode.currentMode = MapMode.SATTELITE;
			} else {
				MapMode.currentMode = MapMode.MAP;
			}
			break;
		case R.id.recording_satellite_view:
			if (mMenu.findItem(R.id.recording_satellite_view).isChecked()) {
				MapMode.currentMode = MapMode.MAP;
			} else {
				MapMode.currentMode = MapMode.SATTELITE;
			}
			break;
		}
		Activity child = getChildActivity();
		if (child != null) {
			((RecordReplayActivity)child).setMapMode(MapMode.currentMode);
		}
		return true;
	}
		
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
		((MainActivity)getActivity()).registerOnBackPressedListener(mOnBackPressedListener);
		super.onResume();
	}

	@Override public void onPause() {
		((MainActivity)getActivity()).unregisterOnBackPressedListener(mOnBackPressedListener);
		super.onPause();
	}
	
	public void setRecord(JsonActivity record) {
		mRecord = record;
		RecordReplayActivity activity = (RecordReplayActivity)getChildActivity();
		if (activity != null) {
			activity.setRecord(record);
		} else {
			// onCreateView hasn't been called yet. Delay the call to setRecord until later
		}
	}
}
