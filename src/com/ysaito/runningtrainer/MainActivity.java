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

public class MainActivity extends MapActivity {
	static final String TAG = "Main";
	
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
	
	
	MyOverlay mMapOverlay;
	MapView mMapView;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
        	GeoPoint mLastReportedLocation = null;
        	
            @Override
            public void onLocationChanged(Location location) {
            	// Called when a new location is found by the network location provider.
            	// makeUseOfNewLocation(location);
            	final long time = location.getTime();
            	Log.d(TAG, "REP: " + time + "/" + mLastReportTime);
            	if (time < mLastReportTime + MIN_RECORD_INTERVAL_MS) return;
            	
            	GeoPoint p = new GeoPoint((int)(location.getLatitude() * 1e6), (int)(location.getLongitude() * 1e6));
            	mMapOverlay.addPoint(p);
            	mMapView.invalidate();
            	
            	mLastReportTime = time;
            	mLastReportedLocation = p;
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            	Log.d(TAG, "Status: " + provider + ": " + status);
            }

            @Override
            public void onProviderEnabled(String provider) {
            	Log.d(TAG, "Provider Enabled: " + provider);
            }

            @Override
            public void onProviderDisabled(String provider) {
            	Log.d(TAG, "Provider Disabled: " + provider);
            }
        };

        // Register the listener with the Location Manager to receive location updates
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        Log.d(TAG, "RunningTrainer started");
    }
    
    @Override
    public boolean isRouteDisplayed() { return false; }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    
}
