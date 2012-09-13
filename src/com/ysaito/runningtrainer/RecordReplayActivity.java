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
    	Settings settings = Settings.getSettings(this);
    	((TextView)findViewById(R.id.distance_title)).setText(
    			Util.distanceUnitString(settings));
    	((TextView)findViewById(R.id.duration_title)).setText("Time");
    	((TextView)findViewById(R.id.pace_title)).setText("Pace");
    	
    	if (mRecord != null) {
    		updateStatsViews();
    	}
    }

    private void updateStatsViews() {
    	TextView distanceView = (TextView)findViewById(R.id.distance);
    	TextView durationView = (TextView)findViewById(R.id.duration);
    	TextView paceView = (TextView)findViewById(R.id.pace);    	

    	Settings settings = Settings.getSettings(this);
    	distanceView.setText(Util.distanceToString(mRecord.total_distance, settings));
    	durationView.setText(Util.durationToString(mRecord.duration));
    	if (mRecord.total_distance <= 0.0) {
    		paceView.setText("0:00");
    	} else {
    		paceView.setText(Util.paceToString(mRecord.duration / mRecord.total_distance, settings));
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
