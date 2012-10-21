package com.ysaito.runningtrainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
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

/**
 * Background service that runs while a recording is ongoing
 *
 */
public class RecordingService extends Service {
	private static String TAG = "Gps";
    private NotificationManager mNM;
    private boolean mAutoPaused = false;
    private boolean mUserPaused = false;
    private boolean mStarted = false;
    
    @Override
    public IBinder onBind(Intent intent) {
    	Plog.d(TAG, "onBind");
    	return null;
    }

    enum State {
    	RESET,        // Either the initial state, or the activity has finished
    	RUNNING,      // Currently running
    	USER_PAUSED,  // Paused by the user using through the UI button
    	AUTO_PAUSED
    }
    public static class Status {
    	double startTime;
    	ArrayList<Util.Point> path;
    	LapStats totalStats;
    	LapStats lapStats;
    	JsonWorkout currentInterval;
    }
    public interface StatusListener {
    	/**
    	 * Called every one second, or when a GPS update is received
    	 * @param state Whether the timer is running or paused
    	 */
    	public void onStatusUpdate(State state, Status status);
    	public void onError(String message);
    	
    	// TODO: this is just for testing. remove once GpsStatusView debugging is done.
    	public void onGpsAccuracyUpdate(double meters);
    }
    
    /*
     * Communication channel between RecordingActivity and GpsTrackingService. Since both objects always
     * reside in one process and are run by the main thread, we just function calls to send GPS updates
     * the GpsTrackingService from RecordingActivity.
     */
    private static StatusListener mListener = null;
    private static RecordingService mSingleton = null;
    
    public static RecordingService getSingleton() { return mSingleton; }
    public static void registerListener(StatusListener listener) {
    	mListener = listener;

    	if (mSingleton != null) {
    		// Call the listener immediately to give the initial stats
    		Status status = null;
    		if (mSingleton.mStarted) {
    			status = new Status();
    			status.startTime = mSingleton.mStartTime;
    			status.currentInterval = mSingleton.getCurrentWorkoutInterval();
    			status.path = mSingleton.mPath.getPath();
    			status.lapStats = mSingleton.mLapStats;
    			status.totalStats = mSingleton.mTotalStats;
    		}
    		listener.onStatusUpdate(mSingleton.getState(), status);
    	}
    }
    
    private State getState() {
    	if (!mStarted) return State.RESET;
    	if (mUserPaused) return State.USER_PAUSED;
    	if (mAutoPaused) return State.AUTO_PAUSED;
    	return State.RUNNING;
    }

    public static void unregisterListener(RecordingActivity listener) {
    	mListener = null;
    }
    
    private final void startNewLap() {
    	if (mWorkoutIterator != null && !mWorkoutIterator.done()) {
    		mWorkoutIterator.next();
    		final JsonWorkout newWorkout = getCurrentWorkoutInterval();
    		if (newWorkout == null) {
    			speak("Workout ended", null);
    		} else {
    			speak(JsonWorkout.workoutToSpeechText(newWorkout), null);
    		}
    	}
    	final long curDistance = (long)mTotalStats.getDistance();
    	mLapStartDistance = curDistance;
    	mLapStats = new LapStats();
    }

    public final Status resetActivityAndStop() {
    	if (!mStarted) {
    		// Shouldn't happen in practice
    		stopSelf();
    		mSingleton = null;
    		return null;
    	}
    	mStarted = false;
		updateTimer();

		Status status = new Status();
    	status.startTime = mStartTime;
    	status.currentInterval = getCurrentWorkoutInterval();
    	status.path = mPath.getPath();
    	status.lapStats = mLapStats;
    	status.totalStats = mTotalStats;

    	mTts.stop();  // Discard queued speeches
    	mNM.cancel(1);
    	speak("Reset", new SpeakDoneListener() {
    		public void onDone() {
    			stopSelf();
    		}
    	});
    	return status;
    }
    
    public final void onStartStopButtonPress() {
    	final double now = System.currentTimeMillis() / 1000.0;
    	State prevState = getState();
    	if (!mStarted) {
    		;
    	} else if (mUserPaused) {
    		mUserPaused = false;
    		mTotalStats.onResume(now);
    		mLapStats.onResume(now);
    	} else {
    		mUserPaused = true;
    		mTotalStats.onPause(now);
    		mLapStats.onPause(now);
    	}
    	State newState = getState();
    	if (prevState == State.RUNNING && newState == State.USER_PAUSED) {
    		speak("paused", null);
    	} else if (prevState == State.USER_PAUSED && newState == State.RUNNING) {
    		speak("resumed", null);
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

    private static final String ON_TARGET = "On target";
    private static final String FASTER = "Faster";
    private static final String SLOWER = "Slower";
    private static final double MIN_PACE_ALERT_INTERVAL_SECONDS = 10.0;
    
    private String mLastPaceAlertText = ON_TARGET;
    private double mLastPaceAlertTime = 0.0;
    private double mLastPaceAlertIntervalSeconds = 5.0;
    
    enum LapType {
    	KEEP_CURRENT_LAP_IF_POSSIBLE,
		FORCE_NEW_LAP
    }
    private void updateStats(LapType lapType) {
    	boolean newLap = (lapType == LapType.FORCE_NEW_LAP);

    	final JsonWorkout currentWorkout = getCurrentWorkoutInterval();
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
    			String text = ON_TARGET;
    			if (pace > currentWorkout.slowTargetPace) {
    				text = FASTER;
    			} else if (pace != 0.0 && pace < currentWorkout.fastTargetPace) {
    				// Pace==0.0 if no movement. Shut up in that case
    				text = SLOWER;
    			} 
    			double nowSeconds = System.currentTimeMillis() / 1000.0;
    			if (text != mLastPaceAlertText) {
    				if (mLastPaceAlertTime + MIN_PACE_ALERT_INTERVAL_SECONDS < nowSeconds) {
    					speak(text + ";<300>;" + Util.paceToSpeechText(pace), null);
    					mLastPaceAlertText = text;
    					mLastPaceAlertTime = nowSeconds;
    					mLastPaceAlertIntervalSeconds = MIN_PACE_ALERT_INTERVAL_SECONDS; 
    				}
    			} else if (text == ON_TARGET) {
    				;
    			} else {
    				if (mLastPaceAlertTime + mLastPaceAlertIntervalSeconds < nowSeconds) {
    					speak(text + ";<300>;" + Util.paceToSpeechText(pace), null);
    					mLastPaceAlertText = text;
    					mLastPaceAlertTime = nowSeconds;
    					mLastPaceAlertIntervalSeconds = Math.min(
    							mLastPaceAlertIntervalSeconds * 1.5,
    							30.0);
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
    	Status status = null;
    	if (mStarted) {
    		status = new Status();
    		status.startTime = mStartTime;
    		status.currentInterval = getCurrentWorkoutInterval();
    		status.path = mPath.getPath();
    		status.lapStats = mLapStats;
    		status.totalStats = mTotalStats;
    	}
    	if (mListener != null) {
    		mListener.onStatusUpdate(getState(), status);
    	}
    }

    private void notifyError(String message) {
    	if (mListener != null) {
    		mListener.onError(message);
    	}
    }

    private final JsonWorkout getCurrentWorkoutInterval() {
    	return (mWorkoutIterator != null && !mWorkoutIterator.done()) ? 
    			mWorkoutIterator.getWorkout() :	null;
    }
    
	private LocationListener mLocationListener;
	private LocationManager mLocationManager;

	private double mStartTime = -1.0;
	
	// List of gps points recorded so far.
	private PathAggregator mPath = null;
	
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
    	Plog.d(TAG, "onCreate");
    	Plog.init(getApplicationContext());
		Settings.Initialize(getApplicationContext());
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

		mId = mInstanceSeq++;
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
        			for (String r : mQueuedSpeechRequests) {
        				speak(r, null);
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
	
	private static final Pattern RE_PAUSE = Pattern.compile("^<([0-9]+)>$");
	
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
			String[] tokens = text.split(";");
			for (int i = 0; i < tokens.length; ++i) {
				String token = tokens[i];
				Matcher m = RE_PAUSE.matcher(token);
				if (m.matches()) {
					int pauseMillis = Integer.parseInt(m.group(1));
					mTts.playSilence(pauseMillis, TextToSpeech.QUEUE_ADD, 
							(i == tokens.length - 1 ? params : null));
				} else {
					mTts.speak(token, TextToSpeech.QUEUE_ADD, 
							(i == tokens.length - 1 ? params : null));
				}
			}
		}
	}

	private void updateTimer() {
    	if (mTimer != null) {
    		mTimer.cancel();
    		mTimer.purge();
    		mTimer = null;
    	}
		if (getState() == State.RUNNING) {
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
		super.onDestroy();
    	Plog.d(TAG, "onDestroy");
		Log.d(TAG, toString() + ": Destroyed");
		mStarted = false;
		updateTimer();
		mLocationManager.removeUpdates(mLocationListener);
		mSingleton = null;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, toString() + ": OnStart " + startId + " state=" + getState());
		mSingleton = this;
		if (mStarted) {
			// The activity already running.
			// Happens when startService is called after the activity has
			// resumed from previous sleep.
		} else {
			JsonWorkout workout = (JsonWorkout)intent.getSerializableExtra("workout");
			speak("started", null);
			mPath = new PathAggregator(Settings.autoPauseDetection, Settings.smoothGps);
			mTotalStats = new LapStats();
			mLapStats = new LapStats();
			mStartTime = System.currentTimeMillis() / 1000.0;
			mStarted = true;
			mUserPaused = false;
			mAutoPaused = false;
			if (workout != null) {
				mWorkoutIterator = new WorkoutIterator(new JsonWorkout(workout));
			} else {
				mWorkoutIterator = null;
			}
			if (!Settings.fakeGps) {
				if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
						!mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
					notifyError("Please enable GPS in Settings / Location services.");
				} else {
					mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
					mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);
				}
			}
	        Notification notification = new Notification(R.drawable.running_logo, 
	        		"Started recording",
	        		System.currentTimeMillis());
	        // The PendingIntent to launch our activity if the user selects this notification
	        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
	                new Intent(this, MainActivity.class), 0);

	        notification.setLatestEventInfo(this, "RunningTrainer", 
	        		"Recording since " + Util.dateToString(System.currentTimeMillis()/1000),
	        		contentIntent);

	        mNM.notify(1, notification);
			updateTimer();
		}
		return START_NOT_STICKY;
	}
	
	private void onGpsLocationUpdate(long now, Location newLocation) {
		if (mStarted) {
			PathAggregator.Result result = mPath.addLocation(
					System.currentTimeMillis() / 1000.0,
					newLocation.getLatitude(),
					newLocation.getLongitude(),
					newLocation.getAltitude());
			handleResult(result);
			updateStats(LapType.KEEP_CURRENT_LAP_IF_POSSIBLE);
		}
	}

	private int mNumFakeInputs = 0;
	
	private void dofakeGpsLocationUpdate() {
		if (mStarted) {
			double now = System.currentTimeMillis() / 1000.0;
			if (mPath.getPath().size() == 0) {
				mPath.addLocation(now, 38.00, -120.0, 0);
			} else {
				Util.Point lastWgs = mPath.getPath().get(mPath.getPath().size() - 1);
				double latitude, longitude;
				if (mNumFakeInputs % 25 == 0) {
					// Simulate a jump in GPS reading
					latitude = 39.0;
					longitude = -121.0;
				} else if (mNumFakeInputs % 20 > 10) {
					latitude = lastWgs.latitude + 0.0001;
					longitude = lastWgs.longitude;
				} else {
					latitude = lastWgs.latitude;
					longitude = lastWgs.longitude;
				}
				PathAggregator.Result result = mPath.addLocation(now, latitude, longitude, lastWgs.altitude);
				Log.d(TAG, "ADD: " + latitude + " " + lastWgs.latitude + " r=" + result);
				handleResult(result);
				++mNumFakeInputs;
				updateStats(LapType.KEEP_CURRENT_LAP_IF_POSSIBLE);
			}
			int n = mNumFakeInputs;
			double accuracy = GpsStatusView.GPS_DISABLED;
			if (n % 7 == 0) {
				accuracy = GpsStatusView.GPS_DISABLED;				
			} else if (n % 7 == 1) {
				accuracy = GpsStatusView.NO_GPS_STATUS;
			} else if (n % 7 == 2) {
				accuracy = 100.0;
			} else if (n % 7 == 3) {
				accuracy = 25.0;
			} else if (n % 7 == 4) {
				accuracy = 12.0;
			} else if (n % 7 == 5) {
				accuracy = 7.0;
			} else {
				accuracy = 5.0;
			}
			mListener.onGpsAccuracyUpdate(accuracy);
		}
	}

	private void handleResult(PathAggregator.Result result) {
		if (result.pauseType == Util.PauseType.RUNNING) {
			mTotalStats.onGpsUpdate(result.absTime, result.deltaDistance);
			mLapStats.onGpsUpdate(result.absTime, result.deltaDistance);
		} else if (result.pauseType == Util.PauseType.PAUSE_STARTED) {
			speak("auto paused", null);
			mTotalStats.onPause(result.absTime);
			mLapStats.onPause(result.absTime);			
			mAutoPaused = true;
		} else if (result.pauseType == Util.PauseType.PAUSE_ENDED) {
			speak("auto resumed", null);
			mTotalStats.onResume(result.absTime);
			mLapStats.onResume(result.absTime);			
			mTotalStats.onGpsUpdate(result.absTime, result.deltaDistance);
			mLapStats.onGpsUpdate(result.absTime, result.deltaDistance);
			mAutoPaused = false;
		}
	}
	
}
