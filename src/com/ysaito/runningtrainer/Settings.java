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
		
		// TODO: fill the locale
		return settings;
	}

	// Unit of measurement
	public static final int METRIC = 0;
	public static final int US = 1;
	
	public int unit = METRIC;
	public Locale locale = Locale.US;
	public String viewTypes[];
}
