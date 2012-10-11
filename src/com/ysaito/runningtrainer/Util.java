package com.ysaito.runningtrainer;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.location.Location;
import android.util.Log;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;

public class Util {
	static final String TAG = "Util";
	static final double METERS_PER_MILE = 1609.34;
	static final boolean ASSERT_ENABLED = true;
	
	public static interface SingletonInitializer<T> {
		public T createSingleton();
	}
	
	/**
	 * Singleton support.
	 * 
	 * The below example would initialize mSingleton with the current time when the object is created.
	 * 
	 * Singleton<Integer> mSingleton = new Singleton<Integer>;
	 * ...
	 * Integer x = mSingleton.get(new SingletonInitializer<Integer>() {
	 *    public Integer createSingleton() { return System.currentTimeMillis(); }
	 * });   
	 */
	public static class Singleton<T> {
		private enum State {
			UNINITIALIZED, INITIALIZER_RUNNING, INITIALIZED
		}
		private T mObject = null;
		private State mState = State.UNINITIALIZED;
		
		public Singleton() { }

		/**
		 * Get the singleton instance.
		 * @param initializer Called on the first call to this function. It should create and return the singleton object.
		 * Subsequent calls to get() will return this object.
		 */
		synchronized T get(SingletonInitializer<T> initializer) {
			while (mState == State.INITIALIZER_RUNNING) {
				try {
					wait();
				} catch (InterruptedException e) {
					;
				}
			}
			if (mState == State.UNINITIALIZED) {
				mState = State.INITIALIZER_RUNNING;
				mObject = initializer.createSingleton();
				mState = State.INITIALIZED;
				notifyAll();
			}
			return mObject;
		}
	}
	
	static public class LapSummary {
		// Cumulative meters traveled, since the start of the activity
		double distance;
	
		// Activity duration, since the start of the activity
		double elapsedSeconds;
		
		// Cumulative meters climbed, since the start of the activity
		double elevationGain;
		
		// Cumulative meters lost, since the start of the activity
		double elevationLoss;

		// The location at the start of the lap.
		JsonWGS84 location;
	}

	public enum PauseType {
		RUNNING, PAUSE_STARTED, PAUSE_CONTINUING, PAUSE_ENDED,
	}
		
	public static class Point {
		double absTime;
		double latitude;
		double longitude;
		double altitude;
	}
	
	public static class PathAggregatorResult {
		public PauseType pauseType;
		
		// The number of meters moved since the last call to addLocation. >0 only when pauseType == {RUNNING,PAUSE_ENDED}
		public double deltaDistance;
		
		// If pauseType=={RUNNING, PAUSE_ENDED}, the current duration, as the # of wall seconds since 1970/1/1
		// If pauseType==PAUSE_CONTINUING, the value is 0.
		//
		// If pauseType==PAUSE_STARTED, this is the time pause started, as the # of wall seconds since 1970/1/1
		// The pause-start time may be in the past.
		public double absTime;
		
		@Override public String toString() {
			String t = "";
			if (pauseType == PauseType.RUNNING) t = "running";
			if (pauseType == PauseType.PAUSE_STARTED) t = "pause_started";
			if (pauseType == PauseType.PAUSE_CONTINUING) t = "pause_continuing";			
			if (pauseType == PauseType.PAUSE_ENDED) t = "pause_ended";						
			return String.format("pause=%s delta=%f time=%f", t, deltaDistance, absTime);
		}
	}
		
	static public class PathAggregator {
		private float[] mTmp = new float[1];
		private final ArrayList<Point> mPath = new ArrayList<Point>();
		
		private boolean mPaused = false;
		private double mPauseEndTime = -1.0;
		
		public static final double PAUSE_DETECTION_WINDOW_SECONDS = 10.0; 
		public static final double PAUSE_MAX_DISTANCE = 5.0;
		public static final double JUMP_DETECTION_MIN_PACE = 1.5 * 60 / 1000.0;
		/**
		 * @param timestamp Number of seconds elapsed since the start of the activity (not adjusted for pause time)
		 */
		public PathAggregatorResult addLocation(
			double absTime, double latitude, double longitude, double altitude) {
			PathAggregatorResult r = new PathAggregatorResult();
			r.pauseType = Util.PauseType.RUNNING;

			if (mPath.size() == 0) {
				mPauseEndTime = absTime;
			} else {
				Point lastLocation = mPath.get(mPath.size() - 1);
				Location.distanceBetween(lastLocation.latitude, lastLocation.longitude,
						latitude, longitude, mTmp);
				double pace = (absTime - lastLocation.absTime) / mTmp[0];
				if (pace < JUMP_DETECTION_MIN_PACE) {
					Log.d(TAG, "PACE=" + pace + " " + JUMP_DETECTION_MIN_PACE);
					// Large jump detected. Ignore the GPS reading.
					r.absTime = absTime;
					r.pauseType = (mPaused ? PauseType.RUNNING : PauseType.PAUSE_CONTINUING);
					r.deltaDistance = 0.0;
					return r;
				}
			}

			if (!mPaused) {
				// Currently in running mode. Detect a pause when:
				// (1) at least 10 seconds passed since the last resumption. 
				// (2) every GPS report in the last 10 seconds is within 5 meters of the current location.
				//
				// The condition (1) is needed to pausing too often when GPS is noisy.
				if (absTime >= mPauseEndTime + PAUSE_DETECTION_WINDOW_SECONDS) {
					for (int i = mPath.size() - 1; i >= 0; --i) {
						final Point location = mPath.get(i);
						Location.distanceBetween(location.latitude, location.longitude,
								latitude, longitude, mTmp);
						final double distance = mTmp[0];
						if (distance >= PAUSE_MAX_DISTANCE) {
							// The user moved. 
							break;
						}
						if (absTime - location.absTime >= PAUSE_DETECTION_WINDOW_SECONDS) {
							mPaused = true;
							// Remove all the elements after the @p location, since they are part of the pause
							while (mPath.size() > i + 1) mPath.remove(mPath.size() - 1);
							r.pauseType = Util.PauseType.PAUSE_STARTED;
							r.absTime = location.absTime;
							return r;
						}
					}
				}
			} else  {
				// Currently paused. Detect resumption.
				Point lastLocation = mPath.get(mPath.size() - 1);
				Location.distanceBetween(lastLocation.latitude, lastLocation.longitude,
						latitude, longitude, mTmp);
				final double distance = mTmp[0];
				if (distance < PAUSE_MAX_DISTANCE) {
					r.pauseType = Util.PauseType.PAUSE_CONTINUING;
					return r;
				} 
				r.pauseType = Util.PauseType.PAUSE_ENDED;
				mPaused = false;
				mPauseEndTime = absTime;
				// Fallthrough
			}
			Point wgs = new Point();
			wgs.latitude = latitude;
			wgs.longitude = longitude;
			wgs.altitude = altitude;
			wgs.absTime = absTime;

			if (mPath.size() > 0) {
				Point lastLocation = mPath.get(mPath.size() - 1);
				Location.distanceBetween(lastLocation.latitude, lastLocation.longitude,
						latitude, longitude,
						mTmp);
				r.deltaDistance = mTmp[0];
			}
			r.absTime = absTime;
			mPath.add(wgs);
			return r;
		}
		
		/**
		 * Return the list of locations visited during the activity, in time series order. 
		 * Locations during pauses are removed.
		 */
		public ArrayList<Point> getPath() { 
			return mPath; 
		}
	}

	/**
	 * Crash the program after displaying a message
	 * @param context If not null, used to show a toast just before crashing. 
	 */
	static void crash(Context context, String message) {
		if (context != null) {
			error(context, message);
		}
		// Force a crash
		String xx = null;
		xx = xx + "";
	}

	static void info(Context context, String message) {
		
	}
	
	static void error(Context context, String message) {
		Toast.makeText(context, message, Toast.LENGTH_LONG).show();
		Log.d(TAG, "Error: " + message);
	}

	static public String paceToSpeechText(double secondsPerMeter) {
		return durationToSpeechText(secondsPerMeter * (Settings.unit == Settings.Unit.US ? METERS_PER_MILE : 1000.0));
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

	static public String distanceToSpeechText(double meters) {
		String unit; 
		long value100;
		if (Settings.unit == Settings.Unit.US) {
			unit = "miles";
			value100 = (long)((meters * 100) / METERS_PER_MILE);
		} else {
			unit = "kilometers";
			value100 = (long)((meters * 100) / 1000.0);
		}
		// Drop the fraction if the value >= 100
		if (value100 >= 100 * 100 || value100 % 100 == 0)
			return String.format("%d %s", value100 / 100, unit);
		// Speak up to one digit fraction if the value >= 10
		if (value100 >= 10 * 100 || value100 % 10 == 0)
			return String.format("%d.%d %s", value100 / 100, (value100 / 10) % 10, unit);
		// Else speak up to two digits fraction
		return String.format("%d.%d %s", value100 / 100, value100 % 100, unit);
	}
	
	static public String durationToString(double secondsD) {
		final long seconds = (long)secondsD;
		if (seconds < 3600) {
			return String.format("%02d:%02d",
					seconds / 60,
					seconds % 60);
		} else {
			final long hours = Math.min(99, seconds / 3600);
			return String.format("%02d:%02d:%02d",
					hours,
					(seconds % 3600) / 60,
					seconds % 60);
		}
	}

	private static final Pattern HOUR_MIN_SEC_PATTERN = Pattern.compile("^(\\d+):(\\d+):(\\d+)$");
	private static final Pattern MIN_SEC_PATTERN = Pattern.compile("^(\\d+):(\\d+)$");

	/**
	 * Parse string of form "MM:SS" or "HH:MM:SS".
	 *
	 * @return the number of seconds. -1 on error.
	 */
	static public double durationFromString(String s) {
		try {
			int hour = 0;
			int min = 0;
			int sec = 0;
			
			Matcher m = HOUR_MIN_SEC_PATTERN.matcher(s);
			if (m.matches()) {
				hour = Integer.parseInt(m.group(1));
				min = Integer.parseInt(m.group(2));
				sec = Integer.parseInt(m.group(3));
			} else {
				m = MIN_SEC_PATTERN.matcher(s);
				if (!m.matches()) return -1.0;
				min = Integer.parseInt(m.group(1));
				sec = Integer.parseInt(m.group(2));
			}
			if (hour < 0) return -1.0;
			if (min < 0 || min >= 60) return -1.0;
			if (sec < 0 || sec >= 60) return -1.0;			
			return hour * 3600 + min * 60 + sec;
		} catch (NumberFormatException e) {
			return -1;
		}
	}
	
	static public String distanceUnitString() {
		if (Settings.unit == Settings.Unit.US) {
			return "mile";
		} else {
			return "km";
		}
	}

	static public String distanceToString(double meters) {
		if (Settings.unit == Settings.Unit.US) {
			return String.format("%.2f", meters / METERS_PER_MILE);
		} else {
			return String.format("%.2f", meters / 1000.0);
		}
	}
	
	/**
	 * Given a textual distance string, such as "1.0", return the value in meters.
	 * The unit is extracted from @p settings. Return -1.0 on error.
	 */
	static public double distanceFromString(String s) {
		try {
			double multiplier = (Settings.unit == Settings.Unit.US ? METERS_PER_MILE : 1000.0);
			return Double.parseDouble(s) * multiplier;
		} catch (NumberFormatException e) {
			return -1.0;
		}
	}
	
	static public String paceUnitString() {
		if (Settings.unit == Settings.Unit.US) {
			return "s/mile";
		} else {
			return "s/km";
		}
	}

	static public String dateToString(double utcSeconds) {
		StringBuilder b = new StringBuilder();
		GregorianCalendar tmpCalendar = new GregorianCalendar();
		tmpCalendar.setTimeInMillis((long)(utcSeconds * 1000));
		
		// TODO: change the date format depending on settings.locale
		b.append(String.format("%04d/%02d/%02d-%02d:%02d",
				tmpCalendar.get(Calendar.YEAR),
				tmpCalendar.get(Calendar.MONTH) - Calendar.JANUARY + 1,
				tmpCalendar.get(Calendar.DAY_OF_MONTH),
				tmpCalendar.get(Calendar.HOUR_OF_DAY),
				tmpCalendar.get(Calendar.MINUTE)));
		return b.toString();
	}
	
	static public String paceToString(double secondsPerMeter) {
		long seconds;
		if (Settings.unit == Settings.Unit.US) {
			seconds = (long)(secondsPerMeter * METERS_PER_MILE);
		} else {
			seconds = (long)(secondsPerMeter * 1000);
		}
		if (seconds < 99 * 60 + 60) {
			return String.format("%02d:%02d",
					seconds / 60,
					seconds % 60);
		} else {
			return "99:59";
		}
		
	}

	/**
	 * Given a pace string, such as "7:00", return the numeric value as seconds per meter.
	 * Returns a regative value on parse error.
	 */
	static public double paceFromString(String s) {
		final double d = durationFromString(s);
		if (d < 0.0) return d;
		return d / (Settings.unit == Settings.Unit.US ? METERS_PER_MILE : 1000.0);
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

	static public ArrayList<Util.LapSummary> listLaps(JsonActivity record) {
		final ArrayList<Util.LapSummary> laps = new ArrayList<Util.LapSummary>();
		double autoLapInterval = Settings.autoLapDistanceInterval;
		if (autoLapInterval <= 0.0) {
			autoLapInterval = (Settings.unit == Settings.Unit.US ? METERS_PER_MILE : 1000.0);
		}
		int lastLap = 0;
		double distance = 0.0;       // Cumulative meters traveled
		double elevationGain = 0.0;  // Cumulative elevation gain, in meters
		double elevationLoss = 0.0;  // Cumulative elevation loss, in meters		
		float tmp[] = new float[2];
		for (int i = 1; i < record.path.length; ++i) {
			JsonWGS84 location = record.path[i];
			JsonWGS84 lastLocation = record.path[i - 1];
			Location.distanceBetween(lastLocation.latitude, lastLocation.longitude,
					location.latitude, location.longitude,
					tmp);
			distance += tmp[0];
			
			final double elevationDelta = location.altitude - lastLocation.altitude;
			if (elevationDelta < 0) {
				elevationLoss += -elevationDelta;
			} else {
				elevationGain += elevationDelta;
			}
			final int thisLap = (int)(distance / autoLapInterval); 
			if (lastLap != thisLap || i == record.path.length - 1) {
				Util.LapSummary lap = new Util.LapSummary();
				lap.distance = distance;
				lap.elapsedSeconds = location.timestamp;
				lap.elevationGain = elevationGain;
				lap.elevationLoss = elevationLoss;
				lap.location = location;
				laps.add(lap);
				lastLap = thisLap;
			}
		}
		return laps;
	}
	
}
