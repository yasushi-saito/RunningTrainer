package com.ysaito.runningtrainer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class RecordingActivity extends MapActivity {
    static final String TAG = "Recording";

    static public class MyOverlay extends Overlay {
        private ArrayList<GeoPoint> mPoints;
        private double mCurrentAccuracy; // the latest report on GPS accuracy (meters)
        private GeoPoint mCurrentLocation;
        
        public MyOverlay() {
            mPoints = new ArrayList<GeoPoint>();
        }

    	public void clearPath() {
    		mPoints.clear();
    		mCurrentLocation = null;
    	}

        public void updatePath(ArrayList<HealthGraphClient.JsonWGS84> path) {
        	while (mPoints.size() < path.size()) {
        		HealthGraphClient.JsonWGS84 point = path.get(mPoints.size());
        		GeoPoint p = new GeoPoint((int)(point.latitude * 1e6), (int)(point.longitude * 1e6));
        		mPoints.add(p);
        	}
        }

        public void setCurrentLocation(HealthGraphClient.JsonWGS84 location, double accuracy) {
        	mCurrentLocation = new GeoPoint((int)(location.latitude * 1e6), (int)(location.longitude * 1e6));
        	mCurrentAccuracy = accuracy;
        }
        
        @Override
        public boolean draw(Canvas canvas, MapView mapView, boolean shadow, long when) {
            boolean v = super.draw(canvas, mapView, shadow, when);
            if (shadow) return v;
            
            Projection projection = mapView.getProjection();
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            if (mPoints.size() > 0) {
            	paint.setColor(0xff000080);
            	paint.setStyle(Paint.Style.STROKE);
            	paint.setStrokeWidth(5);

            	if (mPoints.size() > 1) {
            		Path path = new Path();
            		for (int i = 0; i < mPoints.size(); i++) {
            			GeoPoint gPointA = mPoints.get(i);
            			Point pointA = new Point();
            			projection.toPixels(gPointA, pointA);
            			if (i == 0) { //This is the start point
            				path.moveTo(pointA.x, pointA.y);
            			} else {
            				path.lineTo(pointA.x, pointA.y);
            			}
            		}
            		canvas.drawPath(path, paint);
            	}
            }
            if (mCurrentLocation != null) {
            	Point pointA = new Point();
            	projection.toPixels(mCurrentLocation, pointA);
            	
            	paint.setColor(0xff000080);
            	paint.setStyle(Paint.Style.STROKE);
            	paint.setStrokeWidth(8);
            	paint.setColor(0xff0000ff);
            	
            	float radius = Math.max(10.0f, projection.metersToEquatorPixels((float)mCurrentAccuracy));
            	canvas.drawCircle(pointA.x, pointA.y, radius, paint);
            }
            return v;
        }
        
    };

    private static class Stats {
    	// The time activity started. Millisecs since 1970/1/1
    	// TODO: this should be the first GPS fix after the start, not the time of the start
    	private final long mStartTime;
    	
    	// The index of the last path[] element that was handled by updatePath.
    	// We assume that the array path[] passed to updatePath() simply adds new points at the end on 
    	// every call
    	private int mLastPathSegment = 0;
    	
    	// Cumulative distance of points in path[0..mLastPathSegment].
    	private double mDistance = 0;

    	/** 
    	 * Remember up to last 10 seconds worth of GPS measurements.
    	 */
    	private class Event {
    		public Event(double d, long t) { distance = d; absTime = t; }
    		
    		// The distance delta from the previous Event in mRecentEvents
    		public final double distance;
    		
    		// # of milliseconds since 1970/1/1
    		public final long absTime;
    	}
    	ArrayDeque<Event> mRecentEvents;
    	
    	private final float[] mTmp = new float[1];
    	
    	Stats() {
    		mStartTime = System.currentTimeMillis();
    		mRecentEvents = new ArrayDeque<Event>();
    	}
    	public final long getStartTime() { return mStartTime; }
    	
    	/**
    	 * @return the cumulative distance traveled, in meters
    	 */
    	public final double getDistance() { return mDistance; }
    	
    	/**
    	 * @return The number of milliseconds elapsed.
    	 */
    	public final long getDurationMillis() { return System.currentTimeMillis() - mStartTime; }

    	/**
    	 * @return The average pace since the beginning of the activity
    	 */
    	public final double getPace() {
    		if (mRecentEvents.size() == 0) return 0.0;
    		final long minTimestamp = mRecentEvents.getFirst().absTime;
    		final long maxTimestamp = mRecentEvents.getLast().absTime;
    		final long delta = maxTimestamp - minTimestamp;
    		if (delta <= 0 || mDistance <= 0.0) return 0.0;
    		return delta / 1000.0 / mDistance;
    	}
    	
    	/**
    	 * @return The pace over the last 10 seconds, as seconds / meter
    	 */
    	public final double getCurrentPace() {
    		if (mRecentEvents.size() == 0) return 0.0;
    		
    		/** Compute the distance the user moved in the last ~15 seconds. */
    		Iterator<Event> iter = mRecentEvents.iterator();
    		
    		final long now = System.currentTimeMillis();
    		long minTimestamp = now + 100000;
    		long maxTimestamp = 0;
    		double totalDistance = 0.0;
    		Log.d(TAG, "PACE: now=" + now);
    		while (iter.hasNext()) {
    			Event event = iter.next();
    			if (event.absTime >= now - 15 * 1000) {
    				minTimestamp = Math.min(minTimestamp, event.absTime);
    				maxTimestamp = Math.max(maxTimestamp, event.absTime);
    				totalDistance += event.distance;
    				Log.d(TAG, "PACE: log abs=" + event.absTime + " distance=" + event.distance);
    			}
    		}
    		double pace;
    		if (totalDistance <= 0.0) {
    			pace = 0.0;
    		} else {
    			pace = (maxTimestamp - minTimestamp) / 1000.0 / totalDistance;
    		}
    		Log.d(TAG, "PACE: timedelta=" + (maxTimestamp - minTimestamp) + " distance=" + totalDistance + " pace=" + pace);
    		return pace;
    		/*return String.format("now-max=%d delta=%d n=%d total=%f pace=%f", 
    				now - maxTimestamp, maxTimestamp - minTimestamp, n, totalDistance, 
    				pace);*/
    	}
    	
    	public final void updatePath(ArrayList<HealthGraphClient.JsonWGS84> path) {
    		if (path.size() > mLastPathSegment + 1) {
    			HealthGraphClient.JsonWGS84 lastPoint = path.get(mLastPathSegment);
    			mLastPathSegment++;
    			for (;;) {
    				HealthGraphClient.JsonWGS84 thisPoint = path.get(mLastPathSegment);
    				Location.distanceBetween(lastPoint.latitude, lastPoint.longitude,
    						thisPoint.latitude, thisPoint.longitude, mTmp);
    				mDistance += mTmp[0];
    				
    				final long absTime = (long)(mStartTime + thisPoint.timestamp * 1000);
    				mRecentEvents.addLast(new Event(mTmp[0], absTime));
    				lastPoint = thisPoint;
    				if (mLastPathSegment >= path.size() - 1) break;
    				++mLastPathSegment;
    			}
    			
    			// Drop events that are more than 30 seconds old. But keep at least one record so that	
    			// if the user stops moving, we can still display the current pace.
    			final long lastRetained = System.currentTimeMillis() - 30 * 1000;
    			while (mRecentEvents.size() > 1) {
    				Event e = mRecentEvents.peekFirst();
    				if (e.absTime >= lastRetained) break;
    				mRecentEvents.removeFirst();
    			}
    		}
    	}
    }
    
    static public class StatsView {
    	private final TextView mValueView;
    	private final TextView mTitleView;
    	private final String mDisplayType;
    	/**
    	 * 
    	 * @param value_view_id
    	 * @param title_view_id
    	 * 
    	 * @param displayType One of the values defined in display_type_values in array.xml. For example,
    	 * "total_distance".
    	 */
    	public StatsView(Activity parent, int value_view_id, int title_view_id, String displayType) {
    		mValueView = (TextView)parent.findViewById(value_view_id);
    		mTitleView = (TextView)parent.findViewById(title_view_id);
    		mDisplayType = displayType;
    	}
    	
    	public void update(
    			Stats totalStats, Stats userLapStats, Stats autoLapStats,
    			Settings settings) {
    		String value = "         ";
    		String title = "YY";
    		
    		// newerStats is the newer of {user,auto}LapStats.
    		Stats newerLapStats = userLapStats;
    		if (autoLapStats != null &&
    				(autoLapStats == null || autoLapStats.getStartTime() < autoLapStats.getStartTime())) {
    			newerLapStats = autoLapStats;
    		}
    		if (mDisplayType.equals("none")) {
    			;
    		} else if (mDisplayType.equals("total_distance")) {
    			title = "Total distance (" + Util.distanceUnitString(settings) + ")";
    			if (totalStats != null) {
    				value = Util.distanceToString(totalStats.getDistance(), settings);
    			}
    		} else if (mDisplayType.equals("total_elapsed")) {
    			title = "Total elapsed";
    			if (totalStats != null) {
    				value = Util.durationToString(totalStats.getDurationMillis() / 1000.0);
    			}
    		} else if (mDisplayType.equals("total_pace")) {
    			title = "Average pace (" + Util.paceUnitString(settings)+ ")";
    			if (totalStats != null) {
    				value = Util.paceToString(totalStats.getPace(), settings);
    			}
    		} else if (mDisplayType.equals("current_pace")) {
    			title = "Current pace (" + Util.paceUnitString(settings)+ ")";
    			if (totalStats != null) {
    				value = Util.paceToString(totalStats.getCurrentPace(), settings);
    			}
    		} else if (mDisplayType.equals("lap_distance")) {
    			title = "Lap distance (" + Util.distanceUnitString(settings) + ")";
    			if (newerLapStats != null) {
    				value = Util.distanceToString(newerLapStats.getDistance(), settings);
    			}
    		} else if (mDisplayType.equals("lap_elapsed")) {
    			title = "Lap elapsed";
    			if (newerLapStats != null) {
    				value = Util.durationToString(newerLapStats.getDurationMillis() / 1000.0);
    			}
    		} else if (mDisplayType.equals("lap_pace")) {
    			title = "Lap pace (" + Util.paceUnitString(settings)+ ")";
    			if (newerLapStats != null) {
    				value = Util.paceToString(newerLapStats.getPace(), settings);
    			}
    		} else if (mDisplayType.equals("auto_lap_distance")) {
    			title = "Autolap distance (" + Util.distanceUnitString(settings) + ")";
    			if (autoLapStats != null) {
    				value = Util.distanceToString(autoLapStats.getDistance(), settings);
    			}
    		} else if (mDisplayType.equals("auto_lap_elapsed")) {
    			title = "Autolap elapsed";
    			if (autoLapStats != null) {
    				value = Util.durationToString(autoLapStats.getDurationMillis() / 1000.0);
    			}
    		} else if (mDisplayType.equals("auto_lap_pace")) {
    			title = "Autolap pace (" + Util.paceUnitString(settings)+ ")";
    			if (autoLapStats != null) {
    				value = Util.paceToString(autoLapStats.getPace(), settings);
    			}
    		} else {
    			value = "Unknown display type: " + mDisplayType;
    			title = "???";
    		}

    		mValueView.setText(value);
    		mTitleView.setText(title);
    	}
    }
    
    private MyOverlay mMapOverlay;
    private MapView mMapView;
    private StatsView mStatsViews[];

    private Button mPauseResumeButton;
    private Button mStartStopButton;
    private Button mLapButton;    
    private Settings mSettings;  // User pref settings at the start of the activity
    private RecordManager mRecordManager;

    private static final int STOPPED = 0;
    private static final int PAUSED = 1;
    private static final int RUNNING = 2;
    private int mRecordingState = STOPPED;
    private Stats mTotalStats = null;  // stats since the beginning of the activity
    private Stats mUserLapStats = null;    // stats since the start of the last manual lap
    private Stats mAutoLapStats = null;    // stats since the start of the last auto lap, i.e., the one that renews every 1mile or 1km.
    
    HealthGraphClient.JsonActivity mLastReportedActivity = null;
    ArrayList<HealthGraphClient.JsonWGS84> mLastReportedPath = null;
    
    public void onGpsUpdate(
    		HealthGraphClient.JsonActivity activity,
    		ArrayList<HealthGraphClient.JsonWGS84> path) {
    	mLastReportedActivity = activity;
    	mLastReportedPath = path;
    	mMapOverlay.updatePath(path);
    	if (mTotalStats != null) mTotalStats.updatePath(path);
    	if (mUserLapStats != null) mUserLapStats.updatePath(path);
    	if (mAutoLapStats != null) mAutoLapStats.updatePath(path);    	
    	mMapView.invalidate();
    }
    
    private LocationManager mLocationManager;
    private LocationListener mLocationListener; 
    private Timer mTimer;

    public void onEverySecond() {
    	if (mTotalStats == null) return;  // this shouldn't happen.

    	for (int i = 0; i < mStatsViews.length; ++i) {
    		mStatsViews[i].update(mTotalStats, mUserLapStats, mAutoLapStats, mSettings);
    	}
    }

    @Override public void onPause() {
    	super.onPause();
        mLocationManager.removeUpdates(mLocationListener);
        stopTimer();
    }
    
    @Override public void onResume() {
    	super.onResume();
    	mSettings = Settings.getSettings(this);
        // Define a listener that responds to location updates
        mLocationListener = new LocationListener() {
    		public void onLocationChanged(Location location) {
    			HealthGraphClient.JsonWGS84 wgs = new HealthGraphClient.JsonWGS84();
    			wgs.latitude = location.getLatitude();
    			wgs.longitude = location.getLongitude();
    			wgs.altitude = location.getAltitude();
    			mMapOverlay.setCurrentLocation(wgs, location.getAccuracy());
    			mMapView.invalidate();
			}
			public void onProviderDisabled(String provider) { }
			public void onProviderEnabled(String provider) { }
			public void onStatusChanged(String provider, int status, Bundle extras) {
			}        
        };
        // Register the listener with the Location Manager to receive location updates
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);

        mStatsViews = new StatsView[6];
        mStatsViews[0] = new StatsView(this, R.id.view0, R.id.view0_title, mSettings.viewTypes[0]);
        mStatsViews[1] = new StatsView(this, R.id.view1, R.id.view1_title, mSettings.viewTypes[1]);        
        mStatsViews[2] = new StatsView(this, R.id.view2, R.id.view2_title, mSettings.viewTypes[2]);        
        mStatsViews[3] = new StatsView(this, R.id.view3, R.id.view3_title, mSettings.viewTypes[3]);        
        mStatsViews[4] = new StatsView(this, R.id.view4, R.id.view4_title, mSettings.viewTypes[4]);        
        mStatsViews[5] = new StatsView(this, R.id.view5, R.id.view5_title, mSettings.viewTypes[5]);
        for (int i = 0; i < mStatsViews.length; ++i) {
        	mStatsViews[i].update(null,  null, null, mSettings);
        }
        updateTimerIfNecessary();
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.recording);
        mRecordManager = new RecordManager(this);
        mMapOverlay = new MyOverlay();
        mMapView = (MapView)findViewById(R.id.map_view);
        mMapView.getOverlays().add(mMapOverlay);
        mMapView.setBuiltInZoomControls(true);

        mLocationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);

        mPauseResumeButton = (Button)findViewById(R.id.pause_resume_button);
        mPauseResumeButton.setEnabled(false); // pause/resume is disabled unless the recorder is running
        mPauseResumeButton.setOnClickListener(new Button.OnClickListener() {
        	public void onClick(View v) {
        		if (mRecordingState == RUNNING) {
        			onPauseButtonPress();
        			mPauseResumeButton.setText(R.string.resume);
        		} else if (mRecordingState == PAUSED) {
        			// Either running or paused
        			onResumeButtonPress();
        			mPauseResumeButton.setText(R.string.pause); 
        		} else {
        			// Stopped. This shouldn't happen, since the button is disabled in this state.
        		}
        	}
        });

        mLapButton = (Button)findViewById(R.id.lap_button);
        mLapButton.setOnClickListener(new Button.OnClickListener() {
        	public void onClick(View v) {
        		mUserLapStats = new Stats();
        	}
        });
        
        mStartStopButton = (Button)findViewById(R.id.start_stop_button);
        mStartStopButton.setOnClickListener(new Button.OnClickListener() {
        	public void onClick(View v) {
        		if (mRecordingState == STOPPED) {
        			mLapButton.setEnabled(true);
        			mPauseResumeButton.setEnabled(true);
        			mPauseResumeButton.setText(R.string.pause);
        			mStartStopButton.setText(R.string.stop); 
        			onStartButtonPress();
        		} else {
        			// Either running or paused
        			mLapButton.setEnabled(false);
        			mPauseResumeButton.setEnabled(false);
        			mPauseResumeButton.setText(R.string.pause);
        			mStartStopButton.setText(R.string.start); 
        			onStopButtonPress();
        		}
        	}
        });
        GpsTrackingService.registerGpsListener(this);
        if (GpsTrackingService.isGpsServiceRunning()) {
        	mRecordingState = RUNNING;
        	mStartStopButton.setText(R.string.stop); 
        } else {
        	mRecordingState = STOPPED;
        	mStartStopButton.setText(R.string.start);
        }
    }

    @Override
    public void onDestroy() {
    	super.onDestroy();
    	GpsTrackingService.unregisterGpsListener(this);
    }

    private void updateTimerIfNecessary() {
    	stopTimer();
    	
    	if (mRecordingState == RUNNING) {
    		mTimer = new Timer();
    		mTimer.schedule(new TimerTask() {
    			@Override public void run() {
    				runOnUiThread(new Runnable() {
    					public void run() { onEverySecond(); }
    				});
    			}
    		}, 0, 1000);
    	}
    }
    
    private void stopTimer() {
    	if (mTimer != null) {
    		mTimer.cancel();
    		mTimer.purge();
    		mTimer = null;
    	}
    }
    
    void onStartButtonPress() {
        mRecordingState = RUNNING;
    	mMapOverlay.clearPath();
    	mMapView.invalidate();
    	mTotalStats = new Stats();
    	mUserLapStats = null;
    	mAutoLapStats = null;
    	GpsTrackingService.startGpsServiceIfNecessary(this);
    	updateTimerIfNecessary();    	
    }

    private void onStopButtonPress() {
    	mRecordingState = STOPPED;
    	updateTimerIfNecessary();
    	GpsTrackingService.stopGpsServiceIfNecessary(this);
    	if (mTotalStats != null && mLastReportedPath != null && mLastReportedPath.size() >= 1) {
    		HealthGraphClient.JsonWGS84 last = mLastReportedPath.get(mLastReportedPath.size() - 1);
    		HealthGraphClient.JsonWGS84 wgs = new HealthGraphClient.JsonWGS84();
    		wgs.latitude = last.latitude;
    		wgs.longitude = last.longitude;
    		wgs.altitude = last.altitude;
    		wgs.type = "end";
    		wgs.timestamp = (System.currentTimeMillis()- mTotalStats.getStartTime()) / 1000.0;
    		mLastReportedPath.add(wgs);
    		mLastReportedActivity.path = new HealthGraphClient.JsonWGS84[mLastReportedPath.size()];
    		for (int i = 0; i < mLastReportedPath.size(); ++i) mLastReportedActivity.path[i] = mLastReportedPath.get(i);
    		
    		mLastReportedActivity.duration = wgs.timestamp;
    		HealthGraphClient.JsonWGS84 lastLocation = null;
    		
    		float[] distance = new float[1];
    		for (HealthGraphClient.JsonWGS84 location : mLastReportedActivity.path) {
    			if (lastLocation != null) {
    				Location.distanceBetween(lastLocation.latitude, lastLocation.longitude,
    						location.latitude, location.longitude,
    						distance);
    				mLastReportedActivity.total_distance += distance[0];
    			}
    			lastLocation = location;
    		}
    		mRecordManager.addRecord(mTotalStats.getStartTime(), mLastReportedActivity);
    	}
    }	
    private void onPauseButtonPress() {
    	mRecordingState = PAUSED;
    	updateTimerIfNecessary();
    }

    private void onResumeButtonPress() {
    	mRecordingState = RUNNING;
    	updateTimerIfNecessary();
    }

    @Override
    public boolean isRouteDisplayed() { return false; }

    @Override
        public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }


}
