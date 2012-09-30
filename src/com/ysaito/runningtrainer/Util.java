package com.ysaito.runningtrainer;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;

public class Util {
	static final String TAG = "Util";
	static final double METERS_PER_MILE = 1609.34;
	static final boolean ASSERT_ENABLED = true;
	
	public static interface SingletonInitializer<T> {
		public T createSingleton();
	}
	
	public static class Singleton<T> {
		private static final int UNINITIALIZED = 0;		
		private static final int INITIALIZER_RUNNING = 1;
		private static final int INITIALIZED = 2;

		private T mObject = null;
		private int mState = UNINITIALIZED;
		
		public Singleton() { }
		
		/**
		 * Get the singleton object. If the object hasn't been initialized yet, run the @p initializer.
		 */
		synchronized T get(SingletonInitializer<T> initializer) {
			while (mState == INITIALIZER_RUNNING) {
				try {
					wait();
				} catch (InterruptedException e) {
					;
				}
			}
			if (mState == UNINITIALIZED) {
				mState = INITIALIZER_RUNNING;
				mObject = initializer.createSingleton();
				mState = INITIALIZED;
			}
			return mObject;
		}
	}
	
	/**
	 * Crash the program after displaying a message
	 * @param context If not null, used to show a toast just before crashing. 
	 */
	static void crash(Context context, String message) {
		if (context != null) {
			error(context, message);
		}
		// Force a crash
		String xx = null;
		xx = xx + "";
	}

	static void info(Context context, String message) {
		
	}
	
	static void error(Context context, String message) {
		Toast.makeText(context, message, Toast.LENGTH_LONG).show();
		Log.d(TAG, "Error: " + message);
	}

	static public String paceToSpeechText(double secondsPerMeter) {
		return durationToSpeechText(secondsPerMeter * (Settings.unit == Settings.US ? METERS_PER_MILE : 1000.0));
	}
	
	static public String durationToSpeechText(double totalSecondsD) {
		long totalSeconds = (long)totalSecondsD;
		final long hours = totalSeconds / 3600;
		final long minutes = (totalSeconds - hours * 3600) / 60;
		final long seconds = totalSeconds % 60;
		StringBuilder b = new StringBuilder();
		if (hours > 0) {
			b.append(hours);
			b.append(hours > 1 ? " hours" : "hour");
		}
		if (minutes > 0) {
			b.append(" ");
			b.append(minutes);
			b.append(minutes > 1 ? " minutes" : "minute");
		}
		b.append(" ");
		b.append(seconds);
		b.append(seconds > 1 ? " seconds" : "second");
		return b.toString();
	}

	static public String distanceToSpeechText(double meters) {
		String unit; 
		long value100;
		if (Settings.unit == Settings.US) {
			unit = "miles";
			value100 = (long)((meters * 100) / METERS_PER_MILE);
		} else {
			unit = "kilometers";
			value100 = (long)((meters * 100) / 1000.0);
		}
		// Drop the fraction if the value >= 100
		if (value100 >= 100 * 100 || value100 % 100 == 0)
			return String.format("%d %s", value100 / 100, unit);
		// Speak up to one digit fraction if the value >= 10
		if (value100 >= 10 * 100 || value100 % 10 == 0)
			return String.format("%d.%d %s", value100 / 100, (value100 / 10) % 10, unit);
		// Else speak up to two digits fraction
		return String.format("%d.%d %s", value100 / 100, value100 % 100, unit);
	}
	
	static public String durationToString(double secondsD) {
		final long seconds = (long)secondsD;
		if (seconds < 3600) {
			return String.format("%02d:%02d",
					seconds / 60,
					seconds % 60);
		} else {
			final long hours = Math.min(99, seconds / 3600);
			return String.format("%02d:%02d:%02d",
					hours,
					(seconds % 3600) / 60,
					seconds % 60);
		}
	}

	private static final Pattern HOUR_MIN_SEC_PATTERN = Pattern.compile("^(\\d+):(\\d+):(\\d+)$");
	private static final Pattern MIN_SEC_PATTERN = Pattern.compile("^(\\d+):(\\d+)$");

	/**
	 * Parse string of form "MM:SS" or "HH:MM:SS".
	 *
	 * @return the number of seconds. -1 on error.
	 */
	static public double durationFromString(String s) {
		try {
			int hour = 0;
			int min = 0;
			int sec = 0;
			
			Matcher m = HOUR_MIN_SEC_PATTERN.matcher(s);
			if (m.matches()) {
				hour = Integer.parseInt(m.group(1));
				min = Integer.parseInt(m.group(2));
				sec = Integer.parseInt(m.group(3));
			} else {
				m = MIN_SEC_PATTERN.matcher(s);
				if (!m.matches()) return -1.0;
				min = Integer.parseInt(m.group(1));
				sec = Integer.parseInt(m.group(2));
			}
			if (hour < 0) return -1.0;
			if (min < 0 || min >= 60) return -1.0;
			if (sec < 0 || sec >= 60) return -1.0;			
			return hour * 3600 + min * 60 + sec;
		} catch (NumberFormatException e) {
			return -1;
		}
	}
	
	static public String distanceUnitString() {
		if (Settings.unit == Settings.US) {
			return "mile";
		} else {
			return "km";
		}
	}

	static public String distanceToString(double meters) {
		if (Settings.unit == Settings.US) {
			return String.format("%.2f", meters / METERS_PER_MILE);
		} else {
			return String.format("%.2f", meters / 1000.0);
		}
	}
	
	/**
	 * Given a textual distance string, such as "1.0", return the value in meters.
	 * The unit is extracted from @p settings. Return -1.0 on error.
	 */
	static public double distanceFromString(String s) {
		try {
			double multiplier = (Settings.unit == Settings.US ? METERS_PER_MILE : 1000.0);
			return Double.parseDouble(s) * multiplier;
		} catch (NumberFormatException e) {
			return -1.0;
		}
	}
	
	static public String paceUnitString() {
		if (Settings.unit == Settings.US) {
			return "s/mile";
		} else {
			return "s/km";
		}
	}
	
	static public String paceToString(double secondsPerMeter) {
		if (Settings.unit == Settings.US) {
			long secondsPerMile = (long)(secondsPerMeter * METERS_PER_MILE);
			return durationToString(secondsPerMile);
		} else {
			long secondsPerKm = (long)(secondsPerMeter * 1000);
			return durationToString(secondsPerKm);
		}
	}

	/**
	 * Given a pace string, such as "7:00", return the numeric value as seconds per meter.
	 * Returns a regative value on parse error.
	 */
	static public double paceFromString(String s) {
		final double d = durationFromString(s);
		if (d < 0.0) return d;
		return d / (Settings.unit == Settings.US ? METERS_PER_MILE : 1000.0);
	}
	
	static public void RescaleMapView(MapView mapView, ArrayList<GeoPoint> points) {
		if (points.size() == 0) return;
		
		int minLat = Integer.MAX_VALUE;
		int maxLat = Integer.MIN_VALUE;
		int minLong = Integer.MAX_VALUE;
		int maxLong = Integer.MIN_VALUE;
		for (GeoPoint point : points) {
			final int lat = point.getLatitudeE6();
			final int longitude = point.getLongitudeE6();
			minLat = Math.min(minLat, lat);
			maxLat = Math.max(maxLat, lat);
			minLong = Math.min(minLong, longitude);
			maxLong = Math.max(maxLong, longitude);
		}
		final MapController controller = mapView.getController();
		controller.zoomToSpan(maxLat - minLat, maxLong - minLong);
		controller.animateTo(new GeoPoint((minLat + maxLat) / 2, (minLong + maxLong) / 2));
	}
	
}
