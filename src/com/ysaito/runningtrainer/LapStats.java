package com.ysaito.runningtrainer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;

import android.location.Location;
import android.util.Log;

class LapStats {
	private final String TAG = "LapStats";
	
	// The time activity started. Millisecs since 1970/1/1
	// TODO: this should be the first GPS fix after the start, not the time of the start
	private final long mStartTime;
	
	// The index of the last path[] element that was handled by updatePath.
	// We assume that the array path[] passed to updatePath() simply adds new points at the end on 
	// every call
	private int mLastPathSegment = 0;
	
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
		mStartTime = System.currentTimeMillis();
		mRecentEvents = new ArrayDeque<Event>();
	}
	
	/**
	 * @return The time this Stats object was created. Milliseconds since 1970/1/1.
	 */
	public final long getStartTime() { return mStartTime; }
    	
	/**
	 * @return the cumulative distance traveled, in meters
	 */
	public final double getDistance() { return mDistance; }
    	
	/**
	 * @return The number of milliseconds elapsed.
	 */
	public final long getDurationMillis() { return System.currentTimeMillis() - mStartTime; }

	/**
	 * @return The average pace since the beginning of the activity
	 */
	public final double getPace() {
		if (mRecentEvents.size() == 0) return 0.0;
		final long maxTimestamp = mRecentEvents.getLast().absTime;
		final long delta = maxTimestamp - mStartTime;
		if (delta <= 0 || mDistance <= 0.0) return 0.0;
		return delta / 1000.0 / mDistance;
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
		Log.d(TAG, "PACE: now=" + now);
		while (iter.hasNext()) {
			Event event = iter.next();
			if (event.absTime >= now - 15 * 1000) {
				minTimestamp = Math.min(minTimestamp, event.absTime);
				maxTimestamp = Math.max(maxTimestamp, event.absTime);
				totalDistance += event.distance;
				Log.d(TAG, "PACE: log abs=" + event.absTime + " distance=" + event.distance);
			}
		}
		double pace;
		if (totalDistance <= 0.0) {
			pace = 0.0;
		} else {
			pace = (maxTimestamp - minTimestamp) / 1000.0 / totalDistance;
		}
		Log.d(TAG, "PACE: timedelta=" + (maxTimestamp - minTimestamp) + " distance=" + totalDistance + " pace=" + pace);
		return pace;
		/*return String.format("now-max=%d delta=%d n=%d total=%f pace=%f", 
    				now - maxTimestamp, maxTimestamp - minTimestamp, n, totalDistance, 
    				pace);*/
	}
	
	public final void updatePath(ArrayList<HealthGraphClient.JsonWGS84> path) {
		if (path.size() > mLastPathSegment + 1) {
			HealthGraphClient.JsonWGS84 lastPoint = path.get(mLastPathSegment);
			mLastPathSegment++;
			for (;;) {
				HealthGraphClient.JsonWGS84 thisPoint = path.get(mLastPathSegment);
				Location.distanceBetween(lastPoint.latitude, lastPoint.longitude,
						thisPoint.latitude, thisPoint.longitude, mTmp);
				mDistance += mTmp[0];
				
				final long absTime = (long)(mStartTime + thisPoint.timestamp * 1000);
				mRecentEvents.addLast(new Event(mTmp[0], absTime));
				lastPoint = thisPoint;
				if (mLastPathSegment >= path.size() - 1) break;
				++mLastPathSegment;
			}
			
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
}

    
