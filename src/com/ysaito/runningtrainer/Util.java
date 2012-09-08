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
}
