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

public class RecordReplayActivity extends MapActivity {
    static final String TAG = "Recording";

    static public class MyOverlay extends Overlay {
        private ArrayList<GeoPoint> mPoints;
        
        public MyOverlay() {
            mPoints = new ArrayList<GeoPoint>();
        }

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
    			"Distance (" + Util.distanceUnitString(settings) + ")");
    	((TextView)findViewById(R.id.duration_title)).setText("Elapsed");
    	((TextView)findViewById(R.id.pace_title)).setText(
    			"Pace " + Util.paceUnitString(settings));
    	
    	if (mRecord != null) updateStatsViews();
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
    }
    
    @Override
    public boolean isRouteDisplayed() { return false; }
    
    public void setRecord(HealthGraphClient.JsonActivity record) {
    	mRecord = record;
    	mMapOverlay.setPath(record.path);
    	mMapView.invalidate();
    	updateStatsViews();
    }
}
