package com.ysaito.runningtrainer;

import java.util.ArrayList;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class RecordReplayActivity extends MapActivity {
    static final String TAG = "Recording";

    static public class MyOverlay extends Overlay {
        private ArrayList<GeoPoint> mPoints;
        
        public MyOverlay() {
            mPoints = new ArrayList<GeoPoint>();
        }

        public ArrayList<GeoPoint> getPoints() { return mPoints; }
        
        public void setPath(HealthGraphClient.JsonWGS84[] path) {
        	mPoints.clear();
        	for (HealthGraphClient.JsonWGS84 point : path) {
        		GeoPoint p = new GeoPoint((int)(point.latitude * 1e6), (int)(point.longitude * 1e6));
        		mPoints.add(p);
        	}
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
            		Point lastPoint = new Point();
            		projection.toPixels(mPoints.get(0), lastPoint);
            		
            		Point lastSegmentEndpoint = null; 
            		final int maxHeight= mapView.getHeight();
            		final int maxWidth = mapView.getWidth();
            		
            		for (int i = 1; i < mPoints.size(); i++) {
            			GeoPoint geoPoint = mPoints.get(i);
            			Point point = new Point();
            			projection.toPixels(geoPoint, point);
            			
            			// Draw the line from mPoints[i-1] .. mPoints[i] if either of
            			// the endpoints are in the view.
            			boolean drawLine = false;
            			drawLine |= (
            					lastPoint.x >= 0 && lastPoint.x < maxWidth &&
            					lastPoint.y >= 0 && lastPoint.y < maxHeight);
            			drawLine |= (
            					point.x >= 0 && point.x < maxWidth &&
            					point.y >= 0 && point.y < maxHeight);
            			if (drawLine) {
            				if (lastSegmentEndpoint != lastPoint) {
            					path.moveTo(lastPoint.x, lastPoint.y);
            				}
            				path.lineTo(point.x, point.y);
            				lastSegmentEndpoint = point;
            			}
            			lastPoint = point;
            		}
            		canvas.drawPath(path, paint);
            	}
            }
            return v;
        }
        
    };

    private MyOverlay mMapOverlay;
    private MapView mMapView;
    private HealthGraphClient.JsonActivity mRecord;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.log_replay);
        mMapOverlay = new MyOverlay();
        mMapView = (MapView)findViewById(R.id.map_view);
        mMapView.getOverlays().add(mMapOverlay);
        mMapView.setBuiltInZoomControls(true);
        
    }

    @Override public void onResume() {
    	super.onResume();
    	((TextView)findViewById(R.id.distance_title)).setText(
    			Util.distanceUnitString());
    	((TextView)findViewById(R.id.duration_title)).setText("Time");
    	((TextView)findViewById(R.id.pace_title)).setText("Pace");
    	
    	if (mRecord != null) {
    		updateStatsViews();
    	}
    }

    @Override public void onBackPressed() {
    	super.onBackPressed();
    }
    
    private void updateStatsViews() {
    	TextView distanceView = (TextView)findViewById(R.id.distance);
    	TextView durationView = (TextView)findViewById(R.id.duration);
    	TextView paceView = (TextView)findViewById(R.id.pace);    	

    	distanceView.setText(Util.distanceToString(mRecord.total_distance));
    	durationView.setText(Util.durationToString((long)mRecord.duration));
    	if (mRecord.total_distance <= 0.0) {
    		paceView.setText("0:00");
    	} else {
    		paceView.setText(Util.paceToString(mRecord.duration / mRecord.total_distance));
    	}
    	
    	 if (mRecord.path.length > 0) {
    		 MapController controller = mMapView.getController();
    		 GeoPoint point = new GeoPoint((int)(mRecord.path[0].latitude * 1e6), (int)(mRecord.path[0].longitude * 1e6));
    		 controller.animateTo(point);
    	 }
    }
    
    @Override
    public boolean isRouteDisplayed() { return false; }
    
    public void setRecord(HealthGraphClient.JsonActivity record) {
    	mRecord = record;
    	mMapOverlay.setPath(record.path);
    	Util.RescaleMapView(mMapView, mMapOverlay.getPoints());
    	mMapView.invalidate();
    	updateStatsViews();
    }
}
