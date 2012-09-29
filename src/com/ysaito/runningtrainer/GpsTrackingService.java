package com.ysaito.runningtrainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.util.Log;
import android.widget.Toast;

public class GpsTrackingService extends Service {
	private static String TAG = "Gps";
	
	/**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class Binder extends android.os.Binder {
    	GpsTrackingService getService() {
            return GpsTrackingService.this;
        }
    }
    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new Binder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class ActivityStatus {
    	double startTime;
    	ArrayList<HealthGraphClient.JsonWGS84> path;
    	LapStats totalStats;
    	LapStats lapStats;
    	Workout currentInterval;
    }
    
    public interface StatusListener {
    	/**
    	 * 
    	 * @param state
    	 * @param status null if state==RESET. Else nonnull.
    	 */
    	public void onGpsUpdate(
    			int state,
    			ActivityStatus status);
    	public void onGpsError(String message);
    }
    
    /*
     * Communication channel between RecordingActivity and GpsTrackingService. Since both objects always
     * reside in one process and are run by the main thread, we just function calls to send GPS updates
     * the GpsTrackingService from RecordingActivity.
     */
    private final ArrayList<StatusListener> mListeners = new ArrayList<StatusListener>();

    public void registerListener(StatusListener listener) {
    	mListeners.add(listener);

    	// Call the listener immediately to give the initial stats
    	ActivityStatus status = null;
    	if (mState != RESET) {
    		status = new ActivityStatus();
    		status.startTime = mStartTime;
    		status.currentInterval = getCurrentWorkoutInterval();
    		status.path = mPath;
    		status.lapStats = mLapStats;
    		status.totalStats = mTotalStats;
    	}
    	listener.onGpsUpdate(mState, status);
    }
    public void unregisterListener(RecordingActivity listener) {
    	mListeners.remove(listener);
    }
    public boolean isListenerRegistered(RecordingActivity listener) {
    	return mListeners.contains(listener);
    }
    
    public static final int RESET = 0;          // Initial state
    public static final int RUNNING = 1;        // Running state.
    public static final int STOPPED = 2;        // Paused state. The GPS activity is live, but the stats won't count
    
    private int mState = RESET;
    private double mStartTime = -1.0;

    private final void startNewLap() {
    	if (mWorkoutIterator != null && !mWorkoutIterator.done()) {
    		mWorkoutIterator.next();
    		final Workout newWorkout = getCurrentWorkoutInterval();
    		if (newWorkout == null) {
    			speak("Workout ended", null);
    		} else {
    			speak(Workout.workoutToSpeechText(newWorkout), null);
    		}
    	}
    	final long curDistance = (long)mTotalStats.getDistance();
    	mLapStartDistance = curDistance;
    	mLapStats = new LapStats();
    	++mLaps;
    }

    public final ActivityStatus resetActivityAndStop() {
    	if (mState == RESET) {
    		stopSelf();
    		return null;
    	}
    	mState = RESET;
    	ActivityStatus status = new ActivityStatus();
    	status.startTime = mStartTime;
    	status.currentInterval = getCurrentWorkoutInterval();
    	status.path = mPath;
    	status.lapStats = mLapStats;
    	status.totalStats = mTotalStats;
    	
    	speak("Reset", new SpeakDoneListener() {
    		public void onDone() {
    			Log.d(TAG, "Stopping!");
    			stopSelf();
    		}
    	});
    	return status;
    }
    
    public final void onStartStopButtonPress(Workout workout) {
    	if (mState == RESET) {
    		speak("started", null);
    		
    		mPath = new ArrayList<HealthGraphClient.JsonWGS84>();
    		if (FAKE_GPS) {
    			HealthGraphClient.JsonWGS84 wgs = new HealthGraphClient.JsonWGS84();  
    			wgs.latitude = 100.0;
    			wgs.longitude = 100.0;
    			wgs.altitude = 0;
    			wgs.timestamp = 0.0;
    			wgs.type = "start";
    			mPath.add(wgs);
    		}
    		mTotalStats = new LapStats();
    		mLapStats = new LapStats();
    		mStartTime = System.currentTimeMillis() / 1000.0;
    		mState = RUNNING;
    		if (workout != null) {
    			mWorkoutIterator = new WorkoutIterator(new Workout(workout));
    		} else {
    			mWorkoutIterator = null;
    		}
    		if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
    				!mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
    			notifyError("Please enable GPS in Settings / Location services.");
    		} else {
    			mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
    			mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);
    		}
    	} else if (mState == STOPPED) {
    		speak("Resumed", null);
    		mState = RUNNING;
    		mTotalStats.onResume();
    		mLapStats.onResume();
    	} else {
    		speak("Paused", null);
    		mState = STOPPED;  
    		mTotalStats.onPause();
    		mLapStats.onPause();
    	}
    	updateTimer();
    	notifyListeners();
    }
    
    public final void onLapButtonPress() {
    	speak("New lap", null);
    	startNewLap();
    	notifyListeners();
    }
/*
    private static void startGpsServiceIfNecessary(Context context, Workout workout) {
    	if (!mGpsServiceStarted) {
    		mGpsServiceStarted = true;
    		mNewestSpecifiedWorkout = workout;
    		context.startService(new Intent(context, GpsTrackingService.class));
    	}
    }
    
    private static void stopGpsServiceIfNecessary(final Context context) {
    	if (mGpsServiceStarted) {
    		mGpsServiceStarted = false;
    		if (mSingleton != null) {
    			mSingleton.speak("Reset", new SpeakDoneListener() {
    				public void onDone() {
    					context.stopService(new Intent(context, GpsTrackingService.class));
    				}
    			});
    		}
    	}
    }
  */  
    private void speakStats(Settings.SpeechTypes types) {
    	if (types.totalDistance)
    		speak("Total distance " + Util.distanceToSpeechText(mTotalStats.getDistance()), null);
    	if (types.totalDuration)
    		speak("Total time " + Util.durationToSpeechText(mTotalStats.getDurationSeconds()), null);
    	if (types.currentPace)
    		speak("Current pace " + Util.paceToSpeechText(mTotalStats.getCurrentPace()), null);
    	if (types.averagePace)
    		speak("Average pace " + Util.paceToSpeechText(mTotalStats.getPace()), null);
    	if (types.lapDistance)
    		speak("Lap distance " + Util.distanceToSpeechText(mLapStats.getDistance()), null);
    	if (types.lapDuration)
    		speak("Lap time " + Util.durationToSpeechText(mLapStats.getDurationSeconds()), null);
    	if (types.lapPace)
    		speak("Lap pace " + Util.paceToSpeechText(mLapStats.getPace()), null);
    }
    
    private long mLastReportedTotalDistance = 0;
    private long mLastReportedTotalDuration = 0;
    private int mLastReportedLaps= 0;
    private int mLaps = 0;
    
    // The total distance value when the mLapStats was reset.
    // Used to perform auto lapping.
    private long mLapStartDistance = 0;

    private String mLastPaceAlertText = "";
    private double mLastPaceAlertTime = 0.0;
    
    private void updateStats() {
    	final Workout currentWorkout = getCurrentWorkoutInterval();
    	if (currentWorkout != null) {
    		boolean newLap = false;
    		if (currentWorkout.distance > 0.0 &&
    				mLapStats.getDistance() >= currentWorkout.distance) {
    			newLap = true;
    		}
    		if (currentWorkout.duration > 0.0 &&
    				mLapStats.getDurationSeconds() >= currentWorkout.duration) {
    			newLap = true;
    		}
    		if (newLap) {
    			startNewLap();
    		} else {
    			final double pace = mLapStats.getPace();
    			String text = null;
    			if (pace == 0.0) {
    				// No movement. Special case and shut up.
    			} else if (pace > currentWorkout.slowTargetPace) {
    				text = "Faster";
    			} else if (pace < currentWorkout.fastTargetPace) {
    				text = "Slower";
    			}
    			if (text != null) {
    				double nowSeconds = System.currentTimeMillis() / 1000.0;
    				if (!text.equals(mLastPaceAlertText) || 
    						mLastPaceAlertTime + 5.0 < nowSeconds) {
    					speak(text, null);
    					mLastPaceAlertText = text;
    					mLastPaceAlertTime = nowSeconds;
    				}
    			}
    		}
    	}
    	
    	// Autolap is disabled when a workout is active
    	if (Settings.autoLapDistanceInterval > 0 && mWorkoutIterator == null) {
    		final int interval = (int)Settings.autoLapDistanceInterval;
    		final long newDistance = (long)mTotalStats.getDistance();
    		if ((mLapStartDistance / interval) != (newDistance / interval)) {
    			mLapStartDistance = newDistance;
    			mLapStats = new LapStats();
    		}
    	}
    	Settings.SpeechTypes types = null;
    	if (mLaps != mLastReportedLaps) {
    		if (types == null) types = new Settings.SpeechTypes();
    		types.unionFrom(Settings.speakOnLapTypes);
    	}
    	if (Settings.speakDistanceInterval > 0) {
    		final int interval = (int)Math.max(15, Settings.speakDistanceInterval);
    		final long newDistance = (long)mTotalStats.getDistance();
    		if ((mLastReportedTotalDistance / interval) != (newDistance / interval)) {
    			mLastReportedTotalDistance = newDistance;
    			if (types == null) types = new Settings.SpeechTypes();
    			types.unionFrom(Settings.speakDistanceTypes);
    		}
    	}
    	if (Settings.speakTimeInterval > 0) {
    		final int interval = (int)Math.max(5, Settings.speakTimeInterval);
    		final long newDuration = (long)mTotalStats.getDurationSeconds();
    		if (mLastReportedTotalDuration / interval != newDuration / interval) {
    			mLastReportedTotalDuration = newDuration;
    			if (types == null) types = new Settings.SpeechTypes();
    			types.unionFrom(Settings.speakTimeTypes);
    		}
    	}
    	if (types != null) speakStats(types);
    }

    private void notifyError(String message) {
    	for (StatusListener listener : mListeners) {
    		listener.onGpsError(message);
    	}
    }

    private final Workout getCurrentWorkoutInterval() {
    	return (mWorkoutIterator != null && !mWorkoutIterator.done()) ? 
    			mWorkoutIterator.getWorkout() :	null;
    }
    
    private void notifyListeners() {
    	updateStats();
    	ActivityStatus status = null;
    	if (mState != RESET) {
    		status = new ActivityStatus();
    		status.startTime = mStartTime;
    		status.currentInterval = getCurrentWorkoutInterval();
    		status.path = mPath;
    		status.lapStats = mLapStats;
    		status.totalStats = mTotalStats;
    	}
    	for (StatusListener listener : mListeners) {
    		listener.onGpsUpdate(mState, status);
    	}
    }
	
	private LocationListener mLocationListener;
	private LocationManager mLocationManager;

	// List of gps points recorded so far.
	private ArrayList<HealthGraphClient.JsonWGS84> mPath;
	
	// Stats since the beginning of the activity. */
	private LapStats mTotalStats = null;
	
    // Stats since the start of the last lap
    private LapStats mLapStats = null;
    
	
	private static int mInstanceSeq = 0;
	private int mId;
	private WorkoutIterator mWorkoutIterator;
	
	@Override public String toString() {
		return "GpsService[" + mId + "]";
	}
	@Override
	public void onCreate() {
		Log.d(TAG, "CREATED!");
		Settings.Initialize(getApplicationContext());

		mId = mInstanceSeq++;
		Toast.makeText(this, toString() + ": Created", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onCreate");
		
		// Define a listener that responds to location updates
		mLocationListener = new LocationListener() {
			// Ignore location updates at intervals smaller than this limit.
			static final int MIN_RECORD_INTERVAL_MS = 1000;

			long mLastReportTime = 0;
			
			public void onLocationChanged(Location location) {
				// Called when a new location is found by the network location provider.
				// makeUseOfNewLocation(location);
				final long time = System.currentTimeMillis();
				if (time < mLastReportTime + MIN_RECORD_INTERVAL_MS) return;
				onGpsLocationUpdate(time, location);
				
				mLastReportTime = time;
			}
			public void onStatusChanged(String provider, int status, Bundle extras) {
				Log.d(TAG, "Status: " + provider + ": " + status);
			}
			
			public void onProviderEnabled(String provider) {
				Log.d(TAG, "Provider Enabled: " + provider);
			}
			
			public void onProviderDisabled(String provider) {
				Log.d(TAG, "Provider Disabled: " + provider);
			}
		};
		
		// Register the listener with the Location Manager to receive location updates
		mLocationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
		
		mTimer = null;
		updateTimer();
        mTts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
        	public void onInit(int status) {
        		mTts.setLanguage(Locale.US);
        	}
        });
        mTtsListener = new OnUtteranceCompletedListener() {
			public void onUtteranceCompleted(String utteranceId) {
        		SpeakDoneListener listener = mSpeakDoneListeners.get(utteranceId);
        		mSpeakDoneListeners.remove(utteranceId);
        		if (listener != null) listener.onDone();
			}
        };
        mTts.setOnUtteranceCompletedListener(mTtsListener);
	}

    private TextToSpeech mTts;
	private OnUtteranceCompletedListener mTtsListener;
	private Timer mTimer;

	private int mUtteranceId = 0;
	private HashMap<String, SpeakDoneListener> mSpeakDoneListeners = new HashMap<String, SpeakDoneListener>();

	private interface SpeakDoneListener {
		public void onDone();
	}
	/**
	 * Speak "text". If @p listener != null, invoke listener.onDone() after utterance is made.
	 *
	 */
	private void speak(String text, SpeakDoneListener listener) {
		if (listener == null) {
			mTts.speak(text, TextToSpeech.QUEUE_ADD, null);
		} else {
			HashMap<String, String> params = new HashMap<String, String>();
			String key = "utterance:" + mUtteranceId++;
			params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, key);
			mSpeakDoneListeners.put(key, listener);
			mTts.speak(text, TextToSpeech.QUEUE_ADD, params);
		}
	}

	private void updateTimer() {
    	if (mTimer != null) {
    		mTimer.cancel();
    		mTimer.purge();
    		mTimer = null;
    	}
		if (mState == RUNNING) {
			mTimer = new Timer();
			mTimer.schedule(new TimerTask() {
				@Override public void run() {
					Handler handler = new Handler(Looper.getMainLooper());
					handler.post(new Runnable() {
						public void run() { 
							if (FAKE_GPS) dofakeGpsLocationUpdate();
							notifyListeners(); 
						}
					});
				}
			}, 0, 1000);
		}
	}

	private final boolean FAKE_GPS = true;
	
	public void onDestroy() {
		mState = RESET;
		updateTimer();
		Toast.makeText(this, toString() + ": Stopped", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onDestroy");
		mLocationManager.removeUpdates(mLocationListener);
	}
	
	@Override
	public void onStart(Intent intent, int startid) {
	}

	private void onGpsLocationUpdate(long now, Location newLocation) {
		if (mState == RUNNING) {
			HealthGraphClient.JsonWGS84 wgs = new HealthGraphClient.JsonWGS84();
			wgs.latitude = newLocation.getLatitude();
			wgs.longitude = newLocation.getLongitude();
			wgs.altitude = newLocation.getAltitude();
			if (mPath.size() == 0) {
				wgs.timestamp = 0;
				wgs.type = "start";
			} else {
				wgs.timestamp = mTotalStats.getDurationSeconds();
				wgs.type = "gps";
			}
			mPath.add(wgs);
			mTotalStats.onGpsUpdate(wgs);
			mLapStats.onGpsUpdate(wgs);
			notifyListeners();
		}
	}

	private void dofakeGpsLocationUpdate() {
		if (mState == RUNNING) {
			if (mPath.size() > 0) {
				HealthGraphClient.JsonWGS84 wgs = new HealthGraphClient.JsonWGS84();
				HealthGraphClient.JsonWGS84 lastWgs = mPath.get(mPath.size() - 1);
				wgs.latitude = lastWgs.latitude + 0.0008;
				wgs.longitude = lastWgs.longitude;
				wgs.altitude = lastWgs.altitude;
				wgs.timestamp = lastWgs.timestamp + 0.3;
				wgs.type = "gps";
				mPath.add(wgs);
				mTotalStats.onGpsUpdate(wgs);
				mLapStats.onGpsUpdate(wgs);
				notifyListeners();
			}
		}
	}
}
