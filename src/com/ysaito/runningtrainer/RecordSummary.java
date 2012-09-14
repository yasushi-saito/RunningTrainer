package com.ysaito.runningtrainer;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class RecordSummary {
	// The file basename, under /sdcard/Data/Android/com.ysaito.runningtrainer/files/
	// The other fields in this class are extracted from the basename.
	public String basename;
	
	// Time the recording started. # of millisecs since 1970/1/1
	public long startTime;
	
	// Total distance in meters
	public double distance;
	
	// Duration in seconds 
	public double duration;

	// The path under api.runkeeper.com that stores this activity. Typically something like "/fitnessActivities/10".
	// The value is null if the record hasn't been sent to runkeeper.
	public String runkeeperPath;
	
}
