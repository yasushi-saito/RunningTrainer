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

    public static class ActivityStatus {
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
    	public void onGpsUpdate(State state, ActivityStatus status);
    	public void onGpsError(String message);
    }
    
    /*
     * Communication channel between RecordingActivity and GpsTrackingService. Since both objects always
     * reside in one process and are run by the main thread, we just function calls to send GPS updates
     * the GpsTrackingService from RecordingActivity.
     */
    private static StatusListener mListener = null;
    private static GpsTrackingService mSingleton = null;
    
    public static GpsTrackingService getSingleton() { return mSingleton; }
    public static void registerListener(StatusListener listener) {
    	mListener = listener;

    	if (mSingleton != null) {
    		// Call the listener immediately to give the initial stats
    		ActivityStatus status = null;
    		if (mSingleton.mState != State.RESET) {
    			status = new ActivityStatus();
    			status.startTime = mSingleton.mStartTime;
    			status.currentInterval = mSingleton.getCurrentWorkoutInterval();
    			status.path = mSingleton.mPath.getPath();
    			status.lapStats = mSingleton.mLapStats;
    			status.totalStats = mSingleton.mTotalStats;
    		}
    		listener.onGpsUpdate(mSingleton.mState, status);
    	}
    }
    
    public static void unregisterListener(RecordingActivity listener) {
    	mListener = null;
    }
    /*
    private boolean isListenerRegistered(RecordingActivity listener) {
    	return mListeners.contains(listener);
    }*/
    
    enum State {
    	RESET,     // Either the initial state, or the activity has finished
    	RUNNING,   // Currently running
    	STOPPED    // Paused
    }
    private State mState = State.RESET;
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
    }

    public final ActivityStatus resetActivityAndStop() {
    	if (mState == State.RESET) {
    		stopSelf();
    		mSingleton = null;
    		return null;
    	}
    	mState = State.RESET;
		updateTimer();
    	ActivityStatus status = new ActivityStatus();
    	status.startTime = mStartTime;
    	status.currentInterval = getCurrentWorkoutInterval();
    	status.path = mPath.getPath();
    	status.lapStats = mLapStats;
    	status.totalStats = mTotalStats;
    	
    	mTts.stop();  // Discard queued speeches
    	speak("Reset", new SpeakDoneListener() {
    		public void onDone() {
    			stopSelf();
    		}
    	});
    	return status;
    }
    
    public final void onStartStopButtonPress() {
    	if (mState == State.RESET) {
    		;
    	} else if (mState == State.STOPPED) {
    		speak("Resumed", null);
    		mState = State.RUNNING;
    		mTotalStats.onResume();
    		mLapStats.onResume();
    	} else {
    		speak("Paused", null);
    		mState = State.STOPPED;  
    		mTotalStats.onPause();
    		mLapStats.onPause();
    	}
    	updateTimer();
    	updateStats(LapType.KEEP_CURRENT_LAP_IF_POSSIBLE);
    }
    
    public final void onLapButtonPress() {
    	speak("New lap", null);
    	updateStats(LapType.FORCE_NEW_LAP);
    }

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
    	if (types.lapDuration) {
    		Log.d(TAG, "LAPSTATS: " + mLapStats.getDurationSeconds());
    		speak("Lap time " + Util.durationToSpeechText(mLapStats.getDurationSeconds()), null);
    	}
    	if (types.lapPace)
    		speak("Lap pace " + Util.paceToSpeechText(mLapStats.getPace()), null);
    }
    
    private long mLastReportedTotalDistance = 0;
    private long mLastReportedTotalDuration = 0;
    
    // The total distance value when the mLapStats was reset.
    // Used to perform auto lapping.
    private long mLapStartDistance = 0;

    private String mLastPaceAlertText = "";
    private double mLastPaceAlertTime = 0.0;

    enum LapType {
    	KEEP_CURRENT_LAP_IF_POSSIBLE,
		FORCE_NEW_LAP
    }
    private void updateStats(LapType lapType) {
    	boolean newLap = (lapType == LapType.FORCE_NEW_LAP);

    	final Workout currentWorkout = getCurrentWorkoutInterval();
    	if (currentWorkout != null) {
    		// When a workout interval is active, ignore the autolap setting
    		// and continue the lap until the interval specifies.
    		if ((currentWorkout.distance > 0.0 &&
    				mLapStats.getDistance() >= currentWorkout.distance) ||
    			(currentWorkout.duration > 0.0 &&
    				mLapStats.getDurationSeconds() >= currentWorkout.duration)) {
    			// Delay installing a new lap until speech is made (speech should use
    			// the last lap's stats, not the empty new stats).
    			newLap = true;
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
    	} else if (Settings.autoLapDistanceInterval > 0) {
    		// Check if the autolap distance has been covered
    		final int interval = (int)Settings.autoLapDistanceInterval;
    		final long newDistance = (long)mTotalStats.getDistance();
    		if ((mLapStartDistance / interval) != (newDistance / interval)) {
    			mLapStartDistance = newDistance;
    			newLap = true;
    		}
    	}
    	
    	// Read the stats out if necessary
    	Settings.SpeechTypes types = null;
    	if (newLap) {
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

    	if (newLap) {
    		startNewLap();
    	}
    	
    	// Notify the listener
    	ActivityStatus status = null;
    	if (mState != State.RESET) {
    		status = new ActivityStatus();
    		status.startTime = mStartTime;
    		status.currentInterval = getCurrentWorkoutInterval();
    		status.path = mPath.getPath();
    		status.lapStats = mLapStats;
    		status.totalStats = mTotalStats;
    	}
    	if (mListener != null) {
    		mListener.onGpsUpdate(mState, status);
    	}
    }

    private void notifyError(String message) {
    	if (mListener != null) {
    		mListener.onGpsError(message);
    	}
    }

    private final Workout getCurrentWorkoutInterval() {
    	return (mWorkoutIterator != null && !mWorkoutIterator.done()) ? 
    			mWorkoutIterator.getWorkout() :	null;
    }
    
	private LocationListener mLocationListener;
	private LocationManager mLocationManager;

	// List of gps points recorded so far.
	private HealthGraphClient.PathAggregator mPath = null;
	
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
		Settings.Initialize(getApplicationContext());

		mId = mInstanceSeq++;
		Toast.makeText(this, toString() + ": Created", Toast.LENGTH_LONG).show();
		Log.d(TAG, toString() + ": Created");
		
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
        		mTtsReady = true;
        		mTts.setLanguage(Locale.US);
        		if (mQueuedSpeechRequests.size() > 0) {
        			for (String s : mQueuedSpeechRequests) {
        				speak(s, null);
        			}
        			mQueuedSpeechRequests.clear();
        		}
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
    private boolean mTtsReady = false;
    private ArrayList<String> mQueuedSpeechRequests = new ArrayList<String>();
    
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
		HashMap<String, String> params = null;
		if (listener != null) {
			params = new HashMap<String, String>();
			String key = "utterance:" + mUtteranceId++;
			params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, key);
			mSpeakDoneListeners.put(key, listener);
		}
		if (!mTtsReady) {
			mQueuedSpeechRequests.add(text);
		} else {
			mTts.speak(text, TextToSpeech.QUEUE_ADD, params);
		}
	}

	private void updateTimer() {
    	if (mTimer != null) {
    		mTimer.cancel();
    		mTimer.purge();
    		mTimer = null;
    	}
		if (mState == State.RUNNING) {
			mTimer = new Timer();
			mTimer.schedule(new TimerTask() {
				@Override public void run() {
					Handler handler = new Handler(Looper.getMainLooper());
					handler.post(new Runnable() {
						public void run() { 
							if (Settings.fakeGps) dofakeGpsLocationUpdate();
							updateStats(LapType.KEEP_CURRENT_LAP_IF_POSSIBLE);
						}
					});
				}
			}, 0, 1000);
		}
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, toString() + ": Destroyed");
		mState = State.RESET;
		updateTimer();
		Toast.makeText(this, toString() + ": Stopped", Toast.LENGTH_LONG).show();
		mLocationManager.removeUpdates(mLocationListener);
		mSingleton = null;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, toString() + ": OnStart " + startId + " state=" + mState);
		mSingleton = this;
		if (mState != State.RESET) {
			// The activity already running.
			// Happens when startService is called after the activity has
			// resumed from previous sleep.
		} else {
			Workout workout = (Workout)intent.getSerializableExtra("workout");
			speak("started", null);
    		
			mPath = new HealthGraphClient.PathAggregator();
			if (Settings.fakeGps) {
				mPath.addPoint(0.0, 100.0, 100.0, 0);
			}
			mTotalStats = new LapStats();
			mLapStats = new LapStats();
			mStartTime = System.currentTimeMillis() / 1000.0;
			mState = State.RUNNING;
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
			updateTimer();
		}
		return START_STICKY;
	}
	
	private void onGpsLocationUpdate(long now, Location newLocation) {
		if (mState == State.RUNNING) {
			final double timestamp = mTotalStats.getDurationSeconds();
			mPath.addPoint(timestamp,
					newLocation.getLatitude(),
					newLocation.getLongitude(),
					newLocation.getAltitude());
			mTotalStats.onGpsUpdate(timestamp, newLocation.getLatitude(), newLocation.getLongitude());
			mLapStats.onGpsUpdate(timestamp, newLocation.getLatitude(), newLocation.getLongitude());
			updateStats(LapType.KEEP_CURRENT_LAP_IF_POSSIBLE);
		}
	}

	private void dofakeGpsLocationUpdate() {
		if (mState == State.RUNNING) {
			if (mPath.getPath().size() > 0) {
				HealthGraphClient.JsonWGS84 lastWgs = mPath.lastPoint();
				mPath.addPoint(lastWgs.timestamp + 0.3, lastWgs.latitude + 0.0008, lastWgs.longitude, lastWgs.altitude);
				mTotalStats.onGpsUpdate(lastWgs.timestamp + 0.3, lastWgs.latitude + 0.0008, lastWgs.longitude);
				mLapStats.onGpsUpdate(lastWgs.timestamp + 0.3, lastWgs.latitude + 0.0008, lastWgs.longitude);
				updateStats(LapType.KEEP_CURRENT_LAP_IF_POSSIBLE);
			}
		}
	}
}
