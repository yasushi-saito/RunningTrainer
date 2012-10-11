package com.ysaito.runningtrainer;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class RecordingFragment extends MapWrapperFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	setHasOptionsMenu(true);
    }

    private Menu mMenu = null;
    private MapMode mMapMode = MapMode.MAP;
    
	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		if (mMapMode == MapMode.MAP) {
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
				mMapMode = MapMode.SATTELITE;
			} else {
				mMapMode = MapMode.MAP;
			}
			break;
		case R.id.recording_satellite_view:
			if (mMenu.findItem(R.id.recording_satellite_view).isChecked()) {
				mMapMode = MapMode.MAP;
			} else {
				mMapMode = MapMode.SATTELITE;
			}
			break;
		}
		Activity child = getChildActivity();
		if (child != null) {
			((RecordingActivity)child).setMapMode(mMapMode);
		}
		return true;
	}
		
	@Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.recording_options_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }
	
	protected Class<?> getActivityClass() {
		return RecordingActivity.class;
	}
}
