package com.ysaito.runningtrainer;

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
	/** 
	 * Read settings from the per-application sharedPreferences.
	 * @param context The application or activity context
	 */
	public static Settings getSettings(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		Settings settings = new Settings();
		
		settings.unit = METRIC;
		final String unit = prefs.getString("unit", "US");
		if (unit.equals("US")) settings.unit = US;
		
		settings.viewTypes = new String[6];
		for (int i = 0; i < settings.viewTypes.length; ++i) {
			settings.viewTypes[i] = prefs.getString("display" + i, "none");
		}

		settings.autoLapDistanceInterval = Double.parseDouble(prefs.getString("autolap_distance_interval", "0"));
		settings.speakTimeInterval = Double.parseDouble(prefs.getString("speak_time_interval", "0"));
		settings.speakDistanceInterval = Double.parseDouble(prefs.getString("speak_distance_interval", "0"));
		settings.speakTotalDistance = prefs.getBoolean("speak_total_distance", false);
		settings.speakTotalDuration = prefs.getBoolean("speak_total_duration", false);
		settings.speakAveragePace = prefs.getBoolean("speak_average_pace", false);
		settings.speakCurrentPace = prefs.getBoolean("speak_current_pace", false);
		settings.speakLapDistance = prefs.getBoolean("speak_lap_distance", false);
		settings.speakLapDuration = prefs.getBoolean("speak_lap_duration", false);
		settings.speakLapPace = prefs.getBoolean("speak_lap_pace", false);
		settings.speakAutoLapDistance = prefs.getBoolean("speak_auto_lap_distance", false);
		settings.speakAutoLapDuration = prefs.getBoolean("speak_auto_lap_duration", false);
		settings.speakAutoLapPace = prefs.getBoolean("speak_auto_lap_pace", false);
		// TODO: fill the locale
		return settings;
	}

	// Unit of measurement
	public static final int METRIC = 0;
	public static final int US = 1;
	
	public int unit = METRIC;
	public Locale locale = Locale.US;
	public String viewTypes[];
	public double autoLapDistanceInterval; // in meters. <= 0 if disabled
	public double speakDistanceInterval;  // in meters. <=0 if disabled
	public double speakTimeInterval;  // in seconds. <=0 if disabled
	public boolean speakTotalDistance;
	public boolean speakTotalDuration;	
	public boolean speakAveragePace;		
	public boolean speakCurrentPace;
	public boolean speakLapDistance;
	public boolean speakLapDuration;
	public boolean speakLapPace;	
	public boolean speakAutoLapDistance;
	public boolean speakAutoLapDuration;
	public boolean speakAutoLapPace;	
}
