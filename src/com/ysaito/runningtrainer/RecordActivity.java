package com.ysaito.runningtrainer;

import java.util.ArrayList;

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
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;

public class RecordActivity extends MapActivity {
    static final String TAG = "Record";

    static public class MyOverlay extends Overlay {
        private ArrayList<GeoPoint> mPoints;
        public MyOverlay() {
            mPoints = new ArrayList<GeoPoint>();
        }

        public void addPoint(GeoPoint point) {
            mPoints.add(point);
        }

        @Override
        public boolean draw(Canvas canvas, MapView mapView, boolean shadow, long when) {
            boolean v = super.draw(canvas, mapView, shadow, when);
            if (shadow || mPoints.size() == 0) return v;

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

            GeoPoint gPointA = mPoints.get(mPoints.size() - 1);
            Point pointA = new Point();
            projection.toPixels(gPointA, pointA);
            paint.setColor(0xff0000ff);
            canvas.drawCircle(pointA.x, pointA.y, 10, paint);
            return v;
        }
    };

    private MyOverlay mMapOverlay;
    private MapView mMapView;
    private Button mPauseResumeButton;
    private Button mStartStopButton;

    private HealthGraphClient.JsonActivity mRecord;
    private ArrayList<HealthGraphClient.JsonWGS84> mPath;
    private RecordManager mRecordManager;
    private long mStartTime;

    private static final int STOPPED = 0;
    private static final int PAUSED = 1;
    private static final int RUNNING = 2;
    private int mRecordingState = STOPPED;

    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.recording);
        mMapOverlay = new MyOverlay();
        mMapView = (MapView)findViewById(R.id.map_view);
        mMapView.getOverlays().add(mMapOverlay);

        // Acquire a reference to the system Location Manager
        LocationManager locationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);

        // Define a listener that responds to location updates
        LocationListener locationListener = new LocationListener() {
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
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);

        mPauseResumeButton = (Button)findViewById(R.id.pause_resume_button);
        mPauseResumeButton.setEnabled(false); // pause/resume is disabled unless the recorder is running

        mPauseResumeButton.setOnClickListener(new Button.OnClickListener() {
        	public void onClick(View v) {
        		if (mRecordingState == RUNNING) {
        			onPauseButtonPress();
        			mPauseResumeButton.setText("Resume"); // TODO: externalize
        		} else if (mRecordingState == PAUSED) {
        			// Either running or paused
        			onResumeButtonPress();
        			mPauseResumeButton.setText("Pause");  // TODO: externalize
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
        			mPauseResumeButton.setText("Pause");
        			mStartStopButton.setText("Stop"); // TODO: externalize
        		} else {
        			// Either running or paused
        			onStopButtonPress();
        			mPauseResumeButton.setEnabled(false);
        			mPauseResumeButton.setText("Pause");
        			mStartStopButton.setText("Start");  // TODO: externalize
        		}
        	}
        });

    }

    void onStartButtonPress() {
        mPath = new ArrayList<HealthGraphClient.JsonWGS84>();
        mRecord = new HealthGraphClient.JsonActivity();
        mRecordManager = new RecordManager(this);
        mRecord.type = "Running";  // TODO: allow changing
        mRecord.start_time = HealthGraphClient.utcMillisToString(System.currentTimeMillis());
        mRecord.notes = "Recorded by RunningTrainer";
        mRecordingState = RUNNING;
        mStartTime =  System.currentTimeMillis();
    }

    private void onGpsLocationUpdate(long now, Location newLocation) {
    	if (mRecordingState != RUNNING) return;

    	GeoPoint p = new GeoPoint((int)(newLocation.getLatitude() * 1e6), (int)(newLocation.getLongitude() * 1e6));
    	mMapOverlay.addPoint(p);
    	mMapView.invalidate();

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
    }

    private void onStopButtonPress() {
    	mRecordingState = STOPPED;
    	if (mPath.size() < 1) return;

    	HealthGraphClient.JsonWGS84 last = mPath.get(mPath.size() - 1);
    	HealthGraphClient.JsonWGS84 wgs = new HealthGraphClient.JsonWGS84();
    	wgs.latitude = last.latitude;
    	wgs.longitude = last.longitude;
    	wgs.altitude = last.altitude;
    	wgs.type = "end";
    	wgs.timestamp = (System.currentTimeMillis()- mStartTime) / 1000.0;
    	mPath.add(wgs);
    	mRecord.path = new HealthGraphClient.JsonWGS84[mPath.size()];
    	for (int i = 0; i < mPath.size(); ++i) mRecord.path[i] = mPath.get(i);

    	mRecord.duration = wgs.timestamp;
    	HealthGraphClient.JsonWGS84 lastLocation = null;

    	float[] distance = new float[1];
    	for (HealthGraphClient.JsonWGS84 location : mRecord.path) {
    		if (lastLocation != null) {
    			Location.distanceBetween(lastLocation.latitude, lastLocation.longitude,
    					location.latitude, location.longitude,
    					distance);
    			mRecord.total_distance += distance[0];
    		}
    		lastLocation = location;
    	}
    	mRecordManager.addRecord(mStartTime, mRecord);
    }

    private void onPauseButtonPress() {
    	mRecordingState = PAUSED;
    }

    private void onResumeButtonPress() {
    	mRecordingState = RUNNING;
    }

    @Override
    public boolean isRouteDisplayed() { return false; }

    @Override
        public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }


}
