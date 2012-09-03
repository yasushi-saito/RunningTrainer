package com.ysaito.runningtrainer;

public class RecordSummary {
	// The file basename, under /sdcard/Data/Android/com.ysaito.runningtrainer/files/
	public String basename;
	
	// Time recording started. # of millisecs since 1970/1/1
	public long startTime;
	
	// Total distance in meters
	public double totalDistance;
	
	// Duration in seconds 
	public double duration;
}
