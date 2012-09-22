package com.ysaito.runningtrainer;

import java.io.File;
import java.util.ArrayList;

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
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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
    	
    	public void update(LapStats totalStats, LapStats userLapStats, LapStats autoLapStats) {
    		String value = "         ";
    		String title = "YY";
    		
    		// newerStats is the newer of {user,auto}LapStats.
    		LapStats newerLapStats = userLapStats;
    		if (autoLapStats != null &&
    				(autoLapStats == null || autoLapStats.getStartTimeSeconds() < autoLapStats.getStartTimeSeconds())) {
    			newerLapStats = autoLapStats;
    		}
    		if (mDisplayType.equals("none")) {
    			;
    		} else if (mDisplayType.equals("total_distance")) {
    			title = "Total " + Util.distanceUnitString();
    			if (totalStats != null) {
    				value = Util.distanceToString(totalStats.getDistance());
    			}
    		} else if (mDisplayType.equals("total_duration")) {
    			title = "Total time";
    			if (totalStats != null) {
    				value = Util.durationToString(totalStats.getDurationSeconds());
    			}
    		} else if (mDisplayType.equals("total_pace")) {
    			title = "Avg pace";
    			if (totalStats != null) {
    				value = Util.paceToString(totalStats.getPace());
    			}
    		} else if (mDisplayType.equals("current_pace")) {
    			title = "Cur pace";
    			if (totalStats != null) {
    				value = Util.paceToString(totalStats.getCurrentPace());
    			}
    		} else if (mDisplayType.equals("lap_distance")) {
    			title = "Lap " + Util.distanceUnitString();
    			if (newerLapStats != null) {
    				value = Util.distanceToString(newerLapStats.getDistance());
    			}
    		} else if (mDisplayType.equals("lap_duration")) {
    			title = "Lap time";
    			if (newerLapStats != null) {
    				value = Util.durationToString(newerLapStats.getDurationSeconds());
    			}
    		} else if (mDisplayType.equals("lap_pace")) {
    			title = "Lap pace";
    			if (newerLapStats != null) {
    				value = Util.paceToString(newerLapStats.getPace());
    			}
    		} else if (mDisplayType.equals("auto_lap_distance")) {
    			title = "Autolap " + Util.distanceUnitString();
    			if (autoLapStats != null) {
    				value = Util.distanceToString(autoLapStats.getDistance());
    			}
    		} else if (mDisplayType.equals("auto_lap_duration")) {
    			title = "Autolap time";
    			if (autoLapStats != null) {
    				value = Util.durationToString(autoLapStats.getDurationSeconds());
    			}
    		} else if (mDisplayType.equals("auto_lap_pace")) {
    			title = "Autolap pace";
    			if (autoLapStats != null) {
    				value = Util.paceToString(autoLapStats.getPace());
    			}
    		} else {
    			value = "Unknown display type: " + mDisplayType;
    			title = "???";
    		}

    		mValueView.setText(value);
    		mTitleView.setText(title);
    	}
    }

    private File mRecordDir;
    private MyOverlay mMapOverlay;
    private MapView mMapView;
    private StatsView mStatsViews[];

    private Button mPauseResumeButton;
    private Button mStartStopButton;
    private Button mLapButton;    

    private static final int STOPPED = 0;
    private static final int PAUSED = 1;
    private static final int RUNNING = 2;
    private int mRecordingState = STOPPED;
    
    private LapStats mTotalStats = null;
    HealthGraphClient.JsonActivity mLastReportedActivity = null;
    ArrayList<HealthGraphClient.JsonWGS84> mLastReportedPath = null;
    
    public void onGpsUpdate(
    		HealthGraphClient.JsonActivity activity,
    		ArrayList<HealthGraphClient.JsonWGS84> path,
    		LapStats totalStats,
    		LapStats userLapStats,
    		LapStats autoLapStats) {
    	mLastReportedActivity = activity;
    	mLastReportedPath = path;
    	mMapOverlay.updatePath(path);
    	mTotalStats = totalStats;
    	mMapView.invalidate();
    	for (int i = 0; i < mStatsViews.length; ++i) {
    		mStatsViews[i].update(totalStats, userLapStats, autoLapStats);
    	}
    }

    private LocationManager mLocationManager;
    private LocationListener mLocationListener; 

    @Override public void onPause() {
    	super.onPause();
    	GpsTrackingService.unregisterListener(this);
        mLocationManager.removeUpdates(mLocationListener);
        stopTimer();
    }
    
    @Override public void onResume() {
    	super.onResume();
    	
        GpsTrackingService.registerListener(this);
        if (GpsTrackingService.isGpsServiceRunning()) {
        	mRecordingState = RUNNING;
        	mStartStopButton.setText(R.string.stop); 
        } else {
        	mRecordingState = STOPPED;
        	mStartStopButton.setText(R.string.start);
        }
    	
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
        mStatsViews[0] = new StatsView(this, R.id.view0, R.id.view0_title, Settings.viewTypes[0]);
        mStatsViews[1] = new StatsView(this, R.id.view1, R.id.view1_title, Settings.viewTypes[1]);        
        mStatsViews[2] = new StatsView(this, R.id.view2, R.id.view2_title, Settings.viewTypes[2]);        
        mStatsViews[3] = new StatsView(this, R.id.view3, R.id.view3_title, Settings.viewTypes[3]);        
        mStatsViews[4] = new StatsView(this, R.id.view4, R.id.view4_title, Settings.viewTypes[4]);        
        mStatsViews[5] = new StatsView(this, R.id.view5, R.id.view5_title, Settings.viewTypes[5]);
        for (int i = 0; i < mStatsViews.length; ++i) {
        	mStatsViews[i].update(null,  null, null);
        }
        updateTimerIfNecessary();
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.recording);
        mRecordDir = FileManager.getRecordDir(this);
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
        		GpsTrackingService service = GpsTrackingService.getSingleton();  
        		if (service != null) service.onLapButtonPress();
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
    }

    @Override
    public void onDestroy() {
    	super.onDestroy();
    	if (Util.ASSERT_ENABLED && GpsTrackingService.isListenerRegistered(this)) {
    		Util.assertFail(this, "onPause not called??");
    	}
    }

    private void updateTimerIfNecessary() {
    	stopTimer();
    }
    
    private void stopTimer() {
    }
    
    void onStartButtonPress() {
        mRecordingState = RUNNING;
    	mMapOverlay.clearPath();
    	mMapView.invalidate();
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
    		wgs.timestamp = mTotalStats.getDurationSeconds();
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
    		
    		FileManager.FilenameSummary summary = new FileManager.FilenameSummary();
    		summary.putLong(FileManager.KEY_START_TIME, (long)mTotalStats.getStartTimeSeconds());
    		summary.putLong(FileManager.KEY_DISTANCE, (long)mLastReportedActivity.total_distance);
    		summary.putLong(FileManager.KEY_DURATION, (long)mLastReportedActivity.duration);
    		try {
    			FileManager.writeFile(mRecordDir, summary.getBasename(), mLastReportedActivity);
    		} catch (Exception e) {
    			// TODO: sync I/O
    			Toast.makeText(this, "Failed to write: " + summary.getBasename() + ": " + e.toString(),
    					Toast.LENGTH_LONG).show();
    		}
    	}
    }	
    private void onPauseButtonPress() {
    	mRecordingState = PAUSED;
    	updateTimerIfNecessary();
    	GpsTrackingService service = GpsTrackingService.getSingleton();  
    	if (service != null) service.onPauseButtonPress();
    }

    private void onResumeButtonPress() {
    	mRecordingState = RUNNING;
    	updateTimerIfNecessary();
    	GpsTrackingService service = GpsTrackingService.getSingleton();  
    	if (service != null) service.onResumeButtonPress();
    }

    @Override
    public boolean isRouteDisplayed() { return false; }

    @Override
        public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }


}
