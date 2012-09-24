package com.ysaito.runningtrainer;

import java.io.File;
import java.util.ArrayList;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;
import com.ysaito.runningtrainer.FileManager.FilenameSummary;

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
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class RecordingActivity extends MapActivity {
    static final String TAG = "Recording";

    static public class MyOverlay extends Overlay {
        private ArrayList<GeoPoint> mPoints;
        private double mCurrentAccuracy; // the latest report on GPS accuracy (meters)
        private GeoPoint mCurrentLocation;
        
        public MyOverlay() {
            mPoints = new ArrayList<GeoPoint>();
        }

    	public void clearPath() {
    		mPoints.clear();
    		mCurrentLocation = null;
    	}

        public void updatePath(ArrayList<HealthGraphClient.JsonWGS84> path) {
        	while (mPoints.size() < path.size()) {
        		HealthGraphClient.JsonWGS84 point = path.get(mPoints.size());
        		GeoPoint p = new GeoPoint((int)(point.latitude * 1e6), (int)(point.longitude * 1e6));
        		mPoints.add(p);
        	}
        }

        public void setCurrentLocation(HealthGraphClient.JsonWGS84 location, double accuracy) {
        	mCurrentLocation = new GeoPoint((int)(location.latitude * 1e6), (int)(location.longitude * 1e6));
        	mCurrentAccuracy = accuracy;
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
            if (mCurrentLocation != null) {
            	Point pointA = new Point();
            	projection.toPixels(mCurrentLocation, pointA);
            	
            	paint.setColor(0xff000080);
            	paint.setStyle(Paint.Style.STROKE);
            	paint.setStrokeWidth(8);
            	paint.setColor(0xff0000ff);
            	
            	float radius = Math.max(10.0f, projection.metersToEquatorPixels((float)mCurrentAccuracy));
            	canvas.drawCircle(pointA.x, pointA.y, radius, paint);
            }
            return v;
        }
    }

    static public class StatsView {
    	private final TextView mValueView;
    	private final TextView mTitleView;
    	private final String mDisplayType;
    	/**
    	 * 
    	 * @param value_view_id
    	 * @param title_view_id
    	 * 
    	 * @param displayType One of the values defined in display_type_values in array.xml. For example,
    	 * "total_distance".
    	 */
    	public StatsView(Activity parent, int value_view_id, int title_view_id, String displayType) {
    		mValueView = (TextView)parent.findViewById(value_view_id);
    		mTitleView = (TextView)parent.findViewById(title_view_id);
    		mDisplayType = displayType;
    	}
    	
    	public void update(LapStats totalStats, LapStats userLapStats, LapStats autoLapStats) {
    		String value = "         ";
    		String title = "YY";
    		
    		// newerStats is the newer of {user,auto}LapStats.
    		LapStats newerLapStats = userLapStats;
    		if (autoLapStats != null &&
    				(autoLapStats == null || autoLapStats.getStartTimeSeconds() < autoLapStats.getStartTimeSeconds())) {
    			newerLapStats = autoLapStats;
    		}
    		if (mDisplayType.equals("none")) {
    			;
    		} else if (mDisplayType.equals("total_distance")) {
    			title = "Total " + Util.distanceUnitString();
    			if (totalStats != null) {
    				value = Util.distanceToString(totalStats.getDistance());
    			}
    		} else if (mDisplayType.equals("total_duration")) {
    			title = "Total time";
    			if (totalStats != null) {
    				value = Util.durationToString(totalStats.getDurationSeconds());
    			}
    		} else if (mDisplayType.equals("total_pace")) {
    			title = "Avg pace";
    			if (totalStats != null) {
    				value = Util.paceToString(totalStats.getPace());
    			}
    		} else if (mDisplayType.equals("current_pace")) {
    			title = "Cur pace";
    			if (totalStats != null) {
    				value = Util.paceToString(totalStats.getCurrentPace());
    			}
    		} else if (mDisplayType.equals("lap_distance")) {
    			title = "Lap " + Util.distanceUnitString();
    			if (newerLapStats != null) {
    				value = Util.distanceToString(newerLapStats.getDistance());
    			}
    		} else if (mDisplayType.equals("lap_duration")) {
    			title = "Lap time";
    			if (newerLapStats != null) {
    				value = Util.durationToString(newerLapStats.getDurationSeconds());
    			}
    		} else if (mDisplayType.equals("lap_pace")) {
    			title = "Lap pace";
    			if (newerLapStats != null) {
    				value = Util.paceToString(newerLapStats.getPace());
    			}
    		} else if (mDisplayType.equals("auto_lap_distance")) {
    			title = "Autolap " + Util.distanceUnitString();
    			if (autoLapStats != null) {
    				value = Util.distanceToString(autoLapStats.getDistance());
    			}
    		} else if (mDisplayType.equals("auto_lap_duration")) {
    			title = "Autolap time";
    			if (autoLapStats != null) {
    				value = Util.durationToString(autoLapStats.getDurationSeconds());
    			}
    		} else if (mDisplayType.equals("auto_lap_pace")) {
    			title = "Autolap pace";
    			if (autoLapStats != null) {
    				value = Util.paceToString(autoLapStats.getPace());
    			}
    		} else {
    			value = "Unknown display type: " + mDisplayType;
    			title = "???";
    		}

    		mValueView.setText(value);
    		mTitleView.setText(title);
    	}
    }

    private Activity mThisActivity;
    private File mRecordDir;
    private MyOverlay mMapOverlay;
    private MapView mMapView;
    private StatsView mStatsViews[];

    private TextView mWorkoutTitle;
    private ArrayAdapter<String> mWorkoutListAdapter;
    private Spinner mWorkoutListSpinner;
    private Button mStartStopButton;
    private Button mLapButton;    

    private static final int RESET = 0;          // Initial state
    private static final int RUNNING = 1;        // Running state.
    private static final int STOPPED = 2;        // Paused state. The GPS activity is live, but the stats won't count
    private static final int TRANSITIONING = 3;  // Doing async I/O. No state transition allowed until the I/O finishes
    private int mRecordingState = RESET;

    // Current activity. Non-null only when mRecordingState != RESET.
    private HealthGraphClient.JsonActivity mRecord;
    
    private LapStats mTotalStats = null;
    ArrayList<HealthGraphClient.JsonWGS84> mLastReportedPath = null;
    
    public void onGpsUpdate(
    		ArrayList<HealthGraphClient.JsonWGS84> path,
    		LapStats totalStats,
    		LapStats userLapStats,
    		LapStats autoLapStats,
    		Workout currentInterval) {
    	mLastReportedPath = path;
    	mMapOverlay.updatePath(path);
    	mTotalStats = totalStats;
    	mMapView.invalidate();
    	for (int i = 0; i < mStatsViews.length; ++i) {
    		mStatsViews[i].update(totalStats, userLapStats, autoLapStats);
    	}
    	if (currentInterval != null) {
    		StringBuilder b = new StringBuilder(); 
			Workout.addIntervalToDisplayStringTo(
					currentInterval.duration, 
					currentInterval.distance, 
					currentInterval.fastTargetPace, 
					currentInterval.slowTargetPace, b);
    		mWorkoutTitle.setText(b.toString());
    	}
    }

    private LocationManager mLocationManager;
    private LocationListener mLocationListener; 

    @Override public void onPause() {
    	super.onPause();
    	GpsTrackingService.unregisterListener(this);
        mLocationManager.removeUpdates(mLocationListener);
    }

    // List of workout files, under FileManager.getWorkoutDir().
    // The first entry is always null (corresponds to "no workout").
    // The i'th entry (i>0) corresponds to the i'th entry in the mWorkoutListSpinner.
    private ArrayList<String> mWorkoutFiles = new ArrayList<String>();
    
    private void startListWorkouts() {
    	final File dir = FileManager.getWorkoutDir(this);
    	FileManager.listFilesAsync(dir, new FileManager.ListFilesListener() {
			public void onFinish(Exception e, ArrayList<FilenameSummary> files) {
				if (e != null) {
					Toast.makeText(mThisActivity, "Failed to list " + dir + ": " + e, Toast.LENGTH_LONG).show();
				} else {
					mWorkoutListAdapter.clear();
					mWorkoutFiles.clear();
					mWorkoutListAdapter.add("None");
					mWorkoutFiles.add(null);
					for (FilenameSummary f : files) {
						mWorkoutListAdapter.add(f.getString(FileManager.KEY_WORKOUT_NAME, "unknown"));
						mWorkoutFiles.add(f.getBasename());
					}
					mWorkoutListAdapter.notifyDataSetChanged();
				}
			}
		});
    }
    
    @Override public void onResume() {
    	super.onResume();

        // Define a listener that responds to location updates
        mLocationListener = new LocationListener() {
    		public void onLocationChanged(Location location) {
    			HealthGraphClient.JsonWGS84 wgs = new HealthGraphClient.JsonWGS84();
    			wgs.latitude = location.getLatitude();
    			wgs.longitude = location.getLongitude();
    			wgs.altitude = location.getAltitude();
    			mMapOverlay.setCurrentLocation(wgs, location.getAccuracy());
    			mMapView.invalidate();
			}
			public void onProviderDisabled(String provider) { }
			public void onProviderEnabled(String provider) { }
			public void onStatusChanged(String provider, int status, Bundle extras) {
			}        
        };
        // Register the listener with the Location Manager to receive location updates
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);

        mStatsViews = new StatsView[6];
        mStatsViews[0] = new StatsView(this, R.id.view0, R.id.view0_title, Settings.viewTypes[0]);
        mStatsViews[1] = new StatsView(this, R.id.view1, R.id.view1_title, Settings.viewTypes[1]);        
        mStatsViews[2] = new StatsView(this, R.id.view2, R.id.view2_title, Settings.viewTypes[2]);        
        mStatsViews[3] = new StatsView(this, R.id.view3, R.id.view3_title, Settings.viewTypes[3]);        
        mStatsViews[4] = new StatsView(this, R.id.view4, R.id.view4_title, Settings.viewTypes[4]);        
        mStatsViews[5] = new StatsView(this, R.id.view5, R.id.view5_title, Settings.viewTypes[5]);
        for (int i = 0; i < mStatsViews.length; ++i) {
        	mStatsViews[i].update(null,  null, null);
        }

    	startListWorkouts();
        mRecordingState = GpsTrackingService.getServiceState();
        if (mRecordingState == RUNNING) {
        	mStartStopButton.setText(R.string.pause); 
        } else if (mRecordingState == RESET) {
        	mStartStopButton.setText(R.string.start);
        } else {
        	mStartStopButton.setText(R.string.resume);
        }
        
        // RegisterListener may call the listener method
        GpsTrackingService.registerListener(this);
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.recording);
        mThisActivity = this;
        mRecordDir = FileManager.getRecordDir(this);
        mMapOverlay = new MyOverlay();
        mMapView = (MapView)findViewById(R.id.map_view);
        mMapView.getOverlays().add(mMapOverlay);
        mMapView.setBuiltInZoomControls(true);

        mLocationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);

        mWorkoutTitle = (TextView)findViewById(R.id.workout_title);
        mWorkoutListSpinner = (Spinner)findViewById(R.id.workout_spinner);
        mWorkoutListAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        mWorkoutListSpinner.setAdapter(mWorkoutListAdapter);
        mWorkoutListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mLapButton = (Button)findViewById(R.id.lap_button);
    	mLapButton.setEnabled(false);  // the lap button is disabled unless the activity is running
        mLapButton.setOnClickListener(new Button.OnClickListener() {
        	public void onClick(View v) {
        		GpsTrackingService service = GpsTrackingService.getSingleton();  
        		if (service != null) service.onLapButtonPress();
        	}
        });
        
        mStartStopButton = (Button)findViewById(R.id.start_stop_button);
        mStartStopButton.setOnClickListener(new Button.OnClickListener() {
        	public void onClick(View v) {
        		if (mRecordingState == RESET || mRecordingState == STOPPED) {
        			onStartButtonPress();
        		} else {
        			onStopButtonPress();
        		}
        	}
        });
        
        mStartStopButton.setOnLongClickListener(new Button.OnLongClickListener() { 
        	public boolean onLongClick(View v) {
        		onResetButtonPress();
        		return true;
        	}
        });        
    }

    @Override
    public void onDestroy() {
    	super.onDestroy();
    	if (Util.ASSERT_ENABLED && GpsTrackingService.isListenerRegistered(this)) {
    		Util.crash(this, "onPause not called??");
    	}
    }

    void onStartButtonPress() {
    	if (mRecordingState == TRANSITIONING) return;
    	
    	final int pos = mWorkoutListSpinner.getSelectedItemPosition();
    	final String workoutFilename = (pos >= 0  && pos < mWorkoutFiles.size() ? mWorkoutFiles.get(pos) : null);
    	final int lastState = mRecordingState;
    	mRecordingState = TRANSITIONING;
    	
    	FileManager.readFileAsync(FileManager.getWorkoutDir(this), workoutFilename, Workout.class, new FileManager.ReadListener<Workout>() {
    		public void onFinish(Exception e, Workout workout) {
    			mLapButton.setEnabled(true);
    			mStartStopButton.setText(R.string.pause); 
    			mWorkoutListSpinner.setVisibility(View.GONE);
    			mWorkoutTitle.setText("Workout: foobarfoobarfoobar");
    			if (Util.ASSERT_ENABLED && mRecordingState != TRANSITIONING)
    				Util.crash(mThisActivity, "Wrong state: " + mRecordingState);
    			if (lastState == RESET) {
    				// Starting a new activity
    				mMapOverlay.clearPath();
    				mMapView.invalidate();
    				
    				mRecord = new HealthGraphClient.JsonActivity();
    				mRecord.type = "Running";  // TODO: allow changing
    				mRecord.start_time = HealthGraphClient.generateStartTimeString(System.currentTimeMillis() / 1000.0);
    				mRecord.notes = "Recorded by RunningTrainer";
    				
    				GpsTrackingService.startGpsServiceIfNecessary(mThisActivity, workout);
    			} else if (lastState == STOPPED) {
    				// Resuming an activity
    				GpsTrackingService service = GpsTrackingService.getSingleton();  
    				if (service != null) service.onResumeButtonPress();
    			} else {
    				Util.crash(mThisActivity, "Wrong state: " + lastState);
    			}
    			mRecordingState = RUNNING;
    		}
    	});
    }

    private void onResetButtonPress() {
    	if (mRecordingState == TRANSITIONING) return;
    	
    	mLapButton.setEnabled(false);
    	mStartStopButton.setText(R.string.start); 
    	mRecordingState = RESET;
    	mLapButton.setEnabled(false);
    	mWorkoutListSpinner.setVisibility(View.VISIBLE);
    	mWorkoutTitle.setText("Workout: ");
    	
    	GpsTrackingService.stopGpsServiceIfNecessary(this);
    	if (mTotalStats != null && mRecord != null && mLastReportedPath != null && mLastReportedPath.size() >= 1) {
    		HealthGraphClient.JsonWGS84 last = mLastReportedPath.get(mLastReportedPath.size() - 1);
    		HealthGraphClient.JsonWGS84 wgs = new HealthGraphClient.JsonWGS84();
    		wgs.latitude = last.latitude;
    		wgs.longitude = last.longitude;
    		wgs.altitude = last.altitude;
    		wgs.type = "end";
    		wgs.timestamp = mTotalStats.getDurationSeconds();
    		mLastReportedPath.add(wgs);
    		mRecord.path = new HealthGraphClient.JsonWGS84[mLastReportedPath.size()];
    		for (int i = 0; i < mLastReportedPath.size(); ++i) mRecord.path[i] = mLastReportedPath.get(i);
    		
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
    		
    		final FileManager.FilenameSummary summary = new FileManager.FilenameSummary();
    		summary.putLong(FileManager.KEY_START_TIME, (long)mTotalStats.getStartTimeSeconds());
    		summary.putLong(FileManager.KEY_DISTANCE, (long)mRecord.total_distance);
    		summary.putLong(FileManager.KEY_DURATION, (long)mRecord.duration);
    		FileManager.writeFileAsync(mRecordDir, summary.getBasename(), mRecord, new FileManager.ResultListener() {
				public void onFinish(Exception e) {
					if (e != null) Util.error(mThisActivity, "Failed to write: " + summary.getBasename() + ": " + e);
				}
			});
    		mRecord = null;
    	}
    }	
    
    private void onStopButtonPress() {
    	mLapButton.setEnabled(false);
    	mStartStopButton.setText(R.string.resume); 
    	mRecordingState = STOPPED;
    	GpsTrackingService service = GpsTrackingService.getSingleton();  
    	if (service != null) service.onPauseButtonPress();
    }

    @Override
    public boolean isRouteDisplayed() { return false; }

    @Override
        public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }


}
