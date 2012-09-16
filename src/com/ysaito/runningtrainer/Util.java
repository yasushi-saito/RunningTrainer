package com.ysaito.runningtrainer;

import java.util.ArrayList;

import android.content.Context;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;

public class Util {
	static final double METERS_PER_MILE = 1609.34;

	static final boolean ASSERT_ENABLED = true;
	
	/**
	 * Crash the program after displaying a message
	 * @param context Used to show a toast just before crashing
	 */
	static void assertFail(Context context, String message) {
		Toast.makeText(context, message, Toast.LENGTH_LONG).show();
		
		// Force a crash
		String xx = null;
		xx = xx + "";
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

	static public String distanceToSpeechText(double meters, Settings settings) {
		if (settings.unit == Settings.US) {
			return String.format("%.2f miles", meters / METERS_PER_MILE);
		} else {
			return String.format("%.2f kilometers", meters / 1000.0);
		}
	}
	
	static public String durationToString(double secondsD) {
		final long seconds = (long)secondsD;
		if (seconds < 3600) {
			return String.format("%02d:%02d",
					seconds / 60,
					seconds % 60);
		} else {
			return String.format("%d:%02d:%02d",
					seconds / 3600,
					(seconds % 3600) / 60,
					seconds % 60);
		}
	}

	static public String distanceUnitString(Settings settings) {
		if (settings.unit == Settings.US) {
			return "mile";
		} else {
			return "km";
		}
	}

	static public String distanceToString(double meters, Settings settings) {
		if (settings.unit == Settings.US) {
			return String.format("%.2f", meters / METERS_PER_MILE);
		} else {
			return String.format("%.2f", meters / 1000.0);
		}
	}
	
	static public String paceUnitString(Settings settings) {
		if (settings.unit == Settings.US) {
			return "s/mile";
		} else {
			return "s/km";
		}
	}
	
	static public String paceToString(double secondsPerMeter, Settings settings) {
		if (settings.unit == Settings.US) {
			long secondsPerMile = (long)(secondsPerMeter * METERS_PER_MILE);
			return durationToString(secondsPerMile);
		} else {
			long secondsPerKm = (long)(secondsPerMeter * 1000);
			return durationToString(secondsPerKm);
		}
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
