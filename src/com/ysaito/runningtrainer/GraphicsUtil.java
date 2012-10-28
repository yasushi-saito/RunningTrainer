package com.ysaito.runningtrainer;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.Projection;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;

public class GraphicsUtil {
    private static Bitmap BITMAP_CURRENT_LOCATION;
    private static Bitmap BITMAP_GO;
    private static Bitmap BITMAP_STOP;
	private static float SCREEN_DENSITY;
	private static float ICON_HEIGHT;
	
	static private Bitmap scaleBitmap(Bitmap original) {
		float scale = ICON_HEIGHT / original.getHeight();
		Bitmap newBitmap = Bitmap.createScaledBitmap(original, (int)(original.getWidth() * scale), (int)(original.getHeight() * scale), true);
		if (newBitmap == null) newBitmap = original;  // size didn't change
		return newBitmap;
	}
	
	static public final void maybeInitialize(Activity activity) {
		if (BITMAP_CURRENT_LOCATION != null) return;
		
		SCREEN_DENSITY = activity.getResources().getDisplayMetrics().scaledDensity;
		ICON_HEIGHT = 32 * SCREEN_DENSITY; // 10sp
		BITMAP_CURRENT_LOCATION = scaleBitmap(BitmapFactory.decodeResource(activity.getResources(), R.drawable.ic_maps_indicator_current_position));
		BITMAP_GO = scaleBitmap(BitmapFactory.decodeResource(activity.getResources(), R.drawable.go));
		BITMAP_STOP = scaleBitmap(BitmapFactory.decodeResource(activity.getResources(), R.drawable.stop));
	}

	static private void internalDraw(float latitude, float longitude, float accuracy, Canvas canvas, Projection projection, Paint paint,
			Bitmap bitmap, float offsetX, float offsetY) {
		android.graphics.Point screenPoint = new android.graphics.Point();
		projection.toPixels(new GeoPoint((int)(latitude * 1e6), (int)(longitude * 1e6)), screenPoint);
		
		// Draw a semitransparent circle to indicate the accuracy.
		float radius = Math.max(10.0f, projection.metersToEquatorPixels(accuracy));
		if (radius > bitmap.getWidth() / 3) {
			// The circle isn't totally obscured by the "current position" bitmap
			paint.setStyle(Paint.Style.FILL);
			paint.setColor(0x200000ff);
			canvas.drawCircle(screenPoint.x, screenPoint.y, radius, paint);
			
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(2);
			paint.setColor(0xff0000ff);
			canvas.drawCircle(screenPoint.x, screenPoint.y, radius, paint);
		}            	            	
		paint.reset();
		canvas.drawBitmap(bitmap, screenPoint.x - offsetX, screenPoint.y - offsetY, paint);
	}
	
	static public void drawStartPoint(float latitude, float longitude, float accuracy, Canvas canvas, Projection projection, Paint paint) {
		internalDraw(latitude, longitude, accuracy, canvas, projection, paint,
				BITMAP_GO,
				BITMAP_GO.getWidth() / 2.0f,
				BITMAP_GO.getHeight());
	}

	static public void drawStopPoint(float latitude, float longitude, float accuracy, Canvas canvas, Projection projection, Paint paint) {
		internalDraw(latitude, longitude, accuracy, canvas, projection, paint,
				BITMAP_STOP,
				BITMAP_STOP.getWidth() / 2.0f,
				BITMAP_STOP.getHeight());
	}

	static public void drawCurrentPosition(float latitude, float longitude, float accuracy, Canvas canvas, Projection projection, Paint paint) {
		internalDraw(latitude, longitude, accuracy, canvas, projection, paint,
				BITMAP_CURRENT_LOCATION,
				BITMAP_CURRENT_LOCATION.getWidth() / 2.0f,
				BITMAP_CURRENT_LOCATION.getHeight() / 2.0f);
	}
}
