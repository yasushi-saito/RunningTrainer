package com.ysaito.runningtrainer;

import java.util.ArrayList;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class RecordReplayActivity extends MapActivity {
    @SuppressWarnings("unused")
	private static final String TAG = "Recording";

    static public class MyOverlay extends Overlay {
        private final ArrayList<GeoPoint> mPoints = new ArrayList<GeoPoint>();
        private GeoPoint mHighlight = null;

        public ArrayList<GeoPoint> getPoints() { return mPoints; }
        
        public void setPath(HealthGraphClient.JsonWGS84[] path) {
        	mPoints.clear();
        	for (HealthGraphClient.JsonWGS84 point : path) {
        		GeoPoint p = new GeoPoint((int)(point.latitude * 1e6), (int)(point.longitude * 1e6));
        		mPoints.add(p);
        	}
        }

        public void setHighlight(HealthGraphClient.JsonWGS84 location) {
        	if (location == null) {
        		mHighlight = null;
        	} else {
        		mHighlight = new GeoPoint((int)(location.latitude * 1e6), (int)(location.longitude * 1e6));
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
            if (mHighlight != null) {
            	Point point = new Point();
            	projection.toPixels(mHighlight, point);
            	
            	paint.setColor(0xff000080);
            	paint.setStyle(Paint.Style.STROKE);
            	paint.setStrokeWidth(8);
            	paint.setColor(0xff0000ff);
            	
            	canvas.drawCircle(point.x, point.y, 10, paint);
            }
            return v;
        }
        
    };

    private MyOverlay mMapOverlay;
    private MapView mMapView;
    private ListView mLapListView;
    private MyAdapter mLapListAdapter;
    private HealthGraphClient.JsonActivity mRecord;
    
    private static class MyAdapter extends BaseAdapter {
    	private final LayoutInflater mInflater;
    	private ArrayList<HealthGraphClient.LapSummary> mLaps = new ArrayList<HealthGraphClient.LapSummary>();
    		    
    	public MyAdapter(Context context) { 
    		mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE); 
    	}

    	public void setRecord(HealthGraphClient.JsonActivity record) {
    		mLaps = HealthGraphClient.listLaps(record);
    		notifyDataSetChanged();
    	}

    	public final HealthGraphClient.LapSummary getLapSummary(int index) {
    		if (index < 0 || index >= mLaps.size()) return null;
    		return mLaps.get(index);
    	}
    	
    	//
    	// Methods to implement BaseAdapter
    	//
    	
    	public int getCount() { 
    		// +1 for the header row
    		return mLaps.size() + 1;
    	}
    	
    	public Object getItem(int position) { 
    		if (position == 0) return null;
    		return getLapSummary(position - 1);
   		} 
    	
    	public long getItemId(int position) { 
    		return position; 
    	}
    	
    	public View getView(int position, View convertView, ViewGroup parent) {
    		LinearLayout layout;
    		if (convertView == null) {
    			layout = (LinearLayout)mInflater.inflate(R.layout.lap_list_row, parent, false);
    		} else {
    			layout = (LinearLayout)convertView;
    		}
    		TextView distanceView = (TextView)layout.findViewById(R.id.lap_list_distance);
    		TextView paceView = (TextView)layout.findViewById(R.id.lap_list_pace);
    		TextView elevGainView = (TextView)layout.findViewById(R.id.lap_list_elevation_gain);
    		TextView elevLossView = (TextView)layout.findViewById(R.id.lap_list_elevation_loss);
    		if (position == 0) {
    			// Print the header line
    			distanceView.setText(Util.distanceUnitString());
    			paceView.setText("Pace");
    			elevGainView.setText("Elev gain");
    			elevLossView.setText("Elev loss");    			
    		} else {
    			--position;
    			if (position < 0 && position >= mLaps.size()) return layout;

    			final HealthGraphClient.LapSummary lap = mLaps.get(position);
    			final HealthGraphClient.LapSummary lastLap = (position > 0 ? mLaps.get(position - 1) : null);

    			distanceView.setHorizontallyScrolling(false);
    			distanceView.setText(Util.distanceToString(lap.distance));

    			double lastElapsed = (lastLap != null ? lastLap.elapsedSeconds : 0.0);
    			double lastDistance = (lastLap != null ? lastLap.distance : 0.0);

    			double distanceDelta = lap.distance - lastDistance;
    			double elapsedDelta = lap.elapsedSeconds - lastElapsed;
    			double pace = (distanceDelta > 0 ? elapsedDelta / distanceDelta : 0.0);
    			paceView.setText(Util.paceToString(pace));

    			double v = (lap.elevationGain - (lastLap != null ? lastLap.elevationGain : 0.0));
    			elevGainView.setText(Util.distanceToString(v));

    			v = (lap.elevationLoss - (lastLap != null ? lastLap.elevationLoss : 0.0));
    			elevLossView.setText(Util.distanceToString(v));
    		}
    		return layout;
    	}
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.log_replay);
        mMapOverlay = new MyOverlay();
        mMapView = (MapView)findViewById(R.id.replay_map_view);
        mMapView.getOverlays().add(mMapOverlay);
        mMapView.setBuiltInZoomControls(true);
        
        mLapListView = (ListView)findViewById(R.id.replay_lap_list);
        mLapListAdapter = new MyAdapter(this);
        mLapListView.setAdapter(mLapListAdapter);
        
        mLapListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
				final HealthGraphClient.LapSummary lap = (HealthGraphClient.LapSummary)mLapListAdapter.getItem(position);
				if (lap == null) {
					mMapOverlay.setHighlight(null);
				} else {
					mMapOverlay.setHighlight(lap.location);
				}
				mMapView.invalidate();
			}
        });
    }

    @Override public void onResume() {
    	super.onResume();
    	((TextView)findViewById(R.id.replay_distance_title)).setText(
    			Util.distanceUnitString());
    	((TextView)findViewById(R.id.replay_duration_title)).setText("Time");
    	((TextView)findViewById(R.id.replay_pace_title)).setText("Pace");
    	
    	if (mRecord != null) {
    		updateStatsViews();
    	}
    }

    @Override public void onBackPressed() {
    	super.onBackPressed();
    }
    
    private void updateStatsViews() {
    	TextView distanceView = (TextView)findViewById(R.id.replay_distance);
    	TextView durationView = (TextView)findViewById(R.id.replay_duration);
    	TextView paceView = (TextView)findViewById(R.id.replay_pace);    	

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
    	mLapListAdapter.setRecord(record);
    	updateStatsViews();
    }
}
