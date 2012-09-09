package com.ysaito.runningtrainer;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class RecordSummary {
	// The file basename, under /sdcard/Data/Android/com.ysaito.runningtrainer/files/
	public String basename;
	
	// Time recording started. # of millisecs since 1970/1/1
	public long startTime;
	
	// Total distance in meters
	public double totalDistance;
	
	// Duration in seconds 
	public double duration;
	
	final public String toString(Settings settings) {
		GregorianCalendar tmpCalendar = new GregorianCalendar();
		StringBuilder b = new StringBuilder();
		tmpCalendar.setTimeInMillis(startTime);
		
		// TODO: change the date format depending on settings.locale
		b.append(String.format("%04d/%02d/%02d %02d:%02d [%s]",
				tmpCalendar.get(Calendar.YEAR),
				tmpCalendar.get(Calendar.MONTH) - Calendar.JANUARY + 1,
				tmpCalendar.get(Calendar.DAY_OF_MONTH),
				tmpCalendar.get(Calendar.HOUR),
				tmpCalendar.get(Calendar.MINUTE),
				Util.durationToString(duration)));
		b.append(Util.distanceToString(totalDistance, settings));
		return b.toString();
	}
}
