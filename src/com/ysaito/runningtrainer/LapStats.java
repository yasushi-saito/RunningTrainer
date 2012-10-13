package com.ysaito.runningtrainer;

import java.util.ArrayDeque;
import java.util.Iterator;

import android.location.Location;
import android.util.Log;

class LapStats {
	@SuppressWarnings("unused")
	private final String TAG = "LapStats";

	// If mPauseCount > 0, we are currently in paused state. This not a enum, so that we can account
	// for user-initiated autopause and gps-initiated autopause separately.
	private int mPauseCount = 0;
	
	// The time this lap started. Millisecs since 1970/1/1
	private final double mLapStartTimeSeconds;
	
	// The time the activity started. Millisecs since 1970/1/1
	private double mRecordStartTimeSeconds = 0.0;

	// The last time "Resume" button was pressed. == mStartTime if resume was never pressed. Millisecs since 1970/1/1.
	private double mLastResumeTime = 0.0;
	
	private double mCumulativeSecondsBeforeLastResume = 0.0;

	// The last GPS coordinate and timestamp reported.
	private double mLastTimestamp = -1.0;
	
	// Cumulative distance of points in path[0..mLastPathSegment].
	private double mDistance = 0;
    	
	/** 
	 * Remember up to last 10 seconds worth of GPS measurements.
	 */
	private class Event {
		public Event(double d, double t) { distance = d; absTime = t; }
		
		// The distance delta from the previous Event in mRecentEvents
		public final double distance;
		
		// # of seconds since 1970/1/1
		public final double absTime;
	}
	private final ArrayDeque<Event> mRecentEvents;
	
	LapStats() {
		mLapStartTimeSeconds = System.currentTimeMillis() / 1000.0;
		mLastResumeTime = mLapStartTimeSeconds;
		mRecentEvents = new ArrayDeque<Event>();
	}

	/**
	 * @return The time this Stats object was created. seconds since 1970/1/1.
	 */
	public final double getStartTimeSeconds() { 
		return mLapStartTimeSeconds;
	}

	/**
	 * @return the cumulative distance traveled, in meters
	 */
	public final double getDistance() { return mDistance; }
    	
	/**
	 * @return The total number of seconds spent in the activity. Pause periods will be accounted for.
	 */
	public final double getDurationSeconds() {
		double secs = mCumulativeSecondsBeforeLastResume; 
		if (mPauseCount <= 0 && mLastResumeTime > 0.0) {
			secs += System.currentTimeMillis() / 1000.0 - mLastResumeTime;
		}
		return secs;
	}
	
	/**
	 * @return The average pace (seconds / meter) since the beginning of the activity
	 */
	public final double getPace() {
		final double duration = getDurationSeconds();
		final double distance = getDistance();
		if (distance <= 0.0) return 0.0;
		return duration / distance;
	}
    	
	/**
	 * @return The pace over the last 10 seconds, as seconds / meter
	 */
	public final double getCurrentPace() {
		if (mRecentEvents.size() == 0) return 0.0;
		
		/** Compute the distance the user moved in the last ~15 seconds. */
		Iterator<Event> iter = mRecentEvents.iterator();
		
		final double now = System.currentTimeMillis() / 1000.0;
		double minTimestamp = now + 100000;
		double maxTimestamp = 0;
		double totalDistance = 0.0;
		while (iter.hasNext()) {
			Event event = iter.next();
			if (event.absTime >= now - 15) {
				minTimestamp = Math.min(minTimestamp, event.absTime);
				maxTimestamp = Math.max(maxTimestamp, event.absTime);
				totalDistance += event.distance;
			}
		}
		double pace;
		if (totalDistance <= 0.0) {
			pace = 0.0;
		} else {
			pace = (maxTimestamp - minTimestamp) / totalDistance;
		}
		return pace;
	}
	
	/**
	 * @param pauseTime The time the pause started. # seconds since 1970/1/1.
	 */
	public final void onPause(double pauseTime) {
		Log.d(TAG, "onpause: " + pauseTime);
		++mPauseCount;
		if (mPauseCount == 1) {  // active -> pause transition
			final double duration = pauseTime - mLastResumeTime;
			if (duration > 0) {
				mCumulativeSecondsBeforeLastResume += duration;
			}
		}
	}
	
	/**
	 * @param pauseTime The time the paused ended. # seconds since 1970/1/1.
	 */
	public final void onResume(double resumeTime) {
		Log.d(TAG, "onresume: " + resumeTime);
		--mPauseCount;
		if (mPauseCount < 0) Util.crash(null, "PauseCount < 0");
		if (mPauseCount == 0) {
			mLastResumeTime = resumeTime;
		}
	}

	public final void onGpsUpdate(double absTime, double deltaDistance) {
		if (mLastTimestamp < 0.0) {
		} else {
			mDistance += deltaDistance;
			mRecentEvents.addLast(new Event(deltaDistance, absTime));

			// Drop events that are more than 30 seconds old. But keep at least one record so that	
			// if the user stops moving, we can still display the current pace.
			final double lastRetained = System.currentTimeMillis() / 1000.0 - 30;
			while (mRecentEvents.size() > 1) {
				Event e = mRecentEvents.peekFirst();
				if (e.absTime >= lastRetained) break;
				mRecentEvents.removeFirst();
			}
		}
		mLastTimestamp = absTime;
	}
}

    
