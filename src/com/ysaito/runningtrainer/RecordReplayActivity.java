package com.ysaito.runningtrainer;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
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
	private static final String TAG = "RecordReplayActivity";

    static public class MyOverlay extends Overlay {
        private final ChunkedArray<GeoPoint> mPoints = new ChunkedArray<GeoPoint>();
        private final Paint mPaint = new Paint();
        private JsonWGS84 mCurrentLocation = null;
        
        public final ChunkedArray<GeoPoint> getPoints() { return mPoints; }
        
        public final void setPath(JsonWGS84[] path) {
        	mPoints.clear();
        	for (JsonWGS84 point : path) {
        		GeoPoint p = new GeoPoint((int)(point.latitude * 1e6), (int)(point.longitude * 1e6));
        		mPoints.add(p);
        	}
        }

        public final void setHighlightLocation(JsonWGS84 location) {
        	mCurrentLocation = location;
        }
        
        @Override
        public boolean draw(Canvas canvas, MapView mapView, boolean shadow, long when) {
            boolean v = super.draw(canvas, mapView, shadow, when);
            if (shadow) return v;
            
            Projection projection = mapView.getProjection();
            mPaint.setAntiAlias(true);

            GraphicsUtil.drawPath(mPoints, mapView.getWidth(), mapView.getHeight(), canvas, projection, mPaint);
            if (mCurrentLocation != null) {
            	GraphicsUtil.drawCurrentPosition((float)mCurrentLocation.latitude, (float)mCurrentLocation.longitude, 0.0f, canvas, projection, mPaint);
            }
            if (mPoints.size() > 0) {
            	GeoPoint start = mPoints.front();
            	GraphicsUtil.drawStartPoint(
            			(float)(start.getLatitudeE6() / 1e6), (float)(start.getLongitudeE6() / 1e6), canvas, projection, mPaint);
            	
            }
            if (mPoints.size() > 1) {
            	GeoPoint stop = mPoints.back();
            	GraphicsUtil.drawStopPoint(
            			(float)(stop.getLatitudeE6() / 1e6), (float)(stop.getLongitudeE6() / 1e6), canvas, projection, mPaint);
            }
            return v;
        }
        
    };

    private MyOverlay mMapOverlay;
    private MapView mMapView;
    private ListView mLapListView;
    private MyAdapter mLapListAdapter;
    private JsonActivity mRecord;
    
    private static class MyAdapter extends BaseAdapter {
    	private final LayoutInflater mInflater;
    	private ChunkedArray<Util.LapSummary> mLaps = new ChunkedArray<Util.LapSummary>();

    	public MyAdapter(Context context) { 
    		mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE); 
    	}

    	public void setRecord(JsonActivity record) {
    		mLaps = Util.listLaps(record);
    		notifyDataSetChanged();
    	}

    	public final Util.LapSummary getLapSummary(int index) {
    		if (index < 0 || index >= mLaps.size()) return null;
    		return mLaps.getAtIndex(index);
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
    			distanceView.setText(Util.capitalizedDistanceUnitString());    			
    			paceView.setText("Pace");
    			elevGainView.setText("Elev gain");
    			elevLossView.setText("Elev loss");    			
    		} else {
    			--position;
    			if (position < 0 && position >= mLaps.size()) return layout;

    			final Util.LapSummary lap = mLaps.getAtIndex(position);
    			final Util.LapSummary lastLap = (position > 0 ? mLaps.getAtIndex(position - 1) : null);

    			distanceView.setHorizontallyScrolling(false);
    			distanceView.setText(Util.distanceToString(lap.distance, Util.DistanceUnitType.KM_OR_MILE));

    			double lastElapsed = (lastLap != null ? lastLap.elapsedSeconds : 0.0);
    			double lastDistance = (lastLap != null ? lastLap.distance : 0.0);

    			double distanceDelta = lap.distance - lastDistance;
    			double elapsedDelta = lap.elapsedSeconds - lastElapsed;
    			double pace = (distanceDelta > 0 ? elapsedDelta / distanceDelta : 0.0);
    			paceView.setText(Util.paceToString(pace));

    			double v = (lap.elevationGain - (lastLap != null ? lastLap.elevationGain : 0.0));
    			elevGainView.setText(Util.distanceToString(v, Util.DistanceUnitType.MILE_OR_FEET));

    			v = (lap.elevationLoss - (lastLap != null ? lastLap.elevationLoss : 0.0));
    			elevLossView.setText(Util.distanceToString(v, Util.DistanceUnitType.MILE_OR_FEET));
    		}
    		return layout;
    	}
    }
    
    public void setMapMode(int mapMode) {
    	mMapView.setSatellite(mapMode == MapMode.SATTELITE);
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	Plog.d(TAG, "onCreate");
    	GraphicsUtil.maybeInitialize(this);
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
				final Util.LapSummary lap = (Util.LapSummary)mLapListAdapter.getItem(position);
				if (lap == null) {
					mMapOverlay.setHighlightLocation(null);
				} else {
					mMapOverlay.setHighlightLocation(lap.location);
					MapController controller = mMapView.getController();
					GeoPoint point = new GeoPoint((int)(lap.location.latitude * 1e6), (int)(lap.location.longitude * 1e6));
					controller.animateTo(point);
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
    
    @Override
    public boolean isRouteDisplayed() { return false; }
    
    private final void updateStatsViews() {
    	TextView distanceView = (TextView)findViewById(R.id.replay_distance);
    	TextView durationView = (TextView)findViewById(R.id.replay_duration);
    	TextView paceView = (TextView)findViewById(R.id.replay_pace);    	

    	distanceView.setText(Util.distanceToString(mRecord.total_distance, Util.DistanceUnitType.KM_OR_MILE));
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
    
    public final void setRecord(JsonActivity record) {
    	mRecord = record;
    	mMapOverlay.setPath(mRecord.path);
    	Util.rescaleMapView(mMapView, mMapOverlay.getPoints());
    	mMapView.invalidate();
    	mLapListAdapter.setRecord(record);
    	updateStatsViews();
    }
}
