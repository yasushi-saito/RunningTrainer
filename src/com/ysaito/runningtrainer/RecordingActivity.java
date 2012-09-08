package com.ysaito.runningtrainer;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

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

public class RecordingActivity extends MapActivity {
    static final String TAG = "Recording";

    static public class MyOverlay extends Overlay {
        private ArrayList<GeoPoint> mPoints;
        private GeoPoint mCurrentLocation;
        
        public MyOverlay() {
            mPoints = new ArrayList<GeoPoint>();
        }

    	public void clearPath() {
    		mPoints.clear();
    	}

        public void updatePath(ArrayList<HealthGraphClient.JsonWGS84> path) {
        	while (mPoints.size() < path.size()) {
        		HealthGraphClient.JsonWGS84 point = path.get(mPoints.size());
        		GeoPoint p = new GeoPoint((int)(point.latitude * 1e6), (int)(point.longitude * 1e6));
        		mPoints.add(p);
        	}
        }

        public void setCurrentLocation(HealthGraphClient.JsonWGS84 location) {
        	if (mPoints.size() == 0) {
        		// Real recording already started. ignore the location update 
        	} else {
        		GeoPoint p = new GeoPoint((int)(location.latitude * 1e6), (int)(location.longitude * 1e6));
        		mCurrentLocation = p;
        	}
        }
        
        @Override
        public boolean draw(Canvas canvas, MapView mapView, boolean shadow, long when) {
            boolean v = super.draw(canvas, mapView, shadow, when);
            if (shadow) return v;
            if (mPoints.size() > 0) {
            	Projection projection = mapView.getProjection();
            	Paint paint = new Paint();
            	paint.setAntiAlias(true);
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
            	drawCurrentLocation(canvas, mapView, mPoints.get(mPoints.size() - 1));
            } else if (mCurrentLocation != null) {
            	drawCurrentLocation(canvas, mapView, mCurrentLocation);
            }
            return v;
        }
        
        private void drawCurrentLocation(Canvas canvas, MapView mapView, GeoPoint geoPoint) {
        	Projection projection = mapView.getProjection();
            Point pointA = new Point();
            projection.toPixels(geoPoint, pointA);
            
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setColor(0xff000080);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
            paint.setColor(0xff0000ff);
            canvas.drawCircle(pointA.x, pointA.y, 10, paint);
        }
    };

    private static class Stats {
    	// The time activity started. Millisecs since 1970/1/1
    	private final long mStartTime;
    	
    	// The index of the last path[] element that was handled by updatePath.
    	// We assume that the array path[] passed to updatePath() simply adds new points at the end on 
    	// every call
    	private int mLastPathSegment = 0;
    	
    	// Cumulative distance of points in path[0..mLastPathSegment].
    	private double mDistance = 0;
    	
    	private final float[] mTmp = new float[1];
    	
    	Stats() {
    		mStartTime = System.currentTimeMillis();
    	}
    	public final long getStartTime() { return mStartTime; }
    	public final double getDistanceMeters() { return mDistance; }
    	public final long getDurationMillis() { return System.currentTimeMillis() - mStartTime; }
    	public final void updatePath(ArrayList<HealthGraphClient.JsonWGS84> path) {
    		if (path.size() > mLastPathSegment + 1) {
    			HealthGraphClient.JsonWGS84 lastPoint = path.get(mLastPathSegment);
    			mLastPathSegment++;
    			for (; mLastPathSegment < path.size(); mLastPathSegment++) {
    				HealthGraphClient.JsonWGS84 thisPoint = path.get(mLastPathSegment);
    				Location.distanceBetween(lastPoint.latitude, lastPoint.longitude,
    						thisPoint.latitude, thisPoint.longitude, mTmp);
    				mDistance += mTmp[0];
    				lastPoint = thisPoint;
    			}
    		}
    	}
    }
    
    private MyOverlay mMapOverlay;
    private MapView mMapView;
    private TextView mPaceView;     // for displaying pace
    private TextView mDistanceView; // for displaying distance    
    private TextView mElapsedView;  // for displaying elapsed        
    private Button mPauseResumeButton;
    private Button mStartStopButton;

    private RecordManager mRecordManager;

    private static final int STOPPED = 0;
    private static final int PAUSED = 1;
    private static final int RUNNING = 2;
    private int mRecordingState = STOPPED;
    private Stats mStats = new Stats();
    
    HealthGraphClient.JsonActivity mLastReportedActivity = null;
    ArrayList<HealthGraphClient.JsonWGS84> mLastReportedPath = null;
    
    public void onGpsUpdate(HealthGraphClient.JsonActivity activity,
    		ArrayList<HealthGraphClient.JsonWGS84> path) {
    	mLastReportedActivity = activity;
    	mLastReportedPath = path;
    	mMapOverlay.updatePath(path);
    	mMapView.invalidate();
    }
    
    private LocationManager mLocationManager;
    private LocationListener mDummyLocationListener; 
    private Timer mTimer;

    public void onEverySecond() {
    	if (mStats == null) return;  // this shouldn't happen.
    	final long now = System.currentTimeMillis();
    	final long elapsed = now - mStats.getStartTime();
    	mElapsedView.setText(Util.durationToString(elapsed / 1000.0));
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

        mPaceView = (TextView)findViewById(R.id.pace_view);
        mDistanceView = (TextView)findViewById(R.id.distance_view);
        mElapsedView = (TextView)findViewById(R.id.elapsed_view);
        
        mLocationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);

        // Define a listener that responds to location updates
        mDummyLocationListener = new LocationListener() {
    		public void onLocationChanged(Location location) {
    			if (mRecordingState == PAUSED || mRecordingState == STOPPED) {
    				HealthGraphClient.JsonWGS84 wgs = new HealthGraphClient.JsonWGS84();
    				wgs.latitude = location.getLatitude();
    				wgs.longitude = location.getLongitude();
    				wgs.altitude = location.getAltitude();
    				mMapOverlay.setCurrentLocation(wgs);
    				mMapView.invalidate();
    			}
			}

			public void onProviderDisabled(String provider) { }
			public void onProviderEnabled(String provider) { }
			public void onStatusChanged(String provider, int status, Bundle extras) {
			}        
        };

        // Register the listener with the Location Manager to receive location updates
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mDummyLocationListener);
        mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mDummyLocationListener);

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

        mStartStopButton = (Button)findViewById(R.id.start_stop_button);
        mStartStopButton.setOnClickListener(new Button.OnClickListener() {
        	public void onClick(View v) {
        		if (mRecordingState == STOPPED) {
        			onStartButtonPress();
        			mPauseResumeButton.setEnabled(true);
        			mPauseResumeButton.setText(R.string.pause);
        			mStartStopButton.setText(R.string.stop); 
        		} else {
        			// Either running or paused
        			onStopButtonPress();
        			mPauseResumeButton.setEnabled(false);
        			mPauseResumeButton.setText(R.string.pause);
        			mStartStopButton.setText(R.string.start); 
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
        mLocationManager.removeUpdates(mDummyLocationListener);
    	GpsTrackingService.unregisterGpsListener(this);
    }

    private void startTimer() {
    	if (mTimer != null) mTimer.cancel();
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
        	@Override public void run() {
        		runOnUiThread(new Runnable() {
        			public void run() { onEverySecond(); }
        		});
        	}
        }, 0, 1000);
    }
    
    private void stopTimer() {
    	if (mTimer != null) {
    		mTimer.cancel();
    		mTimer = null;
    	}
    }
    
    void onStartButtonPress() {
        mRecordingState = RUNNING;
    	mMapOverlay.clearPath();
    	mMapView.invalidate();
    	mStats = new Stats();
    	GpsTrackingService.startGpsServiceIfNecessary(this);
    	startTimer();    	
    }

    private void onStopButtonPress() {
    	mRecordingState = STOPPED;
    	GpsTrackingService.stopGpsServiceIfNecessary(this);
    	if (mStats == null || mLastReportedPath == null || mLastReportedPath.size() < 1) {
    		// This shouldn't happen in practice, but just a paranoid check
    		return;
    	}

    	HealthGraphClient.JsonWGS84 last = mLastReportedPath.get(mLastReportedPath.size() - 1);
    	HealthGraphClient.JsonWGS84 wgs = new HealthGraphClient.JsonWGS84();
    	wgs.latitude = last.latitude;
    	wgs.longitude = last.longitude;
    	wgs.altitude = last.altitude;
    	wgs.type = "end";
    	wgs.timestamp = (System.currentTimeMillis()- mStats.getStartTime()) / 1000.0;
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
    	mRecordManager.addRecord(mStats.getStartTime(), mLastReportedActivity);
    	stopTimer();
    }

    private void onPauseButtonPress() {
    	mRecordingState = PAUSED;
    	stopTimer();    	
    }

    private void onResumeButtonPress() {
    	mRecordingState = RUNNING;
    	startTimer();
    }

    @Override
    public boolean isRouteDisplayed() { return false; }

    @Override
        public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }


}
