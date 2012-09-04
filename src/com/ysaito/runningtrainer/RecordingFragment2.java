package com.ysaito.runningtrainer;

import java.util.ArrayList;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import android.app.Fragment;
import android.app.LocalActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;

@SuppressWarnings("deprecation")
public class RecordingFragment2 extends Fragment {
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

    private static final String KEY_STATE_BUNDLE = "localActivityManagerState";
	private LocalActivityManager mLocalActivityManager;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        Bundle state = null;
        if (savedInstanceState != null) {
            state = savedInstanceState.getBundle(KEY_STATE_BUNDLE);
        }
        mLocalActivityManager = new LocalActivityManager(getActivity(), true);
        mLocalActivityManager.dispatchCreate(state);
    }
    
    @Override
    public View onCreateView(
    		LayoutInflater inflater, 
    		ViewGroup container,
            Bundle savedInstanceState) {
		Gson gson = new GsonBuilder().create();
		
        Intent i = new Intent(getActivity(), RecordActivity.class); 
        Window w = mLocalActivityManager.startActivity("tag", i); 
        View currentView = w.getDecorView(); 
        currentView.setVisibility(View.VISIBLE); 
        currentView.setFocusableInTouchMode(true); 
        ((ViewGroup) currentView).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        return currentView;
    }
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle(KEY_STATE_BUNDLE,
                mLocalActivityManager.saveInstanceState());
    }

    @Override
    public void onResume() {
        super.onResume();
        mLocalActivityManager.dispatchResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mLocalActivityManager.dispatchPause(getActivity().isFinishing());
    }

    @Override
    public void onStop() {
        super.onStop();
        mLocalActivityManager.dispatchStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mLocalActivityManager.dispatchDestroy(getActivity().isFinishing());
    }
}
