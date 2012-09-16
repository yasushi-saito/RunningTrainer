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
  private interface Element {
	  public ArrayList<Element> getChildren();
	  public void draw(Canvas canvas, float x, float y);
	  public float getHeight();
	  public RectF getLastBoundingBox();
  }

  private class PlaceholderDuringMove implements Element {
	  private float mHeight = 1.0f;
	  private final RectF mLastBoundingBox = new RectF(-1.0f, -1.0f, -1.0f, -1.0f);
	  public void setHeight(float height) { mHeight = height; }
	  public ArrayList<Element> getChildren() { return null; }
	  public void draw(Canvas canvas, float x, float y) { }
	  public float getHeight() { return mHeight; }
	  public RectF getLastBoundingBox() { 
		  return mLastBoundingBox;
	  }
	  @Override public String toString() { 
		  return "placeholder";
	  }
  }

  PlaceholderDuringMove mPlaceholder = new PlaceholderDuringMove();
  public PlaceholderDuringMove getPlaceholder(float height) {
	  removeElement(mPlaceholder);
	  mPlaceholder.setHeight(height);
	  return mPlaceholder;
  }
	
  private class Interval implements Element {
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
	  
	  @Override public String toString() { 
		  return String.format("Interval: duration=%f distance=%f pace=[%f,%f]", mDuration, mDistance, mLowTargetPace, mHighTargetPace);
	  }

	  public ArrayList<Element> getChildren() { return null; }
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
  
  private class Repeats implements Element {
	  public int mRepeats = 0;
	  public final ArrayList<Element> mEntries = new ArrayList<Element>();
	  
	  private RectF mLastBoundingBox = new RectF(-1.0f, -1.0f, -1.0f, -1.0f);

	  private final float mScreenDensity; // For converting sp -> pixels
	  
	  public Repeats() {
		  mScreenDensity = getContext().getResources().getDisplayMetrics().scaledDensity;
	  }

	  @Override public String toString() { 
		  StringBuilder b = new StringBuilder();
		  b.append(String.format("Repeats[%d]{", mRepeats));
		  boolean first = true;
		  for (Element elem : mEntries) {
			  if (!first) b.append(", ");
			  first = false;
			  b.append(elem.toString());
		  }
		  b.append("}");
		  return b.toString();
	  }

	  public ArrayList<Element> getChildren() { return mEntries; }
	  
	  public RectF getLastBoundingBox() { return mLastBoundingBox; }
	  
	  public float getHeight() {
		  float height = 48 * mScreenDensity;
		  for (Element entry : mEntries) height += entry.getHeight();
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
		  for (Element entry : mEntries) {
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
	  
  private ArrayList<Element> mEntries = new ArrayList<Element>();
  
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
  private Element mMovingEntry;
  private float mMoveStartX, mMoveStartY;
  private float mMoveCurrentX, mMoveCurrentY;
  private float mMoveDeltaX, mMoveDeltaY;
  
  public boolean onTouch(View v, MotionEvent event) {
	  final int action = event.getAction();
	  if (action == MotionEvent.ACTION_DOWN) {
		  mMovingEntry = findEntryAtPosition(event.getX(), event.getY());
		  if (mMovingEntry != null) {
			  RectF rect = mMovingEntry.getLastBoundingBox();
			  mMoveStartX = event.getX();
			  mMoveStartY = event.getY();
			  mMoveDeltaX = event.getX() - rect.left;
			  mMoveDeltaY = event.getY() - rect.top;
			  
			  replaceElement(mMovingEntry, getPlaceholder(mMovingEntry.getHeight()));
		  }
		  return true;
	  }
	  if (action == MotionEvent.ACTION_MOVE && mMovingEntry != null) {
		  mMoveCurrentX = event.getX();
		  mMoveCurrentY = event.getY();
		  
		  tryFindMoveDestination(mMovingEntry, event.getX(), event.getY(), null);
		  invalidate();
		  return true;
	  }
	  if (action == MotionEvent.ACTION_UP) {
		  if (mMovingEntry != null) {
			  replaceElement(mPlaceholder, mMovingEntry);
		  }
		  mMovingEntry = null;
		  removeElement(mPlaceholder);
		  invalidate();
		  return true;
	  }
	  return true;
  }

  private boolean moveToNewPosition(Element movingEntry, ArrayList<Element> list) {
	  final int N = list.size();
	  for (int i = 0; i < N; ++i) {
		  if (list.get(i) == mPlaceholder) {
			  list.set(i, movingEntry);
			  removeElement(movingEntry);
			  return true;
		  }
		  ArrayList<Element> children = list.get(i).getChildren();
		  if (children != null) {
			  if (moveToNewPosition(movingEntry, children)) return true;
		  }
	  }
	  return false;
  }
  static private Element findEntryAtPositionRec(Element e, float x, float y) {
	  if (isSelected(x, y, e.getLastBoundingBox())) return e;
	  ArrayList<Element> children = e.getChildren();
	  if (children != null) {
		  for (Element child : children) {
			  Element e2 = findEntryAtPositionRec(child, x, y);
			  if (e2 != null) return e2;
		  }
	  }
	  return null;
  }

  private void tryFindMoveDestination(Element movingEntry, float x, float y, Repeats parent) {
	  ArrayList<Element> children = (parent != null ? parent.getChildren() : mEntries);
	  final int N = children.size();
	  for (int i = 0; i < N; ++i) {
		  final Element entry = children.get(i);
		  if (entry instanceof PlaceholderDuringMove) continue;
		  
		  final RectF rect = entry.getLastBoundingBox();
		  final float height = rect.bottom - rect.top;
		  // If the point is in the upper 1/3 of the rect, create space above the entry.
		  if (y < rect.top + height / 3 && y >= rect.top - 10) {
			  PlaceholderDuringMove placeholder = getPlaceholder(movingEntry.getHeight());
			  removeElement(placeholder);
			  children.add(children.indexOf(entry), placeholder); 
			  Log.d(TAG, "Move above: "+ entry.toString());
			  return;
		  }
		  // If the point is in the bottom 1/3 of the rect, create space below the entry.
		  if (y >= rect.bottom - height / 3 && y < rect.bottom- 10) {
			  PlaceholderDuringMove placeholder = getPlaceholder(movingEntry.getHeight());
			  removeElement(placeholder);
			  children.add(children.indexOf(entry) + 1, placeholder); 
			  Log.d(TAG, "Move below: "+ entry.toString());
			  return;
		  }
	  }
  }

  private boolean removeElement(Element toRemove) {
	  return removeElementRec(mEntries, toRemove);
  }

  private boolean replaceElement(Element toRemove, Element toAdd) {
	  removeElement(toAdd);
	  return replaceElementRec(mEntries, toRemove, toAdd);
  }
  
  private boolean replaceElementRec(ArrayList<Element> list, Element toRemove, Element toAdd) {
	  final int N = list.size();
	  for (int i = 0; i < N; ++i) {
		  Element entry = list.get(i);
		  if (entry == toRemove) {
			  list.set(i, toAdd);
			  return true;
		  }
		  ArrayList<Element> children = entry.getChildren();
		  if (children != null) {
			  if (replaceElementRec(children, toRemove, toAdd)) return true;
		  }
	  }
	  return false;
  }

  private boolean removeElementRec(ArrayList<Element> list, Element toRemove) {
	  final int N = list.size();
	  for (int i = 0; i < N; ++i) {
		  Element entry = list.get(i);
		  if (entry == toRemove) {
			  list.remove(i);
			  return true;
		  }
		  ArrayList<Element> children = entry.getChildren();
		  if (children != null) {
			  if (removeElementRec(children, toRemove)) return true;
		  }
	  }
	  return false;
  }
  
  private Element findEntryAtPosition(float x, float y) {
	  for (Element entry : mEntries) {
		  Element e = findEntryAtPositionRec(entry, x, y);
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
	  
	  for (Element entry : mEntries) {
		  entry.draw(canvas, x, y);
		  y += entry.getHeight();
	  }
	  if (mMovingEntry != null) {
		  mMovingEntry.draw(canvas, mMoveCurrentX - mMoveDeltaX, mMoveCurrentY - mMoveDeltaY);
	  }
  }
  
}
