package com.ysaito.runningtrainer;

import java.util.ArrayList;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.Projection;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

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
		
		if (accuracy > bitmap.getHeight() / 4) {
			paint.setStyle(Paint.Style.FILL);
			paint.setStrokeWidth(3);
			paint.setColor(0x300000ff);
			
			float radius = Math.max(10.0f, projection.metersToEquatorPixels(accuracy));
			canvas.drawCircle(screenPoint.x, screenPoint.y, radius, paint);

			paint.setStyle(Paint.Style.STROKE);
			paint.setColor(0xff0000ff);
			canvas.drawCircle(screenPoint.x, screenPoint.y, radius, paint);
		}
		paint.reset();
		canvas.drawBitmap(bitmap, screenPoint.x - offsetX, screenPoint.y - offsetY, paint);
	}

	/**
	 * Draw the googlemap "start" marker at <latitude, longitude>.
	 */
	static public void drawStartPoint(float latitude, float longitude, Canvas canvas, Projection projection, Paint paint) {
		internalDraw(latitude, longitude, 0.0f, canvas, projection, paint,
				BITMAP_GO,
				BITMAP_GO.getWidth() / 2.0f,
				BITMAP_GO.getHeight());
	}

	/**
	 * Draw the googlemap "stop" marker at <latitude, longitude>.
	 */
	static final public void drawStopPoint(float latitude, float longitude, Canvas canvas, Projection projection, Paint paint) {
		internalDraw(latitude, longitude, 0.0f, canvas, projection, paint,
				BITMAP_STOP,
				BITMAP_STOP.getWidth() / 2.0f,
				BITMAP_STOP.getHeight());
	}

	/**
	 * Draw the GPS current position marker at <latitude, longitude>, with @p accuracy meters. 
	 */
	static final public void drawCurrentPosition(float latitude, float longitude, float accuracy, Canvas canvas, Projection projection, Paint paint) {
		// Draw a semitransparent circle to indicate the accuracy.
		internalDraw(latitude, longitude, accuracy, canvas, projection, paint,
				BITMAP_CURRENT_LOCATION,
				BITMAP_CURRENT_LOCATION.getWidth() / 2.0f,
				BITMAP_CURRENT_LOCATION.getHeight() / 2.0f);
	}
	
	static final public void drawPath(ArrayList<GeoPoint> points,
			int viewWidth, int viewHeight,
			Canvas canvas, Projection projection, Paint paint) {
		if (points.size() <= 1) return;
		
    	paint.setColor(0xff000080);
    	paint.setStyle(Paint.Style.STROKE);
    	paint.setStrokeWidth(5);

    	Path path = new Path();
    	android.graphics.Point lastPoint = new android.graphics.Point();
    	projection.toPixels(points.get(0), lastPoint);
    		
    	android.graphics.Point lastSegmentEndpoint = null; 
    	
    	for (int i = 1; i < points.size(); i++) {
    		GeoPoint geoPoint = points.get(i);
    		android.graphics.Point point = new android.graphics.Point();
    		projection.toPixels(geoPoint, point);
    		
    		// Draw the line from mPoints[i-1] .. mPoints[i] if either of
    		// the endpoints are in the view.
    		boolean drawLine = false;
    		drawLine |= (
    				lastPoint.x >= 0 && lastPoint.x < viewWidth &&
    				lastPoint.y >= 0 && lastPoint.y < viewHeight);
    		drawLine |= (
    				point.x >= 0 && point.x < viewWidth &&
    				point.y >= 0 && point.y < viewHeight);
    		if (drawLine) {
    			if (lastSegmentEndpoint != lastPoint) {
    				path.moveTo(lastPoint.x, lastPoint.y);
    			}
    			path.lineTo(point.x, point.y);
    			lastSegmentEndpoint = point;
    		}
    		lastPoint = point;
    	}
    	canvas.drawPath(path, paint);
	}
}
