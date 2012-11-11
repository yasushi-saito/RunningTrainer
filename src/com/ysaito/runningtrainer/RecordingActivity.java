package com.ysaito.runningtrainer;

import java.io.File;
import java.util.ArrayList;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;
import com.ysaito.runningtrainer.FileManager.ParsedFilename;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class RecordingActivity extends MapActivity implements RecordingService.StatusListener {
    static final String TAG = "Recording";

    void startBackgroundService(JsonWorkout workout) {
    	// Establish a connection with the service.  We use an explicit
    	// class name because we want a specific service implementation that
    	// we know will be running in our own process (and thus won't be
    	// supporting component replacement by other applications).
    	Intent intent = new Intent(this, RecordingService.class);
    	intent.putExtra("workout", workout);
    	getApplicationContext().startService(intent);
    	// Note: this service will stop itself once the recording activity stops (by the user long-pressing the "Stop" button)
    }

    public class MyOverlay extends Overlay {
        private final ChunkedArray<GeoPoint> mPoints = new ChunkedArray<GeoPoint>();
        private double mStartTime = -1.0;
        private final Paint mPaint = new Paint();
        
        private double mCurrentAccuracy = -1.0;    // The latest report on GPS accuracy (meters)
        private double mCurrentLatitude, mCurrentLongitude;  // The last report on GPS location

    	public final void clearPath() {
    		mPoints.clear();
    		mCurrentAccuracy = -1.0;
    	}

        public final void updatePath(double startTime, ChunkedArray<Util.Point> path) {
        	if (Util.ASSERT_ENABLED && path.size() != mPoints.size() + 1) {
        		Util.crash(null, "More than one point added: " + path.size() + ": " + mPoints.size());
        	}
        	if (startTime != mStartTime) {
        		mStartTime = startTime;
        		mPoints.clear();
        	}
        	Util.Point point = path.back();
        	mPoints.add(point.toGeoPoint());
        }

        public final void setCurrentLocation(double latitude, double longitude, double accuracy) {
        	mCurrentLatitude = latitude;
        	mCurrentLongitude = longitude;
        	mCurrentAccuracy = accuracy;
        }

        @Override
        public boolean draw(Canvas canvas, MapView mapView, boolean shadow, long when) {
            boolean v = super.draw(canvas, mapView, shadow, when);
            if (shadow) return v;
            
            Projection projection = mapView.getProjection();
            mPaint.reset();
            mPaint.setAntiAlias(true);
            GraphicsUtil.drawPath(mPoints, mapView.getWidth(), mapView.getHeight(), canvas, projection, mPaint);
            if (mCurrentAccuracy >= 0.0) {
            	GraphicsUtil.drawCurrentPosition((float)mCurrentLatitude, (float)mCurrentLongitude, (float)mCurrentAccuracy,
            			canvas, projection, mPaint);
            }
            if (mPoints.size() > 0) {
            	GeoPoint start = mPoints.front();
            	GraphicsUtil.drawStartPoint(
            			(float)(start.getLatitudeE6() / 1e6), (float)(start.getLongitudeE6() / 1e6), canvas, projection, mPaint);
            	
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
    	
    	public final void update(LapStats totalStats, LapStats lapStats, LapStats lastLapStats, boolean paused) {
    		String value = "";
    		String title = "";
    		
    		if (mDisplayType.equals("none")) {
    			mValueView.setVisibility(View.INVISIBLE);
    			mTitleView.setVisibility(View.INVISIBLE);
    			return;
    		} else if (mDisplayType.equals("total_distance")) {
    			title = "Total " + Util.distanceUnitString();
    			value = Util.distanceToString(totalStats.getDistance(), Util.DistanceUnitType.KM_OR_MILE);
    		} else if (mDisplayType.equals("total_duration")) {
    			title = "Total time";
    			value = Util.durationToString(totalStats.getDurationSeconds());
    		} else if (mDisplayType.equals("total_pace")) {
    			title = "Avg pace";
    			value = Util.paceToString(totalStats.getPace());
    		} else if (mDisplayType.equals("current_pace")) {
    			title = "Cur pace";
    			value = Util.paceToString(totalStats.getCurrentPace());
    		} else if (mDisplayType.equals("lap_distance")) {
    			title = "Lap " + Util.distanceUnitString();
    			value = Util.distanceToString(lapStats.getDistance(), Util.DistanceUnitType.KM_OR_MILE);
    		} else if (mDisplayType.equals("lap_duration")) {
    			title = "Lap time";
    			value = Util.durationToString(lapStats.getDurationSeconds());
    		} else if (mDisplayType.equals("lap_pace")) {
    			title = "Lap pace";
    			value = Util.paceToString(lapStats.getPace());
    		} else if (mDisplayType.equals("last_lap_pace")) {
    			title = "Last lap pace";
    			if (lastLapStats == null) {
    				value = "";
    			} else {
    				value = Util.paceToString(lastLapStats.getPace());
    			}
    		} else {
    			value = "Unknown display type: " + mDisplayType;
    			title = "???";
    		}
    		mValueView.setVisibility(View.VISIBLE);
    		mTitleView.setVisibility(View.VISIBLE);
    		if (paused) {
    			mValueView.setTextColor(0xff505050);
    		} else {
    			mValueView.setTextColor(0xff90ff90);
    		}
    		mValueView.setText(value);
    		mTitleView.setText(title);
    	}
    }

    private RecordingActivity mThisActivity;
    private File mRecordDir;
    private MyOverlay mMapOverlay;
    private MapView mMapView;
    private StatsView mStatsViews[];

    private TextView mWorkoutTitle;
    private ArrayAdapter<String> mWorkoutListAdapter;
    private Spinner mWorkoutListSpinner;
    private Button mStartStopButton;
    private Button mLapButton;    

    private LocationManager mLocationManager;
    private LocationListener mLocationListener; 

    private boolean mTransitioning = false;
    private LapStats mTotalStats = null;

    // List of workout files, under FileManager.getWorkoutDir().
    // The first entry is always null (corresponds to "no workout").
    // The i'th entry (i>0) corresponds to the i'th entry in the mWorkoutListSpinner.
    private ArrayList<String> mWorkoutFiles = new ArrayList<String>();
    
    // Implements RecodingService.StatusListener
	public void onError(String message) {
		showDialog(message);
	}

    // Implements RecodingService.StatusListener
    public void onStatusUpdate(
    		RecordingService.State state,
    		RecordingService.Status status) {
    	if (state == RecordingService.State.RESET) {
    		mStartStopButton.setText(R.string.start);
    		mLapButton.setEnabled(false);
    		mWorkoutListSpinner.setVisibility(View.GONE);
    		mWorkoutListSpinner.setVisibility(View.VISIBLE);
    		mWorkoutTitle.setText("Workout: ");
    	} else {
    		if (state == RecordingService.State.RUNNING || state == RecordingService.State.AUTO_PAUSED) {
    			mStartStopButton.setText(R.string.pause); 
    			mLapButton.setEnabled(true);
    		} else {
    			// newState == USER_PAUSED
    			mStartStopButton.setText(R.string.resume);
    			mLapButton.setEnabled(false);
    		}
    		mWorkoutListSpinner.setVisibility(View.GONE);
    	}        
        if (status != null) {
        	mMapOverlay.updatePath(status.startTime, status.path);
        	mTotalStats = status.totalStats;
        	mMapView.invalidate();
        	for (int i = 0; i < mStatsViews.length; ++i) {
        		mStatsViews[i].update(status.totalStats, status.lapStats, status.lastLapStats,
        				(state == RecordingService.State.AUTO_PAUSED ||
        				state == RecordingService.State.USER_PAUSED));
        	}
        	if (status.currentInterval != null) {
        		StringBuilder b = new StringBuilder(); 
        		JsonWorkout.intervalToDisplayString(
        				status.currentInterval.duration, 
        				status.currentInterval.distance, 
        				status.currentInterval.fastTargetPace, 
        				status.currentInterval.slowTargetPace, b);
        		mWorkoutTitle.setText(b.toString());
        	} else {
        		mWorkoutTitle.setText("No workout set");
        	}
        }
    }

    @Override public void onPause() {
    	super.onPause();
    	RecordingService.unregisterListener(mThisActivity);
        mLocationManager.removeUpdates(mLocationListener);
    }

    private final void startListWorkouts() {
    	final File dir = FileManager.getWorkoutDir(this);
    	FileManager.runAsync(new FileManager.AsyncRunner<ArrayList<FileManager.ParsedFilename>>() {
    		public ArrayList<FileManager.ParsedFilename> doInThread() throws Exception {
    			return FileManager.listFiles(dir);
    		}
    		public void onFinish(Exception e, ArrayList<ParsedFilename> files) {
    			if (e != null) {
    				Toast.makeText(mThisActivity, "Failed to list " + dir + ": " + e, Toast.LENGTH_LONG).show();
    			} else {
    				mWorkoutListAdapter.clear();
    				mWorkoutFiles.clear();
    				mWorkoutListAdapter.add("None");
    				mWorkoutFiles.add(null);
    				for (ParsedFilename f : files) {
    					mWorkoutListAdapter.add(f.getString(FileManager.KEY_WORKOUT_NAME, "unknown"));
    					mWorkoutFiles.add(f.getBasename());
    				}
    				mWorkoutListAdapter.notifyDataSetChanged();
    			}
    		}
    	});
    }

    private final void showDialog(String message) {
        DialogFragment newFragment = MyAlertDialogFragment.newInstance(message);
        newFragment.show(getFragmentManager(), "dialog");
    }

    public final void doPositiveClick() {
        // Do stuff here.
        Log.i("FragmentAlertDialog", "Positive click!");
    }
    
    public final void doNegativeClick() {
        // Do stuff here.
        Log.i("FragmentAlertDialog", "Negative click!");
    }

    public static class MyAlertDialogFragment extends DialogFragment {
        public static MyAlertDialogFragment newInstance(String message) {
            MyAlertDialogFragment frag = new MyAlertDialogFragment();
            Bundle args = new Bundle();
            args.putString("message", message);
            frag.setArguments(args);
            return frag;
        }
        
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
        	final String message = getArguments().getString("message");
            return new AlertDialog.Builder(getActivity())
            		.setIcon(android.R.drawable.ic_dialog_alert)
            		.setTitle("Error")
                    .setMessage(message)
                    .setPositiveButton("Dismiss",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                            }
                        }
                    )
                    .create();
        }
    }

    private MainActivity mMainActivity;
    private double mLastReportedGpsAccuracy = GpsStatusView.NO_GPS_STATUS; 
    
    public final void setMainActivity(MainActivity mainActivity) {
    	mMainActivity = mainActivity;
    }
    
    // TODO: remove
    public void onGpsAccuracyUpdate(double accuracy) {
    	mMainActivity.setGpsStatus(accuracy);
    }
    
    @Override public void onResume() {
    	super.onResume();
    	mMainActivity.setGpsStatus(mLastReportedGpsAccuracy);
    	
        // Define a listener that responds to location updates
        mLocationListener = new LocationListener() {
    		public void onLocationChanged(Location location) {
    			mMapOverlay.setCurrentLocation(location.getLatitude(), location.getLongitude(), location.getAccuracy());
    			
    			MapController controller = mMapView.getController();
    			GeoPoint point = new GeoPoint((int)(location.getLatitude() * 1e6), (int)(location.getLongitude() * 1e6));
    			controller.animateTo(point);
    			mMapView.invalidate();
    			
    			mLastReportedGpsAccuracy = location.getAccuracy();
    			mMainActivity.setGpsStatus(mLastReportedGpsAccuracy);
			}
			public void onProviderDisabled(String provider) {
				mLastReportedGpsAccuracy = GpsStatusView.GPS_DISABLED;
    			mMainActivity.setGpsStatus(mLastReportedGpsAccuracy);
			}
			public void onProviderEnabled(String provider) { 
				mLastReportedGpsAccuracy = GpsStatusView.NO_GPS_STATUS;
    			mMainActivity.setGpsStatus(mLastReportedGpsAccuracy);
			}
			public void onStatusChanged(String provider, int status, Bundle extras) {
			}        
        };
        
        // Register the listener with the Location Manager to receive location updates
        if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        		!mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
        	Util.error(mThisActivity, "GPS not enabled");
        	showDialog("Please enable GPS in Settings / Location services.");
        } else {
        	mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
        }

        mStatsViews = new StatsView[] { 
        		new StatsView(this, R.id.view0, R.id.view0_title, Settings.viewTypes[0]),
        		new StatsView(this, R.id.view1, R.id.view1_title, Settings.viewTypes[1]),
        		new StatsView(this, R.id.view2, R.id.view2_title, Settings.viewTypes[2]),
        		new StatsView(this, R.id.view3, R.id.view3_title, Settings.viewTypes[3]),
        		new StatsView(this, R.id.view4, R.id.view4_title, Settings.viewTypes[4]),
        		new StatsView(this, R.id.view5, R.id.view5_title, Settings.viewTypes[5])
        };
        final LapStats emptyLapStats = new LapStats(); 
        for (int i = 0; i < mStatsViews.length; ++i) {
        	mStatsViews[i].update(emptyLapStats, emptyLapStats, emptyLapStats, true);
        }
    	startListWorkouts();
    	RecordingService.registerListener(mThisActivity);
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
    	GraphicsUtil.maybeInitialize(this);
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
        		final RecordingService gpsService = RecordingService.getSingleton();
        		if (gpsService != null) {
        			gpsService.onLapButtonPress();
        		}
        	}
        });
        
        mStartStopButton = (Button)findViewById(R.id.start_stop_button);
        mStartStopButton.setOnClickListener(new Button.OnClickListener() {
        	public void onClick(View v) {
        		onStartStopButtonPress();
        	}
        });
        
        mStartStopButton.setOnLongClickListener(new Button.OnLongClickListener() { 
        	public boolean onLongClick(View v) {
        		onResetButtonPress();
        		return true;
        	}
        });        
    }

    private String mLastSelectedWorkoutFilename = null;
    private JsonWorkout mLastSelectedWorkout = null;

    public final void setMapMode(int mode) {
    	mMapView.setSatellite(mode == MapMode.SATTELITE);
    }
    
    /**
     * Called when "start/stop" button is pressed. This method creates the background RecordingService and starts recording if necessary.
     * 
     * @param workout The workout chosen by the user. May be null.
     */
    private final void startOrStop(JsonWorkout workout) {
    	RecordingService gpsService = RecordingService.getSingleton(); 
    	if (gpsService == null) {
    		startBackgroundService(workout);
    	} else {
    		gpsService.onStartStopButtonPress();
    	}
    }
    
    void onStartStopButtonPress() {
    	if (mTransitioning) return;

    	final int pos = mWorkoutListSpinner.getSelectedItemPosition();
    	final String workoutFilename = (pos >= 0  && pos < mWorkoutFiles.size() ? mWorkoutFiles.get(pos) : null);

    	if (workoutFilename == null) {
    		startOrStop(null);
    	} else if (mLastSelectedWorkoutFilename != null && mLastSelectedWorkoutFilename.equals(workoutFilename)) {
    		startOrStop(mLastSelectedWorkout);
    	} else {
    		// Read the workout from the sdcard
    		final File dir = FileManager.getWorkoutDir(this);
    		mTransitioning = true;
    		FileManager.runAsync(new FileManager.AsyncRunner<JsonWorkout>() {
    			public JsonWorkout doInThread() throws Exception {
    				return FileManager.readJson(dir, workoutFilename, JsonWorkout.class);
    			}
    			public void onFinish(Exception error, JsonWorkout workout) {
    				if (!mTransitioning) Util.crash(mThisActivity, "blah");
    				mTransitioning = false;
    				if (error != null) {
    					Util.error(mThisActivity, "Failed to read workout: " + error);
    					// Fallthrough
    				} else {
    					mLastSelectedWorkoutFilename = workoutFilename;
    					mLastSelectedWorkout = workout;
    				}
    				startOrStop(workout);
    			}
			});
    	}
    }

    private final void onResetButtonPress() {
    	if (mTransitioning) return;
    	
    	mLapButton.setEnabled(false);
    	mStartStopButton.setText(R.string.start); 
    	mLapButton.setEnabled(false);
    	mWorkoutListSpinner.setVisibility(View.VISIBLE);
    	mWorkoutTitle.setText("Workout: ");

    	final RecordingService gpsService = RecordingService.getSingleton();
    	
    	if (gpsService != null) {
    		RecordingService.Status status = gpsService.resetActivityAndStop();
    		
    		if (status != null && status.path.size() > 1) {
    			final JsonActivity record = new JsonActivity(); 
    			record.type = "Running";  // TODO: allow changing
    			record.start_time = HealthGraphClient.generateStartTimeString(status.startTime);
    			record.notes = "Recorded by RunningTrainer";
    			record.post_to_facebook = Settings.postToFacebook;
    			record.post_to_twitter = Settings.postToTwitter;
    			
    			// Copy the path entries out
    			record.path = new JsonWGS84[status.path.size()];
    			int i = 0;
    			for (Util.Point point : status.path) { 
    				JsonWGS84.PathMode mode;
    				if (i == 0) {
    					mode = JsonWGS84.PathMode.START;
    				} else if (i == status.path.size() - 1) {
    					mode = JsonWGS84.PathMode.END;
    				} else {
    					mode = JsonWGS84.PathMode.MIDDLE;
    				}
    				record.path[i] = JsonWGS84.fromPoint(point, status.startTime, mode);
    				++i;
    			}
    			record.duration = (status.path.back().absTime - status.path.front().absTime);
    			
    			Util.Point lastLocation = null;
    		
    			float[] distance = new float[1];
    			for (Util.Point location : status.path) {
    				if (lastLocation != null) {
    					Location.distanceBetween(lastLocation.latitude, lastLocation.longitude,
    							location.latitude, location.longitude,
    							distance);
    					record.total_distance += distance[0];
    				}
    				lastLocation = location;
    			}
    		
    			final FileManager.ParsedFilename summary = new FileManager.ParsedFilename();
    			summary.putLong(FileManager.KEY_START_TIME, (long)mTotalStats.getStartTimeSeconds());
    			summary.putLong(FileManager.KEY_DISTANCE, (long)record.total_distance);
    			summary.putLong(FileManager.KEY_DURATION, (long)record.duration);
    		
    			FileManager.runAsync(new FileManager.AsyncRunner<Void>() {
    				public Void doInThread() throws Exception {
    					FileManager.writeJson(mRecordDir, summary.getBasename(), record);
    					return null;
    				}
    				public void onFinish(Exception error, Void value) {
    					if (error != null) Util.error(mThisActivity, "Failed to write: " + summary.getBasename() + ": " + error);
    				}
    			});
    		}
    	}
    }	
    
    @Override
    public boolean isRouteDisplayed() { return false; }

    @Override
        public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
}
