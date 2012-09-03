package com.ysaito.runningtrainer;

/**
 * Record of a run log. Corresponds to "newly completed activites" json described in
 * http://developer.runkeeper.com/healthgraph/fitness-activities.
 *
 * This object is converted to a JSON string using GSON.
 *
 */
public class Record {
	public String type; // "Running", "Cycling", etc.
	public String start_time;      // "Sat, 1 Jan 2011 00:00:00"
	public double total_distance;  // total distance, in meters
	public double duration;        // duration, in seconds
	public String notes;
	WGS84[] path;
	Boolean post_to_facebook;
	Boolean post_to_twitter;
	Boolean detect_pauses;
	
	static public class WGS84 {
		public double timestamp;  // The number of seconds since the start of the activity
		public double latitude;  // The latitude, in degrees (values increase northward and decrease southward)
		public double longitude; // The longitude, in degrees (values increase eastward and decrease westward)
		public double altitude;  //	The altitude of the point, in meters
		public String type; // One of the following values: start, end, gps, pause, resume, manual
	};
}