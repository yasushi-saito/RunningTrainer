package com.ysaito.runningtrainer;

import java.util.Iterator;

import android.location.Location;

import com.ysaito.runningtrainer.Util.PauseType;
import com.ysaito.runningtrainer.Util.Point;
// import com.ysaito.gpskalmanfilter.GpsKalmanFilter;
import com.ysaito.gpssmoother.GpsSmoother;

public class PathAggregator {
	public static class Result {
		public PauseType pauseType;
		
		/**
		 *  The number of meters moved since the last call to addLocation. >0 only when pauseType == {RUNNING,PAUSE_ENDED}
		 */
		public double deltaDistance;
		
		/**
		 * If pauseType=={RUNNING, PAUSE_ENDED}, the current duration, as the # of wall seconds since 1970/1/1
		 * If pauseType==PAUSE_CONTINUING, the value is 0.
		 * If pauseType==PAUSE_STARTED, this is the time pause started, as the # of wall seconds since 1970/1/1
		 * The pause-start time may be in the past.
		 */
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
		public ChunkedArray<Point> mPath;
		public boolean mPaused;
		public double mPauseEndTime;

		public Data(float[] mTmp, ChunkedArray<Point> mPath,
				boolean mPaused, double mPauseEndTime) {
			this.mTmp = mTmp;
			this.mPath = mPath;
			this.mPaused = mPaused;
			this.mPauseEndTime = mPauseEndTime;
		}
	}
	private Data data = new Data(new float[1], new ChunkedArray<Point>(), false, -1.0);
	
	public static final double PAUSE_MAX_DISTANCE = 4.0;
	public static final double JUMP_DETECTION_MIN_PACE = 1.5 * 60 / 1000.0;

	private final boolean mDetectPauses;
	private final GpsSmoother mFilter;
	public final double mPauseDetectionWindowSeconds;

	public PathAggregator(
			boolean detectPauses,
			double pauseDetectionWindowSeconds) {
		mDetectPauses = detectPauses;
		mFilter = null;
		// mFilter = new GpsSmoother();
		mPauseDetectionWindowSeconds = pauseDetectionWindowSeconds;
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
			Point lastLocation = data.mPath.back();
			Location.distanceBetween(lastLocation.getLatitude(), lastLocation.getLongitude(),
					latitude, longitude, data.mTmp);
			double pace = (absTime - lastLocation.getAbsTime()) / data.mTmp[0];
			if (pace < JUMP_DETECTION_MIN_PACE) {
				// Large jump detected. Ignore the GPS reading.
				r.absTime = absTime;
				r.pauseType = (data.mPaused ? PauseType.RUNNING : PauseType.PAUSE_CONTINUING);
				r.deltaDistance = 0.0;
				return r;
			}
		}

		if (mFilter != null) {
			GpsSmoother.Point smoothed = new GpsSmoother.Point();
			if (data.mPath.size() == 0) {
				mFilter.addPoint(latitude, longitude, 0.0, 0.0, smoothed);
			} else {
				final double lastAbsTime = data.mPath.back().getAbsTime();
				mFilter.addPoint(latitude, longitude, 0.0, absTime - lastAbsTime, smoothed);
			}
			latitude = smoothed.latitude;
			longitude = smoothed.longitude;
		}
		
		if (!data.mPaused) {
			// Currently in running mode. Detect a pause when:
			// (1) at least 10 seconds passed since the last resumption. 
			// (2) every GPS report in the last 10 seconds is within 5 meters of the current location.
			//
			// The condition (1) is needed to pausing too often when GPS is noisy.
			if (mDetectPauses && absTime >= data.mPauseEndTime + mPauseDetectionWindowSeconds) {
				for (Iterator<Point> iter = data.mPath.reverseIterator(); iter.hasNext(); ) {
					final Point location = iter.next();
					Location.distanceBetween(location.getLatitude(), location.getLongitude(),
							latitude, longitude, data.mTmp);
					final double distance = data.mTmp[0];
					if (distance >= PAUSE_MAX_DISTANCE) {
						// The user moved. 
						break;
					}
					if (absTime - location.getAbsTime() >= mPauseDetectionWindowSeconds) {
						data.mPaused = true;
						// Remove all the elements after the @p location, since they are part of the pause
						while (data.mPath.back() != location) data.mPath.removeLast();
						data.mPath.add(new Point(PauseType.PAUSE_STARTED, location.getAbsTime(), 
								location.getLatitude(), location.getLongitude(), location.getAltitude()));
						r.pauseType = Util.PauseType.PAUSE_STARTED;
						r.absTime = location.getAbsTime();
						return r;
					}
				}
			}
		} else  {
			// Currently paused. Detect resumption.
			Point lastLocation = data.mPath.back();
			Location.distanceBetween(lastLocation.getLatitude(), lastLocation.getLongitude(),
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
			Point lastLocation = data.mPath.back();
			Location.distanceBetween(lastLocation.getLatitude(), lastLocation.getLongitude(),
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
	public ChunkedArray<Point> getPath() { 
		return data.mPath; 
	}
}