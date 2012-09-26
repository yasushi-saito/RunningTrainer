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
	
    public interface StatusListener {
    	public void onGpsUpdate(
    			ArrayList<HealthGraphClient.JsonWGS84> path,
    			LapStats totalStats,
    			LapStats userLapStats,
    			LapStats autoLapStats,
    			Workout currentInterval);
    	public void onGpsError(String message);
    }
    
    /*
     * Communication channel between RecordingActivity and GpsTrackingService. Since both objects always
     * reside in one process and are run by the main thread, we just function calls to send GPS updates
     * the GpsTrackingService from RecordingActivity.
     */
    private static final ArrayList<StatusListener> mListeners = new ArrayList<StatusListener>();

    public static void registerListener(StatusListener listener) {
    	mListeners.add(listener);
    	if (mSingleton != null) {
    		// Call the listener immediately to give the initial stats
    		final Workout currentInterval = mSingleton.getCurrentWorkoutInterval();
    		listener.onGpsUpdate(mSingleton.mPath, mSingleton.mTotalStats, mSingleton.mUserLapStats, mSingleton.mAutoLapStats, currentInterval);
    	}
    }
    public static void unregisterListener(RecordingActivity listener) {
    	mListeners.remove(listener);
    }
    public static boolean isListenerRegistered(RecordingActivity listener) {
    	return mListeners.contains(listener);
    }
    
    private static boolean mGpsServiceStarted = false;
    private static GpsTrackingService mSingleton = null;
    
    // Used to pass the workout to the newly created GpsTrackingService instance.
    //
    // TODO: there should be a cleaner way to achieve the the same effect. The use of the global variable is rather ugly.
    private static Workout mNewestSpecifiedWorkout;
    
    
    private static final int RESET = 0;
    private static final int STOPPED = 1;
    private static final int RUNNING = 2;
    private int mState = RUNNING;

    public static GpsTrackingService getSingleton() { return mSingleton; }

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
    	mUserLapStats = new LapStats();
    }
    
    public final void onLapButtonPress() {
    	speak("New lap", null);
    	startNewLap();
    }
    
    public final void onPauseButtonPress() {
    	speak("Paused", null);
    	mState = STOPPED;  
    	mTotalStats.onPause();
    	mUserLapStats.onPause();
    	mAutoLapStats.onPause();
    	updateTimer();
    }
    
    public final void onResumeButtonPress() {
    	speak("Resumed", null);
    	mState = RUNNING;
    	mTotalStats.onResume();
    	mUserLapStats.onResume();
    	mAutoLapStats.onResume();
    	updateTimer();
    }
    
    public static int getServiceState() {
    	if (mSingleton == null) {
    		return RESET;
    	} else {
    		return mSingleton.mState;
    	}
    }
    
    public static void startGpsServiceIfNecessary(Context context, Workout workout) {
    	if (!mGpsServiceStarted) {
    		mGpsServiceStarted = true;
    		mNewestSpecifiedWorkout = workout;
    		context.startService(new Intent(context, GpsTrackingService.class));
    	}
    }
    
    public static void stopGpsServiceIfNecessary(final Context context) {
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
    
    private void speakStats() {
    	// newerStats is the newer of {user,auto}LapStats.
    	LapStats newerLapStats = mUserLapStats;
    	if (newerLapStats.getStartTimeSeconds() < mAutoLapStats.getStartTimeSeconds()) {
    		newerLapStats = mAutoLapStats;
    	}
    	
    	if (Settings.speakTotalDistance)
    		speak("Total distance " + Util.distanceToSpeechText(mTotalStats.getDistance()), null);
    	if (Settings.speakTotalDuration)
    		speak("Total time " + Util.durationToSpeechText(mTotalStats.getDurationSeconds()), null);
    	if (Settings.speakCurrentPace)
    		speak("Current pace " + Util.paceToSpeechText(mTotalStats.getCurrentPace()), null);
    	if (Settings.speakAveragePace)
    		speak("Average pace " + Util.paceToSpeechText(mTotalStats.getPace()), null);
    	if (Settings.speakLapDistance)
    		speak("Lap distance " + Util.distanceToSpeechText(newerLapStats.getDistance()), null);
    	if (Settings.speakLapDuration)
    		speak("Lap time " + Util.durationToSpeechText(newerLapStats.getDurationSeconds()), null);
    	if (Settings.speakLapPace)
    		speak("Lap pace " + Util.paceToSpeechText(newerLapStats.getPace()), null);
    	if (Settings.speakAutoLapDistance)
    		speak("Auto lap distance " + Util.distanceToSpeechText(mAutoLapStats.getDistance()), null);
    	if (Settings.speakAutoLapDuration)
    		speak("Auto lap time " + Util.durationToSpeechText(mAutoLapStats.getDurationSeconds()), null);
    	if (Settings.speakAutoLapPace)
    		speak("Auto lap pace " + Util.paceToSpeechText(mAutoLapStats.getPace()), null);
    }
    
    private long mLastReportedTotalDistance = 0;
    private long mLastReportedTotalDuration = 0;
    private long mLastReportedAutoLapDistance = 0;

    private String mLastPaceAlertText = "";
    private double mLastPaceAlertTime = 0.0;
    
    private void updateStats() {
    	final Workout currentWorkout = getCurrentWorkoutInterval();
    	if (currentWorkout != null) {
    		boolean newLap = false;
    		if (currentWorkout.distance > 0.0 &&
    				mUserLapStats.getDistance() >= currentWorkout.distance) {
    			newLap = true;
    		}
    		if (currentWorkout.duration > 0.0 &&
    				mUserLapStats.getDurationSeconds() >= currentWorkout.duration) {
    			newLap = true;
    		}
    		if (newLap) {
    			startNewLap();
    		} else {
    			final double pace = mUserLapStats.getPace();
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
    	
    	if (Settings.autoLapDistanceInterval > 0) {
    		final int interval = (int)Math.max(50, Settings.autoLapDistanceInterval);
    		final long newDistance = (long)mTotalStats.getDistance();
    		if ((mLastReportedAutoLapDistance / interval) != (newDistance / interval)) {
    			mLastReportedAutoLapDistance = newDistance;
    			mAutoLapStats = new LapStats();
    		}
    	}
    	boolean needSpeak = false;
    	if (Settings.speakDistanceInterval > 0) {
    		final int interval = (int)Math.max(15, Settings.speakDistanceInterval);
    		final long newDistance = (long)mTotalStats.getDistance();
    		if ((mLastReportedTotalDistance / interval) != (newDistance / interval)) {
    			mLastReportedTotalDistance = newDistance;
    			needSpeak = true;
    		}
    	}
    	if (Settings.speakTimeInterval > 0) {
    		final int interval = (int)Math.max(5, Settings.speakTimeInterval);
    		final long newDuration = (long)mTotalStats.getDurationSeconds();
    		if (mLastReportedTotalDuration / interval != newDuration / interval) {
    			mLastReportedTotalDuration = newDuration;
    			needSpeak = true;
    		}
    	}
    	if (needSpeak) speakStats();
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
    	final Workout currentInterval = getCurrentWorkoutInterval();
    	for (StatusListener listener : mListeners) {
    		listener.onGpsUpdate(mPath, mTotalStats, mUserLapStats, mAutoLapStats, currentInterval);
    	}
    }
	
	private LocationListener mLocationListener;
	private LocationManager mLocationManager;

	// List of gps points recorded so far.
	private ArrayList<HealthGraphClient.JsonWGS84> mPath;
	
	// Stats since the beginning of the activity. */
	private LapStats mTotalStats = null;
	
    // Stats since the start of the last manual lap
    private LapStats mUserLapStats = null;
    
    // Stats since the start of the last auto lap, i.e., the one that renews every 1mile or 1km.
    private LapStats mAutoLapStats = null;
    
	
	private static int mInstanceSeq = 0;
	private int mId;
	private WorkoutIterator mWorkoutIterator;
	
	@Override public String toString() {
		return "GpsService[" + mId + "]";
	}
	
	@Override
	public void onCreate() {
		Settings.Initialize(getApplicationContext());
		if (mNewestSpecifiedWorkout != null) {
			mWorkoutIterator = new WorkoutIterator(new Workout(mNewestSpecifiedWorkout));
		}
		
		mId = mInstanceSeq++;
		Toast.makeText(this, toString() + ": Created", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onCreate");
		
		mPath = new ArrayList<HealthGraphClient.JsonWGS84>();
		HealthGraphClient.JsonWGS84 wgs = new HealthGraphClient.JsonWGS84();  
		wgs.latitude = 100.0;
		wgs.longitude = 100.0;
		wgs.altitude = 0;
		wgs.timestamp = 0.0;
		wgs.type = "start";
		mPath.add(wgs);
		
		mTotalStats = new LapStats();
		mUserLapStats = new LapStats();
		mAutoLapStats = new LapStats();
		
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
        		speak("started", null);
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
		mSingleton = this;
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
							dofakeGpsLocationUpdate();
							notifyListeners(); 
						}
					});
				}
			}, 0, 1000);
		}
	}
	
	public void onDestroy() {
		mSingleton = null;
		mState = RESET;
		updateTimer();
		Toast.makeText(this, toString() + ": Stopped", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onDestroy");
		mLocationManager.removeUpdates(mLocationListener);
	}
	
	@Override
	public void onStart(Intent intent, int startid) {
		Toast.makeText(this, toString() + ": Started", Toast.LENGTH_LONG).show();
		if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
				!mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
			notifyError("Please enable GPS in Settings / Location services.");
		} else {
			mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
			mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);
		}
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
			mUserLapStats.onGpsUpdate(wgs);
			mAutoLapStats.onGpsUpdate(wgs);
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
				mUserLapStats.onGpsUpdate(wgs);
				mAutoLapStats.onGpsUpdate(wgs);
				notifyListeners();
			}
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
