package com.ysaito.gpssmoother;

public class GpsSmoother {
	public GpsSmoother() {
		mLatitudes = new double[NUM_POINTS];
		mLongitudes = new double[NUM_POINTS];
		for (int i = 0; i < NUM_POINTS; ++i) {
			mLatitudes[i] = INVALID_VALUE;
			mLongitudes[i] = INVALID_VALUE;
		}
	}
	
	public static class Point {
		public double latitude;
		public double longitude;
	};
	
	public void addPoint(double latitude, double longitude, double accuracy, double seconds, Point smoothedPoint) {
		if (mLongitudes[NUM_POINTS - 2] == INVALID_VALUE) {
			// Not enough samples. Just append the new point
			for (int i = 0; i < NUM_POINTS; ++i) {
				if (mLongitudes[i] == INVALID_VALUE) {
					mLongitudes[i] = longitude;
					mLatitudes[i] = latitude;
					smoothedPoint.latitude = latitude;
					smoothedPoint.longitude = longitude;
					break;
				}
			}
			return;
		} 
		if (mLongitudes[NUM_POINTS - 1] != INVALID_VALUE) {
			// Drop the oldest sample 
			for (int i = 1; i < NUM_POINTS; ++i) {
				mLongitudes[i - 1] = mLongitudes[i];
				mLatitudes[i - 1] = mLatitudes[i];				
			}
		}
		mLatitudes[NUM_POINTS - 1] = latitude;
		mLongitudes[NUM_POINTS - 1] = longitude;		
		
		smoothedPoint.latitude = DoLinearRegression(mLatitudes); 
		smoothedPoint.longitude = DoLinearRegression(mLongitudes);
		mLatitudes[NUM_POINTS - 1] = smoothedPoint.latitude;
		mLongitudes[NUM_POINTS - 1] = smoothedPoint.longitude;		
	}

	private double DoLinearRegression(double[] mLatitudes) {
		// here, X = [0, NUM_POINTS)
		//       Y = mLatitudes
		// and we compute a linear regression line, y=ax+b that best matches (X, Y).
		double xySum = 0.0;
		double xxSum = 0.0;
		double xSum = 0.0;
		double ySum = 0.0;
		for (int x = 0; x < NUM_POINTS; ++x) {
			xySum += x * mLatitudes[x];
			xSum += x;
			ySum += mLatitudes[x];
			xxSum += x * x;
		}
		double a = (NUM_POINTS * xySum - xSum * ySum) / (NUM_POINTS * xxSum - xSum * xSum);
		double b = (ySum - a * xSum) / NUM_POINTS; 
		return a * (NUM_POINTS - 1) + b;
	}
	
	private static final int NUM_POINTS = 3;
	private static final double INVALID_VALUE = -99999999.0;
	private double[] mLatitudes;
	private double[] mLongitudes;
}
