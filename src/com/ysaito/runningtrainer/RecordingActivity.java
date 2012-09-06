package com.ysaito.runningtrainer;

import java.util.ArrayList;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class RecordingActivity extends MapActivity {
    static final String TAG = "Record";

    static public class MyOverlay extends Overlay {
        private ArrayList<GeoPoint> mPoints;
        public MyOverlay() {
            mPoints = new ArrayList<GeoPoint>();
        }

        public void updatePath(ArrayList<HealthGraphClient.JsonWGS84> path) {
        	while (mPoints.size() < path.size()) {
        		HealthGraphClient.JsonWGS84 point = path.get(mPoints.size());
        		GeoPoint p = new GeoPoint((int)(point.latitude * 1e6), (int)(point.longitude * 1e6));
        		mPoints.add(p);
        	}
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

    private RecordManager mRecordManager;

    private static final int STOPPED = 0;
    private static final int PAUSED = 1;
    private static final int RUNNING = 2;
    private int mRecordingState = STOPPED;
    private long mStartTime;

    HealthGraphClient.JsonActivity mLastReportedActivity = null;
    ArrayList<HealthGraphClient.JsonWGS84> mLastReportedPath = null;
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
        mLocationManager.removeUpdates(mDummyLocationListener);
    	GpsTrackingService.unregisterGpsListener(this);
    }

    public void onGpsUpdate(HealthGraphClient.JsonActivity activity,
    		ArrayList<HealthGraphClient.JsonWGS84> path) {
    	mLastReportedActivity = activity;
    	mLastReportedPath = path;
    	mMapOverlay.updatePath(path);
    	mMapView.invalidate();
    }
    
    private LocationManager mLocationManager;
    private LocationListener mDummyLocationListener; 
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.recording);
        mMapOverlay = new MyOverlay();
        mMapView = (MapView)findViewById(R.id.map_view);
        mMapView.getOverlays().add(mMapOverlay);
        mMapView.setBuiltInZoomControls(true);

        GpsTrackingService.registerGpsListener(this);
        
        mLocationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);

        // Define a listener that responds to location updates
        mDummyLocationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
			}

			public void onProviderDisabled(String provider) {
			}

			public void onProviderEnabled(String provider) {
			}

			public void onStatusChanged(String provider, int status,
					Bundle extras) {
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
        mRecordManager = new RecordManager(this);
        mRecordingState = RUNNING;
        mStartTime = System.currentTimeMillis();
        startService(new Intent(this, GpsTrackingService.class));
    }

    private void onStopButtonPress() {
    	mRecordingState = STOPPED;
    	stopService(new Intent(this, GpsTrackingService.class));
    	if (mLastReportedPath == null || mLastReportedPath.size() < 1) return;

    	HealthGraphClient.JsonWGS84 last = mLastReportedPath.get(mLastReportedPath.size() - 1);
    	HealthGraphClient.JsonWGS84 wgs = new HealthGraphClient.JsonWGS84();
    	wgs.latitude = last.latitude;
    	wgs.longitude = last.longitude;
    	wgs.altitude = last.altitude;
    	wgs.type = "end";
    	wgs.timestamp = (System.currentTimeMillis()- mStartTime) / 1000.0;
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
    	mRecordManager.addRecord(mStartTime, mLastReportedActivity);
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
