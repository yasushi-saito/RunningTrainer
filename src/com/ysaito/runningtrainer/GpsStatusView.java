package com.ysaito.runningtrainer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

public class GpsStatusView extends View {
	@SuppressWarnings("unused")
	static private final String TAG = "GpsStatusView";

	//
	// Screen drawing
	//
	private final Paint mPaint = new Paint();
	
	// Remove the GPS view (set, e.g., in workout editor mode) 
	public static final double HIDE_GPS_VIEW = 999999.0;
	
	// GPS is disabled by the user
	public static final double GPS_DISABLED = 99999.0;
	
	// GPS is enabled, but fix has not been obtained 
	public static final double NO_GPS_STATUS = 9999.0;
	
	private double mAccuracyMeters = NO_GPS_STATUS;
	
	public GpsStatusView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public void setAccuracy(double meters) {
		mAccuracyMeters = meters;
		if (mAccuracyMeters >= HIDE_GPS_VIEW) {
			setVisibility(View.GONE);
		} else {
			setVisibility(View.VISIBLE);
			invalidate();
		}
	}
	
	private final float SCREEN_DENSITY = getContext().getResources().getDisplayMetrics().scaledDensity;
	private final float TEXT_SIZE = 10 * SCREEN_DENSITY;
	
	private Bitmap BITMAP_DISABLED = BitmapFactory.decodeResource(getResources(), android.R.drawable.ic_menu_close_clear_cancel);
	
	@Override public void onDraw(Canvas canvas) {
		final int width = this.getWidth();
		final int height = this.getHeight();

		mPaint.setTextSize(8 * SCREEN_DENSITY);
		mPaint.setColor(0xffffffff);
		mPaint.setStyle(Paint.Style.FILL);
		canvas.drawText("GPS", 0, 10 * SCREEN_DENSITY, mPaint);
		
		if (mAccuracyMeters >= GPS_DISABLED) {
			mPaint.setColor(0xffff0000);
			mPaint.setStyle(Paint.Style.FILL);
			canvas.drawBitmap(BITMAP_DISABLED, 0, 0, mPaint);
			return;
		}
		
		float gap = width / 25;
		if (mAccuracyMeters >= NO_GPS_STATUS) {
			mPaint.setColor(0xffff0000);
			mPaint.setStyle(Paint.Style.FILL);
			canvas.drawRect(gap, height, width / 5, height - height / 5, mPaint);
			return;
		}
		mPaint.setColor(0xff00ff00);
		mPaint.setStyle(Paint.Style.FILL);
		canvas.drawRect(0, height, width * 1 / 5, height - height / 5, mPaint);
		
		if (mAccuracyMeters <= 30.0) {
			canvas.drawRect(width * 1 / 5 + gap, height, width * 2 / 5 , height - height * 2 / 5, mPaint);
		}
		if (mAccuracyMeters <= 20.0) {
			canvas.drawRect(width * 2 / 5 + gap, height, width * 3 / 5 , height - height * 3 / 5, mPaint);
		}
		if (mAccuracyMeters <= 12.0) {
			canvas.drawRect(width * 3 / 5 + gap, height, width * 4 / 5 , height - height * 4 / 5, mPaint);
		}
		if (mAccuracyMeters <= 6.0) {
			canvas.drawRect(width * 4 / 5 + gap, height, width * 5 / 5 , height - height * 5 / 5, mPaint);
		}
		mPaint.setTextSize(TEXT_SIZE);
		mPaint.setTypeface(Typeface.DEFAULT_BOLD);
		mPaint.setStyle(Paint.Style.FILL);
		String text = String.format("%dm", (int)mAccuracyMeters);
		float textWidth = getTextWidth(text, mPaint);
		float textX = 10;
		float textY = height;
		mPaint.setColor(0xa0000000);
		canvas.drawRect(textX, textY, textX + textWidth, textY - TEXT_SIZE, mPaint);

		mPaint.setColor(0xffffffff);
		canvas.drawText(text, textX, textY, mPaint);
		
	}
	
	private static float getTextWidth(String text, Paint paint) {
		float[] widths = new float[text.length()];
		paint.getTextWidths(text, widths);
		float total = 0.0f;
		for (float w : widths) total += w;
		return total;
	}
}
