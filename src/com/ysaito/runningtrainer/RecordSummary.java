package com.ysaito.runningtrainer;

import java.util.Calendar;
import java.util.GregorianCalendar;

import android.util.Log;

public class RecordSummary {
	// The file basename, under /sdcard/Data/Android/com.ysaito.runningtrainer/files/
	public String basename;
	
	// Time recording started. # of millisecs since 1970/1/1
	public long startTime;
	
	// Total distance in meters
	public double totalDistance;
	
	// Duration in seconds 
	public double duration;
	
	@Override public String toString() {
		GregorianCalendar tmpCalendar = new GregorianCalendar();
	
		StringBuilder b = new StringBuilder();
		tmpCalendar.setTimeInMillis(startTime);
		b.append(String.format("%04d/%02d/%02d ",
				tmpCalendar.get(Calendar.YEAR),
				tmpCalendar.get(Calendar.MONTH) - Calendar.JANUARY + 1,
				tmpCalendar.get(Calendar.DAY_OF_MONTH)));
		return b.toString();
	}
}
