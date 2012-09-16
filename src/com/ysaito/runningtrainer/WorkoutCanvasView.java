package com.ysaito.runningtrainer;
import java.util.ArrayList;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class WorkoutCanvasView extends View implements View.OnTouchListener {
  static public final String TAG = "WorkoutCanvasView";

  // Below are internal versions of the classes defined in Workout.java
  // They contain all the fields in Workout.java, and a few internal things, 
  // such as the display screen locations.
  private interface Entry {
	  public ArrayList<Entry> getChildren();
	  public void draw(Canvas canvas, float x, float y);
	  public float getHeight();
	  public RectF getLastBoundingBox();
  }
	
  private class Interval implements Entry {
	  // At most one of duration or distance is positive.
	  // If duration >= 0, the interval ends at the specified time
	  // If distance >= 0, the interval ends at the specified distance
	  // Else, the interval ends once the user presses the "Lap" button.
	  public double mDuration = -1.0; 
	  public double mDistance = -1.0;
	  
	  // The pace range lowTargetPace is the faster end of the range.
	  public double mLowTargetPace = 0.0;
	  public double mHighTargetPace = 0.0;
	  
	  // The screen rectangle where this object was last drawn
	  private RectF mLastBoundingBox = new RectF(-1.0f, -1.0f, -1.0f, -1.0f);

	  public RectF getLastBoundingBox() { return mLastBoundingBox; }

	  private final float mScreenDensity; // For converting sp -> pixels
	  
	  public Interval() {
		  mScreenDensity = getContext().getResources().getDisplayMetrics().scaledDensity;
	  }

	  public ArrayList<Entry> getChildren() { return null; }
	  public float getHeight() {
		  return 48 * mScreenDensity;
	  }
	  
	  public void draw(Canvas canvas, float x, float y) {
		  paint.setTextSize(24 * mScreenDensity);
		  paint.setColor(0xffc0ffc0);
		  final int screenWidth = getWidth();
		  canvas.drawRect(x, y, screenWidth - 5 * mScreenDensity, y + 36 * mScreenDensity, paint);
		  
		  final StringBuilder b = mTmpStringBuilder;
		  
		  b.setLength(0);
		  if (mDistance >= 0) {
			  b.append(Util.distanceToString(mDistance, mSettings));
			  b.append(" ");
			  b.append(Util.distanceUnitString(mSettings));
		  } else if (mDuration >= 0) {
			  b.append(Util.durationToString(mDuration));
		  } else {
			  b.append("Until Lap");
		  }
		  b.append(" @ ");
		  if (mLowTargetPace <= 0 && mHighTargetPace <= 0) {
			  b.append("No target");
		  } else {
			  if (mLowTargetPace > 0) {
				  b.append(Util.paceToString(mLowTargetPace, mSettings));
			  } else {
				  b.append("-");
			  }
			  b.append(" to ");
			  if (mHighTargetPace > 0) {
				  b.append(Util.paceToString(mHighTargetPace, mSettings));
			  } else {
				  b.append("-");
			  }
		  }
		  paint.setColor(0xff000000);
		  canvas.drawText(b.toString(), x + 2 * mScreenDensity, y + 25 * mScreenDensity, paint);
		  
		  // Remember the rectangle for future calls to isSelected() 
		  mLastBoundingBox.left = x;
		  mLastBoundingBox.right = screenWidth - x;
		  mLastBoundingBox.top = y;
		  mLastBoundingBox.bottom = y + 48 * mScreenDensity;
	  }
  }
  
  private class Repeats implements Entry {
	  public int mRepeats = 0;
	  public final ArrayList<Entry> mEntries = new ArrayList<Entry>();
	  
	  private RectF mLastBoundingBox = new RectF(-1.0f, -1.0f, -1.0f, -1.0f);

	  private final float mScreenDensity; // For converting sp -> pixels
	  
	  public Repeats() {
		  mScreenDensity = getContext().getResources().getDisplayMetrics().scaledDensity;
	  }

	  public ArrayList<Entry> getChildren() { return mEntries; }
	  
	  public RectF getLastBoundingBox() { return mLastBoundingBox; }
	  
	  public float getHeight() {
		  float height = 48 * mScreenDensity;
		  for (Entry entry : mEntries) height += entry.getHeight();
		  return height;
	  }
	  
	  public void draw(Canvas canvas, float x, float y) {
		  final int screenWidth = getWidth();
		  paint.setTextSize(24 * mScreenDensity);
		  
		  paint.setColor(0xffffc0c0);
		  canvas.drawRect(x, y, screenWidth - 5 * mScreenDensity, y + 36 * mScreenDensity, paint);
		  paint.setColor(0xff000000);
		  canvas.drawText(String.format("Repeat %d times", mRepeats), x + 2 * mScreenDensity, y + 25 * mScreenDensity, paint);

		  mLastBoundingBox.left = x;
		  mLastBoundingBox.right = screenWidth - x;
		  mLastBoundingBox.top = y;
		  mLastBoundingBox.bottom = y + 48 * mScreenDensity;
		  
		  y += 48 * mScreenDensity;
		  for (Entry entry : mEntries) {
			  entry.draw(canvas, x + 30 * mScreenDensity, y);
			  y += entry.getHeight();
		  }
	  }	  
  }
	
  public static boolean isSelected(float x, float y, RectF bbox) { 
	  return x >= bbox.left&& x < bbox.right && y >= bbox.top && y < bbox.bottom;
  }
  
  public static boolean canObjectMoveBelow(float x, float y, RectF bbox) {
	  float height = bbox.bottom - bbox.top;
	  return y >= bbox.top + height / 2 && y < bbox.bottom + height / 2;
  }
	  
  private ArrayList<Entry> mEntries = new ArrayList<Entry>();
  
  public WorkoutCanvasView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setOnTouchListener(this);
    
    Interval interval = new Interval();
    interval.mDistance = Util.METERS_PER_MILE;
    interval.mLowTargetPace = -1.0;
    interval.mHighTargetPace = 330 / Util.METERS_PER_MILE;
    mEntries.add(interval);

    interval = new Interval();
    interval.mDuration = 300;
    interval.mLowTargetPace = 400/ Util.METERS_PER_MILE;
    interval.mHighTargetPace = 430 / Util.METERS_PER_MILE;
    mEntries.add(interval);

    Repeats r = new Repeats();
    r.mRepeats = 10;
    
    interval = new Interval();
    interval.mDuration = 300;
    interval.mLowTargetPace = 400/ Util.METERS_PER_MILE;
    interval.mHighTargetPace = 430 / Util.METERS_PER_MILE;
    r.mEntries.add(interval);

    interval = new Interval();
    interval.mDuration = 250;
    interval.mLowTargetPace = 400/ Util.METERS_PER_MILE;
    interval.mHighTargetPace = 430 / Util.METERS_PER_MILE;
    r.mEntries.add(interval);
    
    mEntries.add(r);
    
    interval = new Interval();
    interval.mLowTargetPace = 400/ Util.METERS_PER_MILE;
    interval.mHighTargetPace = 430 / Util.METERS_PER_MILE;
    mEntries.add(interval);
  }

  //
  // onTouch processing
  //
  private Entry mMovingEntry;
  private float mMoveStartX, mMoveStartY;
  private float mMoveCurrentX, mMoveCurrentY;
  
  public boolean onTouch(View v, MotionEvent event) {
	  final int action = event.getAction();
	  if (action == MotionEvent.ACTION_DOWN) {
		  mMovingEntry = findEntryAtPosition(event.getX(), event.getY());
		  if (mMovingEntry != null) {
			  RectF rect = mMovingEntry.getLastBoundingBox();
			  mMoveStartX = event.getX();
			  mMoveStartY = event.getY();
		  }
		  return true;
	  }
	  if (action == MotionEvent.ACTION_MOVE) {
		  mMoveCurrentX = event.getX();
		  mMoveCurrentY = event.getY();
		  invalidate();
		  return true;
	  }
	  if (action == MotionEvent.ACTION_UP) {
		  mMovingEntry = null;
		  invalidate();
		  return true;
	  }
	  return true;
  }

  static private Entry findEntryAtPositionRec(Entry e, float x, float y) {
	  if (isSelected(x, y, e.getLastBoundingBox())) return e;
	  ArrayList<Entry> children = e.getChildren();
	  if (children != null) {
		  for (Entry child : children) {
			  Entry e2 = findEntryAtPositionRec(child, x, y);
			  if (e2 != null) return e2;
		  }
	  }
	  return null;
  }
  
  private Entry findEntryAtPosition(float x, float y) {
	  for (Entry entry : mEntries) {
		  Entry e = findEntryAtPositionRec(entry, x, y);
		  if (e != null) return e;
	  }
	  return null;
  }
  
  //
  // Screen drawing
  //
  private final Paint paint = new Paint();
  private final StringBuilder mTmpStringBuilder = new StringBuilder();
  private Settings mSettings = null;
  
  @Override public void onDraw(Canvas canvas) {
	  mSettings = Settings.getSettings(getContext());
	  paint.setAntiAlias(true);

	  float y = 0;
	  final float density = getContext().getResources().getDisplayMetrics().scaledDensity;
	  float x = 20 * density;
	  
	  for (Entry entry : mEntries) {
		  if (entry == mMovingEntry) {
			  float deltaX = mMoveStartX - mMoveCurrentX;
			  float deltaY = mMoveStartY - mMoveCurrentY;			  
			  entry.draw(canvas, x - deltaX, y - deltaY);
		  } else {
			  entry.draw(canvas, x, y);
		  }
		  y += entry.getHeight();
	  }
  }
  
}
