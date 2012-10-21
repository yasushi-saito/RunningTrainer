package com.ysaito.runningtrainer;

import java.util.ArrayList;

import android.location.Location;

import com.ysaito.runningtrainer.Util.PauseType;
import com.ysaito.runningtrainer.Util.Point;
import com.ysaito.gpskalmanfilter.GpsKalmanFilter;

public class PathAggregator {
	public static class Result {
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

	private static class Data {
		public float[] mTmp;
		public ArrayList<Point> mPath;
		public boolean mPaused;
		public double mPauseEndTime;

		public Data(float[] mTmp, ArrayList<Point> mPath,
				boolean mPaused, double mPauseEndTime) {
			this.mTmp = mTmp;
			this.mPath = mPath;
			this.mPaused = mPaused;
			this.mPauseEndTime = mPauseEndTime;
		}
	}
	private Data data = new Data(new float[1], new ArrayList<Point>(), false, -1.0);
	
	public static final double PAUSE_DETECTION_WINDOW_SECONDS = 10.0; 
	public static final double PAUSE_MAX_DISTANCE = 4.0;
	public static final double JUMP_DETECTION_MIN_PACE = 1.5 * 60 / 1000.0;

	private final boolean mDetectPauses;
	private final GpsKalmanFilter mKalman;
	
	public PathAggregator(
			boolean detectPauses,
			boolean smoothGps) {
		mDetectPauses = detectPauses;
		mKalman = (smoothGps ? new GpsKalmanFilter(10000.0) : null);
	}
			
	/**
	 * @param timestamp Number of seconds elapsed since the start of the activity (not adjusted for pause time)
	 */
	public Result addLocation(
		double absTime, double latitude, double longitude, double altitude) {
		Result r = new Result();
		r.pauseType = PauseType.RUNNING;

		if (data.mPath.size() == 0) {
			data.mPauseEndTime = absTime;
		} else {
			Point lastLocation = data.mPath.get(data.mPath.size() - 1);
			Location.distanceBetween(lastLocation.latitude, lastLocation.longitude,
					latitude, longitude, data.mTmp);
			double pace = (absTime - lastLocation.absTime) / data.mTmp[0];
			if (pace < JUMP_DETECTION_MIN_PACE) {
				// Large jump detected. Ignore the GPS reading.
				r.absTime = absTime;
				r.pauseType = (data.mPaused ? PauseType.RUNNING : PauseType.PAUSE_CONTINUING);
				r.deltaDistance = 0.0;
				return r;
			}
		}

		if (mKalman != null) {
			if (data.mPath.size() == 0) {
				mKalman.updateVelocity(latitude, longitude, 0);
			} else {
				final double lastAbsTime = data.mPath.get(data.mPath.size() - 1).absTime;
				mKalman.updateVelocity(latitude, longitude, absTime - lastAbsTime);
				latitude = mKalman.getLat();
				longitude = mKalman.getLong();
			}
		}
		
		if (!data.mPaused) {
			// Currently in running mode. Detect a pause when:
			// (1) at least 10 seconds passed since the last resumption. 
			// (2) every GPS report in the last 10 seconds is within 5 meters of the current location.
			//
			// The condition (1) is needed to pausing too often when GPS is noisy.
			if (mDetectPauses && absTime >= data.mPauseEndTime + PAUSE_DETECTION_WINDOW_SECONDS) {
				for (int i = data.mPath.size() - 1; i >= 0; --i) {
					final Point location = data.mPath.get(i);
					Location.distanceBetween(location.latitude, location.longitude,
							latitude, longitude, data.mTmp);
					final double distance = data.mTmp[0];
					if (distance >= PAUSE_MAX_DISTANCE) {
						// The user moved. 
						break;
					}
					if (absTime - location.absTime >= PAUSE_DETECTION_WINDOW_SECONDS) {
						data.mPaused = true;
						// Remove all the elements after the @p location, since they are part of the pause
						while (data.mPath.size() > i + 1) data.mPath.remove(data.mPath.size() - 1);
						data.mPath.add(new Point(PauseType.PAUSE_STARTED, location.absTime, location.latitude, location.longitude, location.altitude));
						r.pauseType = Util.PauseType.PAUSE_STARTED;
						r.absTime = location.absTime;
						return r;
					}
				}
			}
		} else  {
			// Currently paused. Detect resumption.
			Point lastLocation = data.mPath.get(data.mPath.size() - 1);
			Location.distanceBetween(lastLocation.latitude, lastLocation.longitude,
					latitude, longitude, data.mTmp);
			final double distance = data.mTmp[0];
			if (distance < PAUSE_MAX_DISTANCE) {
				r.pauseType = Util.PauseType.PAUSE_CONTINUING;
				return r;
			} 
			r.pauseType = Util.PauseType.PAUSE_ENDED;
			data.mPaused = false;
			data.mPauseEndTime = absTime;
			// Fallthrough
		}

		if (data.mPath.size() > 0) {
			Point lastLocation = data.mPath.get(data.mPath.size() - 1);
			Location.distanceBetween(lastLocation.latitude, lastLocation.longitude,
					latitude, longitude,
					data.mTmp);
			r.deltaDistance = data.mTmp[0];
		}
		r.absTime = absTime;
		data.mPath.add(new Point(r.pauseType, absTime, latitude, longitude, altitude));
		return r;
	}
	
	/**
	 * Return the list of locations visited during the activity, in time series order. 
	 * Locations during pauses are removed.
	 */
	public ArrayList<Point> getPath() { 
		return data.mPath; 
	}
}