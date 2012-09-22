package com.ysaito.runningtrainer;

import java.util.ArrayDeque;
import java.util.Iterator;

import android.location.Location;
import android.util.Log;

class LapStats {
	private final String TAG = "LapStats";

	// Current state of the activity. RUNNING if the timer is ticking. PAUSED if the user pressed the "Pause" button
	private static final int RUNNING = 1;
	private static final int PAUSED = 2;
	private int mState = RUNNING;
	
	// The time this lap started. Millisecs since 1970/1/1
	private long mLapStartTimeMillis = 0;
	
	// The time the activity started. Millisecs since 1970/1/1
	private long mRecordStartTimeMillis = 0;

	// The last time "Resume" button was pressed. == mStartTime if resume was never pressed. Millisecs since 1970/1/1.
	private long mLastResumeTimeMillis = 0;
	
	private long mCumulativeDurationBeforeLastResume = 0;

	// The last GPS coordinate and timestamp reported.
	private HealthGraphClient.JsonWGS84 mLastPoint = null;
	
	// Cumulative distance of points in path[0..mLastPathSegment].
	private double mDistance = 0;
    	
	// Tmp used to compute distance between two GeoPoints.
	private final float[] mTmp = new float[1];
    	
	/** 
	 * Remember up to last 10 seconds worth of GPS measurements.
	 */
	private class Event {
		public Event(double d, long t) { distance = d; absTime = t; }
		
		// The distance delta from the previous Event in mRecentEvents
		public final double distance;
		
		// # of milliseconds since 1970/1/1
		public final long absTime;
	}
	private final ArrayDeque<Event> mRecentEvents;
	
	LapStats() {
		mLapStartTimeMillis = System.currentTimeMillis();
		mLastResumeTimeMillis = mLapStartTimeMillis;
		mRecentEvents = new ArrayDeque<Event>();
	}

	/**
	 * @return The time this Stats object was created. seconds since 1970/1/1.
	 */
	public final double getStartTimeSeconds() { 
		return mLapStartTimeMillis / 1000.0; 
	}

	/**
	 * @return the cumulative distance traveled, in meters
	 */
	public final double getDistance() { return mDistance; }
    	
	/**
	 * @return The total number of seconds spent in the activity. Pause periods will be accounted for.
	 */
	public final double getDurationSeconds() {
		long d = mCumulativeDurationBeforeLastResume; 
		if (mState == RUNNING && mLastResumeTimeMillis > 0) {
			d += System.currentTimeMillis() - mLastResumeTimeMillis;
		}
		return d / 1000.0;
	}
	
	/**
	 * @return The average pace (seconds / meter) since the beginning of the activity
	 */
	public final double getPace() {
		final double duration = getDurationSeconds();
		final double distance = getDistance();
		if (distance <= 0.0) return 0.0;
		Log.d(TAG, "PACE=" + duration + "/" + distance);
		return duration / distance;
	}
    	
	/**
	 * @return The pace over the last 10 seconds, as seconds / meter
	 */
	public final double getCurrentPace() {
		if (mRecentEvents.size() == 0) return 0.0;
		
		/** Compute the distance the user moved in the last ~15 seconds. */
		Iterator<Event> iter = mRecentEvents.iterator();
		
		final long now = System.currentTimeMillis();
		long minTimestamp = now + 100000;
		long maxTimestamp = 0;
		double totalDistance = 0.0;
		while (iter.hasNext()) {
			Event event = iter.next();
			if (event.absTime >= now - 15 * 1000) {
				minTimestamp = Math.min(minTimestamp, event.absTime);
				maxTimestamp = Math.max(maxTimestamp, event.absTime);
				totalDistance += event.distance;
			}
		}
		double pace;
		if (totalDistance <= 0.0) {
			pace = 0.0;
		} else {
			pace = (maxTimestamp - minTimestamp) / 1000.0 / totalDistance;
		}
		return pace;
	}
	
	public final void onPause() {
		if (mState == RUNNING) {
			final long now = System.currentTimeMillis();
			mCumulativeDurationBeforeLastResume += (now - mLastResumeTimeMillis);
			mState = PAUSED;
		}
	}
	
	public final void onResume() {
		if (mState == PAUSED) {
			final long now = System.currentTimeMillis();
			mLastResumeTimeMillis = now;
			mState = RUNNING;
		}
	}

	public final void onGpsUpdate(HealthGraphClient.JsonWGS84 point) {
		if (mLastPoint == null) {
			mLastPoint = point;
			mRecordStartTimeMillis = System.currentTimeMillis() - (long)(point.timestamp * 1000);
			return;
		}
		
		Location.distanceBetween(mLastPoint.latitude, mLastPoint.longitude,
				point.latitude, point.longitude, mTmp);
		mDistance += mTmp[0];
		
		final long absTime = mRecordStartTimeMillis + (long)(point.timestamp * 1000);
		mRecentEvents.addLast(new Event(mTmp[0], absTime));
		mLastPoint = point;
			
		// Drop events that are more than 30 seconds old. But keep at least one record so that	
		// if the user stops moving, we can still display the current pace.
		final long lastRetained = System.currentTimeMillis() - 30 * 1000;
		while (mRecentEvents.size() > 1) {
			Event e = mRecentEvents.peekFirst();
			if (e.absTime >= lastRetained) break;
			mRecentEvents.removeFirst();
		}
	}
}

    
