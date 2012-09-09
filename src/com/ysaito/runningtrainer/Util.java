package com.ysaito.runningtrainer;

public class Util {
	static public String durationToString(double seconds) {
		if (seconds < 3600) {
			return String.format("%02d:%02d",
					(long)seconds / 60,
					(long)seconds % 60);
		} else {
			return String.format("%d:02d:%02d",
				(long)seconds / 3600,
				((long)seconds % 3600) / 60,
				(long)seconds % 60);
		}
	}
	
	static public String distanceToString(double meters, Settings settings) {
		if (settings.unit == Settings.US) {
			if (meters <= 1609.34) {
				return String.format("%.2f mile", meters / 1609.34);
			} else {
				return String.format("%.2f miles", meters / 1609.34);
			}
		} else {
			return String.format("%.2f km", meters / 1000.0);
		}
	}
	
	static public String paceToString(double secondsPerMeter, Settings settings) {
		if (settings.unit == Settings.US) {
			double secondsPerMile = secondsPerMeter * 1609.34; 
			return durationToString(secondsPerMile) + " min/mile";
		} else {
			double secondsPerKm = secondsPerMeter * 1000;
			return durationToString(secondsPerKm) + " min/km";
		}
	}
}
