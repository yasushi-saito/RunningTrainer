package com.ysaito.runningtrainer;

import java.util.ArrayList;
import java.util.Locale;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * In-memory copy of user preferences and locale-related settings.
 * 
 * Fields are extracted from the sharedPreferenceManager.
 * See SettingsFragment for the code that modifies the settings.
 */
public class Settings {
	// Unit of measurement
	public enum Unit {
		METRIC,   // km, meter
		US,       // mile, feet
	}
	
	static public Unit unit = Unit.METRIC;
	static public Locale locale = Locale.US;
	static public String viewTypes[] = new String[]{"none", "none", "none", "none", "none", "none"};
	
	public static class SpeechTypes {
		public boolean totalDistance;
		public boolean totalDuration;	
		public boolean averagePace;		
		public boolean currentPace;
		public boolean lapDistance;
		public boolean lapDuration;
		public boolean lapPace;	
		public void unionFrom(SpeechTypes other) {
			if (other.totalDistance) totalDistance = true;
			if (other.totalDuration) totalDuration = true;			
			if (other.averagePace) averagePace = true;
			if (other.currentPace) currentPace = true;
			if (other.lapDistance) lapDistance = true;
			if (other.lapDuration) lapDuration = true;
			if (other.lapPace) lapPace = true;
		}
	}
	static public double autoLapDistanceInterval; // in meters. <= 0 if disabled
	static public double speakDistanceInterval;  // in meters. <=0 if disabled
	static public double speakTimeInterval;  // in seconds. <=0 if disabled
	static public boolean speakOnLap;   // speak at the end of a lap  
	static public SpeechTypes speakDistanceTypes = new SpeechTypes();
	static public SpeechTypes speakTimeTypes = new SpeechTypes();	
	static public SpeechTypes speakOnLapTypes = new SpeechTypes();		
	static public boolean fakeGps;
	private static SharedPreferences mPrefs;
	private static SharedPreferences.OnSharedPreferenceChangeListener mListener;
	private static Context mContext;
	
	public interface OnChangeListener {
		public void onChange();
	}
	
	private static ArrayList<OnChangeListener> mListeners = new ArrayList<OnChangeListener>();
	public static void registerOnChangeListener(OnChangeListener listener) {
		mListeners.add(listener);
	}
	public static void UnregisterOnChangeListener(OnChangeListener listener) {
		mListeners.remove(listener);
	}
	
	public static void Initialize(Context context) {
		if (mPrefs == null) {
			mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
			mListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
				public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
					onChange(key);
				}
			};
			mPrefs.registerOnSharedPreferenceChangeListener(mListener);
			
			// Read the initial values.
			onChange("unit");
			for (int i = 0; i < 6; ++i) onChange("display" + i);
			onChange("autolap_distance_interval");
			onChange("fake_gps");
			onChange("speak_distance_interval");
			onChange("speak_distance_total_distance");
			onChange("speak_distance_total_duration");
			onChange("speak_distance_average_pace");
			onChange("speak_distance_current_pace");
			onChange("speak_distance_lap_distance");
			onChange("speak_distance_lap_duration");
			onChange("speak_distance_lap_pace");

			onChange("speak_time_interval");
			onChange("speak_time_total_distance");
			onChange("speak_time_total_duration");
			onChange("speak_time_average_pace");
			onChange("speak_time_current_pace");
			onChange("speak_time_lap_distance");
			onChange("speak_time_lap_duration");
			onChange("speak_time_lap_pace");

			onChange("speak_onlap_total_distance");
			onChange("speak_onlap_total_duration");
			onChange("speak_onlap_average_pace");
			onChange("speak_onlap_current_pace");
			onChange("speak_onlap_lap_distance");
			onChange("speak_onlap_lap_duration");
			onChange("speak_onlap_lap_pace");
		}
		
		// Keep the latest context. It's used only to display Toasts on fatal errors.
		// Newer contexts are likely to be live.
		mContext = context;
	}
	
	@SuppressWarnings("unused")
	private static String TAG = "Settings";

	private static void onChange(String key) {
		if (key.equals("unit")) {
			unit = Unit.METRIC;
			final String value = mPrefs.getString(key, "US");
			if (value.equals("US")) unit = Unit.US;
		} else if (key.startsWith("display")) {
			int index = Integer.parseInt(key.substring(7));
			if (Util.ASSERT_ENABLED && (index < 0 || index >= 6)) {
				Util.crash(mContext, "Out of range display: " + key);
			} else {
				viewTypes[index] = mPrefs.getString(key, "none");
			}
		} else if (key.equals("fake_gps")) {
			fakeGps = mPrefs.getBoolean(key, false);
		} else if (key.equals("autolap_distance_interval")) {
			autoLapDistanceInterval = Double.parseDouble(mPrefs.getString("autolap_distance_interval", "0"));
		} else if (key.equals("speak_time_interval")) {
			speakTimeInterval = Double.parseDouble(mPrefs.getString(key, "0"));
		} else if (key.equals("speak_distance_interval")) {
			speakDistanceInterval = Double.parseDouble(mPrefs.getString(key, "0"));
		} else if (key.equals("speak_distance_total_distance")) {
			speakDistanceTypes.totalDistance = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_distance_total_duration")) {
			speakDistanceTypes.totalDuration = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_distance_average_pace")) {
			speakDistanceTypes.averagePace = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_distance_current_pace")) {
			speakDistanceTypes.currentPace = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_distance_lap_distance")) {
			speakDistanceTypes.lapDistance = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_distance_lap_duration")) {
			speakDistanceTypes.lapDuration = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_distance_lap_pace")) {
			speakDistanceTypes.lapPace = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_time_total_distance")) {
			speakTimeTypes.totalDistance = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_time_total_duration")) {
			speakTimeTypes.totalDuration = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_time_average_pace")) {
			speakTimeTypes.averagePace = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_time_current_pace")) {
			speakTimeTypes.currentPace = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_time_lap_distance")) {
			speakTimeTypes.lapDistance = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_time_lap_duration")) {
			speakTimeTypes.lapDuration = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_time_lap_pace")) {
			speakTimeTypes.lapPace = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_onlap_total_distance")) {
			speakOnLapTypes.totalDistance = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_onlap_total_duration")) {
			speakOnLapTypes.totalDuration = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_onlap_average_pace")) {
			speakOnLapTypes.averagePace = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_onlap_current_pace")) {
			speakOnLapTypes.currentPace = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_onlap_lap_distance")) {
			speakOnLapTypes.lapDistance = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_onlap_lap_duration")) {
			speakOnLapTypes.lapDuration = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_onlap_lap_pace")) {
			speakOnLapTypes.lapPace = mPrefs.getBoolean(key, false);
		} else {
			if (Util.ASSERT_ENABLED) Util.crash(mContext, "Unsupported key: " + key);
		}
		for (OnChangeListener listener : mListeners) {
			listener.onChange();
		}
	}
}
