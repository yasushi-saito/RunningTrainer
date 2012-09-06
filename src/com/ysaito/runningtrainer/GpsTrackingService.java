package com.ysaito.runningtrainer;

import java.util.ArrayList;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class GpsTrackingService extends Service {
    /*
     * Communication channel between RecordingActivity and GpsTrackingService. Since both objects always
     * reside in one process and are run by the main thread, we just function calls to send GPS updates
     * the GpsTrackingService from RecordingActivity.
     */
    private static final ArrayList<RecordingActivity> mGpsListeners = new ArrayList<RecordingActivity>();
    public static void registerGpsListener(RecordingActivity listener) {
    	mGpsListeners.add(listener);
    }
    public static void unregisterGpsListener(RecordingActivity listener) {
    	mGpsListeners.remove(listener);
    }
    private static void postGpsEvent(
    			HealthGraphClient.JsonActivity activity,
    			ArrayList<HealthGraphClient.JsonWGS84> path) {
    	for (RecordingActivity listener : mGpsListeners) {
    		listener.onGpsUpdate(activity, path);
    	}
    }
    
	
	private static String TAG = "Gps";
	private LocationListener mLocationListener;
	private LocationManager mLocationManager;

	private HealthGraphClient.JsonActivity mRecord;
	private ArrayList<HealthGraphClient.JsonWGS84> mPath;
	private long mStartTime;
	
	private static int mInstanceSeq = 0;
	private int mId;
	
	@Override public String toString() {
		return "GpsService[" + mId + "]";
	}
	
	@Override
	public void onCreate() {
		mId = mInstanceSeq++;
		Toast.makeText(this, toString() + ": Created", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onCreate");
		
		mPath = new ArrayList<HealthGraphClient.JsonWGS84>();
		mRecord = new HealthGraphClient.JsonActivity();
		mRecord.type = "Running";  // TODO: allow changing
		mRecord.start_time = HealthGraphClient.utcMillisToString(System.currentTimeMillis());
		mRecord.notes = "Recorded by RunningTrainer";
		mStartTime =  System.currentTimeMillis();
		
		// Define a listener that responds to location updates
		mLocationListener = new LocationListener() {
			// Ignore location updates at intervals smaller than this limit.
			static final int MIN_RECORD_INTERVAL_MS = 1000;

			long mLastReportTime = 0;
			Location mLastReportedLocation = null;
			
			public void onLocationChanged(Location location) {
				// Called when a new location is found by the network location provider.
				// makeUseOfNewLocation(location);
				final long time = location.getTime();
				Log.d(TAG, "loc: " + time + "/" + location.toString());
				if (time < mLastReportTime + MIN_RECORD_INTERVAL_MS) return;
				onGpsLocationUpdate(time, location);
				
				mLastReportTime = time;
				mLastReportedLocation = location;
			}
			public void onStatusChanged(String provider, int status, Bundle extras) {
				Log.d(TAG, "Status: " + provider + ": " + status);
			}
			
			public void onProviderEnabled(String provider) {
				Log.d(TAG, "Provider Enabled: " + provider);
			}
			
			public void onProviderDisabled(String provider) {
				Log.d(TAG, "Provider Disabled: " + provider);
			}
		};
		
		// Register the listener with the Location Manager to receive location updates
		mLocationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
	}
	
	public void onDestroy() {
		Toast.makeText(this, toString() + ": Stopped", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onDestroy");
		mLocationManager.removeUpdates(mLocationListener);
	}
	
	@Override
	public void onStart(Intent intent, int startid) {
		Toast.makeText(this, toString() + ": Started", Toast.LENGTH_LONG).show();
		mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
		mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);
	}

	private void onGpsLocationUpdate(long now, Location newLocation) {
		HealthGraphClient.JsonWGS84 wgs = new HealthGraphClient.JsonWGS84();
		wgs.latitude = newLocation.getLatitude();
		wgs.longitude = newLocation.getLongitude();
		wgs.altitude = newLocation.getAltitude();
		if (mPath.size() == 0) {
			wgs.timestamp = 0;
			wgs.type = "start";
		} else {
			wgs.timestamp = (now - mStartTime) / 1000.0;
			wgs.type = "gps";
		}
		mPath.add(wgs);
		postGpsEvent(mRecord, mPath);
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}
}
