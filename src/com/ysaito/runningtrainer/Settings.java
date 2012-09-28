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
	public static final int METRIC = 0;
	public static final int US = 1;
	
	static public int unit = METRIC;
	static public Locale locale = Locale.US;
	static public String viewTypes[] = new String[]{"none", "none", "none", "none", "none", "none"};
	static public double autoLapDistanceInterval; // in meters. <= 0 if disabled
	static public double speakDistanceInterval;  // in meters. <=0 if disabled
	static public double speakTimeInterval;  // in seconds. <=0 if disabled
	static public boolean speakTotalDistance;
	static public boolean speakTotalDuration;	
	static public boolean speakAveragePace;		
	static public boolean speakCurrentPace;
	static public boolean speakLapDistance;
	static public boolean speakLapDuration;
	static public boolean speakLapPace;	
	
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
			onChange("speak_time_interval");
			onChange("speak_distance_interval");
			onChange("speak_total_distance");
			onChange("speak_total_duration");
			onChange("speak_average_pace");
			onChange("speak_current_pace");
			onChange("speak_lap_distance");
			onChange("speak_lap_duration");
			onChange("speak_lap_pace");
		}
		
		// Keep the latest context. It's used only to display Toasts on fatal errors.
		// Newer contexts are likely to be live.
		mContext = context;
	}
	
	@SuppressWarnings("unused")
	private static String TAG = "Settings";

	private static void onChange(String key) {
		if (key.equals("unit")) {
			unit = METRIC;
			final String value = mPrefs.getString(key, "US");
			if (value.equals("US")) unit = US;
		} else if (key.startsWith("display")) {
			int index = Integer.parseInt(key.substring(7));
			if (Util.ASSERT_ENABLED && (index < 0 || index >= 6)) {
				Util.crash(mContext, "Out of range display: " + key);
			} else {
				viewTypes[index] = mPrefs.getString(key, "none");
			}
		} else if (key.equals("autolap_distance_interval")) {
			autoLapDistanceInterval = Double.parseDouble(mPrefs.getString("autolap_distance_interval", "0"));
		} else if (key.equals("speak_time_interval")) {
			speakTimeInterval = Double.parseDouble(mPrefs.getString(key, "0"));
		} else if (key.equals("speak_distance_interval")) {
			speakDistanceInterval = Double.parseDouble(mPrefs.getString(key, "0"));
		} else if (key.equals("speak_total_distance")) {
			speakTotalDistance = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_total_duration")) {
			speakTotalDuration = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_average_pace")) {
			speakAveragePace = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_current_pace")) {
			speakCurrentPace = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_lap_distance")) {
			speakLapDistance = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_lap_duration")) {
			speakLapDuration = mPrefs.getBoolean(key, false);
		} else if (key.equals("speak_lap_pace")) {
			speakLapPace = mPrefs.getBoolean(key, false);
		} else {
			if (Util.ASSERT_ENABLED) Util.crash(mContext, "Unsupported key: " + key);
		}
		for (OnChangeListener listener : mListeners) {
			listener.onChange();
		}
	}
}
