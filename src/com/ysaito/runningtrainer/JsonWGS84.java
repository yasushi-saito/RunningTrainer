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
	
	public JsonWGS84() { }
	public JsonWGS84(JsonWGS84 other) {
		timestamp = other.timestamp;
		latitude = other.latitude;
		longitude = other.longitude;
		altitude = other.altitude;
		type = other.type;
	}
	
	enum PathMode { START, MIDDLE, END }; 
	static public JsonWGS84 fromPoint(Util.Point point,  double startTime, PathMode mode) {
		JsonWGS84 wgs = new JsonWGS84();
		wgs.latitude = point.getLatitude();
		wgs.longitude = point.getLongitude();
    	wgs.altitude = point.getAltitude();
    	wgs.timestamp = point.getAbsTime() - startTime;
    	if (mode == PathMode.START) {
    		wgs.type = "start";
    	} else if (mode == PathMode.END) {
    		wgs.type = "end";
    	} else if (point.getType() == Util.PauseType.PAUSE_STARTED) {
    		wgs.type = "pause";
    	} else if (point.getType() == Util.PauseType.PAUSE_ENDED) {
    		wgs.type = "resume";
    	} else {
    		wgs.type = "gps";
    	}
    	return wgs;
	}
}