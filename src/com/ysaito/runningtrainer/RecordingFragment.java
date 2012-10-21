package com.ysaito.runningtrainer;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

public class RecordingFragment extends MapWrapperFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	setHasOptionsMenu(true);
    	mActivity = (MainActivity)getActivity();
    }

    private MainActivity mActivity;
    private Menu mMenu = null;
    private MapMode mMapMode = MapMode.MAP;

    @Override
    public View onCreateView(
    		LayoutInflater inflater, 
    		ViewGroup container,
            Bundle savedInstanceState) {
    	View view = super.onCreateView(inflater, container, savedInstanceState);
    	
    	// The child activity is initialized on the call to onCreateView. Report the identity of the outer activity
    	// so that it can set the GPS status on the action bar.
    	((RecordingActivity)getChildActivity()).setMainActivity(mActivity);
    	return view;
    }
    
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
	
	@Override
	public void onResume() {
		super.onResume();
	}
	
	@Override
	public void onPause() {
		super.onPause();
		mActivity.setGpsStatus(GpsStatusView.HIDE_GPS_VIEW);
	}
	
	protected Class<?> getActivityClass() {
		return RecordingActivity.class;
	}
}
