package com.ysaito.runningtrainer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class GpsStatusView extends View {
	@SuppressWarnings("unused")
	static private final String TAG = "GpsStatusView";

	//
	// Screen drawing
	//
	private final Paint mPaint = new Paint();
	
	public static final double NO_GPS_STATUS = 9999.0;
	private double mAccuracyMeters = NO_GPS_STATUS;
	
	public GpsStatusView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public void setAccuracy(double meters) {
		mAccuracyMeters = meters;
		invalidate();
	}
	
	private final float SCREEN_DENSITY = getContext().getResources().getDisplayMetrics().scaledDensity;
	private final float TEXT_SIZE = 12 * SCREEN_DENSITY;
	
	@Override public void onDraw(Canvas canvas) {
		final int width = this.getWidth();
		final int height = this.getHeight();
		
		if (mAccuracyMeters >= NO_GPS_STATUS) {
			mPaint.setColor(0xffff0000);
			mPaint.setStyle(Paint.Style.FILL);
			canvas.drawRect(0, 0, width / 10, height, mPaint);
			return;
		}
		mPaint.setColor(0xff00ff00);
		mPaint.setStyle(Paint.Style.FILL);
		if (mAccuracyMeters >= 20.0) {
			canvas.drawRect(width / 10, 0, width * 2 / 10, height, mPaint);
		}
		if (mAccuracyMeters >= 10.0) {
			canvas.drawRect(width * 2 / 10, 0, width * 3 / 10, height, mPaint);
		}
		if (mAccuracyMeters >= 5.0) {
			canvas.drawRect(width * 3 / 10, 0, width * 4 / 10, height, mPaint);
		}
		if (mAccuracyMeters >= 3.0) {
			canvas.drawRect(width * 4 / 10, 0, width * 5 / 10, height, mPaint);
		}
		mPaint.setTextSize(TEXT_SIZE);
		mPaint.setColor(0xff405040);
		mPaint.setStyle(Paint.Style.FILL);
		canvas.drawText(String.format("%.2f", mAccuracyMeters), 10, height, mPaint);
	}
}
