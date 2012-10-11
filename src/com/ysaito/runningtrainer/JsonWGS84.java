package com.ysaito.runningtrainer;

/**
 * GPS reading reported to runkeeper
 */
public class JsonWGS84 {
	public double timestamp;  // The number of seconds since the start of the activity
	public double latitude;  // The latitude, in degrees (values increase northward and decrease southward)
	public double longitude; // The longitude, in degrees (values increase eastward and decrease westward)
	public double altitude;  //	The altitude of the point, in meters
	public String type; // One of the following values: start, end, gps, pause, resume, manual
}