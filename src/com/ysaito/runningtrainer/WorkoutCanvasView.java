package com.ysaito.runningtrainer;
import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;

public class WorkoutCanvasView extends View implements View.OnTouchListener {
	static private final String TAG = "WorkoutCanvasView";

	public static class IntervalDialog extends DialogFragment {
		private View mDistanceBox, mDurationBox;
		private EditText mDistanceEditor;
		private EditText mDurationEditor;		
		private EditText mFastPaceEditor;
		private EditText mSlowPaceEditor;		
		private final View mParentView;
		private final Interval mElem;
		
		public IntervalDialog(View parentView, Interval elem) { 
			mParentView = parentView;
			mElem = elem; 
		}
	    
	    @Override
	    public Dialog onCreateDialog(Bundle savedInstanceState) {
	    	View v = getActivity().getLayoutInflater().inflate(R.layout.interval_dialog, null);

	        mDistanceBox = v.findViewById(R.id.box_distance);
	        mDurationBox = v.findViewById(R.id.box_duration);
	        mDistanceEditor = (EditText)v.findViewById(R.id.edit_distance);
	        mDurationEditor = (EditText)v.findViewById(R.id.edit_duration);
	        
	        RadioButton distanceButton = (RadioButton)v.findViewById(R.id.radio_distance);
	        
	        distanceButton.setOnClickListener(new OnClickListener() {
	            public void onClick(View v) {  onDistanceButtonPress(); }
	        });
	        
	        RadioButton durationButton = (RadioButton)v.findViewById(R.id.radio_duration);
	        durationButton.setOnClickListener(new OnClickListener() {
	            public void onClick(View v) {  onDurationButtonPress(); }
	        });
	        
	        RadioButton lapButton = (RadioButton)v.findViewById(R.id.radio_lap_button);
	        lapButton.setOnClickListener(new OnClickListener() {
	            public void onClick(View v) {  onLapButtonPress(); }
	        });

	        if (mElem.getDistance() > 0) {
	        	distanceButton.setChecked(true);
	        	mDistanceEditor.setText(Util.distanceToString(mElem.getDistance()));
	        	onDistanceButtonPress();
	        } else if (mElem.getDuration() > 0) {
	        	durationButton.setChecked(true);
	        	mDurationEditor.setText(Util.durationToString(mElem.getDuration()));
	        	onDurationButtonPress();
	        } else {
	        	lapButton.setChecked(true);
	        	onLapButtonPress();
	        }

	        mFastPaceEditor = (EditText)v.findViewById(R.id.edit_fast_pace);
	        final double fast = mElem.getFastTargetPace();
	        if (Workout.hasFastTargetPace(fast)) {
	        	mFastPaceEditor.setText(Util.paceToString(fast));
	        } else {
	        	mFastPaceEditor.setText("");
	        }
	        
	        mSlowPaceEditor = (EditText)v.findViewById(R.id.edit_slow_pace);
	        final double slow = mElem.getSlowTargetPace();
	        if (Workout.hasSlowTargetPace(slow)) {
	        	mSlowPaceEditor.setText(Util.paceToString(slow));
	        } else {
	        	mSlowPaceEditor.setText("");
	        }
	        
	    	AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
	                .setTitle("Edit Interval")
	                .setPositiveButton(android.R.string.ok,
	                    new DialogInterface.OnClickListener() {
	                        public void onClick(DialogInterface dialog, int whichButton) {
	                        	String durationStr = (mDurationBox.getVisibility() == View.VISIBLE) ? 
	                        			mDurationEditor.getText().toString() : "";
	                        	String distanceStr = (mDistanceBox.getVisibility() == View.VISIBLE) ? 
	                        			mDistanceEditor.getText().toString() : "";
                       			try {
                       				mElem.update(
	                        			durationStr,
	                        			distanceStr,
	                        			mFastPaceEditor.getText().toString(),
	                        			mSlowPaceEditor.getText().toString());
	                        		mParentView.invalidate();
                       			} catch (Exception e) {
	                        		Util.error(getActivity(), "Failed to update interval: " + e);
	                        	}
	                        }
	                    }
	                )
	                .setNegativeButton(android.R.string.cancel,
	                    new DialogInterface.OnClickListener() {
	                        public void onClick(DialogInterface dialog, int whichButton) {
	                            ;
	                        }
	                    }
	                );
	    	builder.setView(v);
	        return builder.create();
	    }
	    
	    private void onDistanceButtonPress() {
	    	mDistanceBox.setVisibility(View.VISIBLE);
	    	mDurationBox.setVisibility(View.GONE);	
	    }
	    private void onDurationButtonPress() {
	    	mDistanceBox.setVisibility(View.GONE);
	    	mDurationBox.setVisibility(View.VISIBLE);	
	    }
	    private void onLapButtonPress() {
	    	mDistanceBox.setVisibility(View.GONE);
	    	mDurationBox.setVisibility(View.GONE);	
	    }
	}

	public static class RepeatsDialog extends DialogFragment {
		private EditText mNumRepeatsEditor;
		private final View mParentView;
		private final Repeats mElem;
		
		public RepeatsDialog(View parentView, Repeats elem) { 
			mParentView = parentView;
			mElem = elem; 
		}
	    
	    @Override
	    public Dialog onCreateDialog(Bundle savedInstanceState) {
	    	View v = getActivity().getLayoutInflater().inflate(R.layout.repeats_dialog, null);

	        mNumRepeatsEditor = (EditText)v.findViewById(R.id.edit_num_repeats);
	        mNumRepeatsEditor.setText(Integer.toString(mElem.getRepeats()));
	        
	    	AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
	                .setTitle("Edit Repeats")
	                .setPositiveButton(android.R.string.ok,
	                    new DialogInterface.OnClickListener() {
	                        public void onClick(DialogInterface dialog, int whichButton) {
	                        	try {
	                        		mElem.update(mNumRepeatsEditor.getText().toString());
	                        		mParentView.invalidate();
	                        	} catch (Exception e) {
	                        		Util.error(getActivity(), "Failed to update repeats: " + e);
	                        	}
	                        }
	                    }
	                )
	                .setNegativeButton(android.R.string.cancel,
	                    new DialogInterface.OnClickListener() {
	                        public void onClick(DialogInterface dialog, int whichButton) {
	                            ;
	                        }
	                    }
	                );
	    	builder.setView(v);
	        return builder.create();
	    }
	}
	
	// Below are internal versions of the classes defined in Workout.java
	// They contain all the fields in Workout.java, and a few internal things, 
	// such as the display screen locations.
	private interface Element {
		public Repeats getParent();
		public void setParent(Repeats p);
		
		public void draw(Canvas canvas, float x, float y);
		public float getHeight();
		public float getWidth();
		public RectF getLastBoundingBox();
		public Workout toWorkout();
	}

	private final float SCREEN_DENSITY = getContext().getResources().getDisplayMetrics().scaledDensity;
	private final float SCREEN_WIDTH = getContext().getResources().getDisplayMetrics().widthPixels;
	private final float HORIZONTAL_INDENT_PER_LEVEL = 20 * SCREEN_DENSITY;
	private Bitmap BITMAP_REMOVE = BitmapFactory.decodeResource(getResources(), android.R.drawable.ic_delete);
	
	private class PlaceholderDuringMove implements Element {
		private float mWidth = 0.0f;
		private float mHeight = 0.0f;
		
		private final RectF mLastBoundingBox = new RectF(-1.0f, -1.0f, -1.0f, -1.0f);
		private Repeats mParent = null;
		
		public void setDimension(float width, float height) { 
			mWidth = width;
			mHeight = height; 
		}
		
		public Repeats getParent() { return mParent; };
		public void setParent(Repeats p) { mParent = p; }
		
		public void draw(Canvas canvas, float x, float y) { 
			mPaint.setStyle(Paint.Style.STROKE);
			mPaint.setColor(0xffc0ffc0);
			mPaint.setStrokeWidth(2 * SCREEN_DENSITY);
			mPaint.setPathEffect(new DashPathEffect(new float[] {10,20}, 0));			
			canvas.drawRect(x, y, x + getWidth(), y + mHeight, mPaint);
		}
		public float getHeight() { return mHeight; }
		public float getWidth() { return mWidth; }
		public RectF getLastBoundingBox() { 
			return mLastBoundingBox;
		}
		public Workout toWorkout() { 
			// This shouldn't be called in practice. create some dummy entry
			Workout w = new Workout();
			w.type = Workout.TYPE_INTERVAL;
			return w;
		}

		@Override public String toString() { 
			return "placeholder";
		}
	}

	PlaceholderDuringMove mPlaceholder = new PlaceholderDuringMove();
	public PlaceholderDuringMove getPlaceholder(Element elem) {
		removeElement(mRoot, mPlaceholder);
		mPlaceholder.setDimension(elem.getWidth(), elem.getHeight());
		return mPlaceholder;
	}
	
	private class Interval implements Element {
		// At most one of duration or distance is positive.
		// If duration >= 0, the interval ends at the specified time
		// If distance >= 0, the interval ends at the specified distance
		// Else, the interval ends once the user presses the "Lap" button.
		private double mDuration = -1.0; 
		private double mDistance = -1.0;
		
		// The pace range lowTargetPace is the faster end of the range.
		private double mFastTargetPace = 0.0;
		private double mSlowTargetPace = 0.0;
		
		// The screen rectangle where this object was last drawn
		private RectF mLastBoundingBox = new RectF(-1.0f, -1.0f, -1.0f, -1.0f);
		private Repeats mParent = null;

		// Accessors
		public double getDuration() { return mDuration; }
		public double getDistance() { return mDistance; }
		public double getFastTargetPace() { return mFastTargetPace; }
		public double getSlowTargetPace() { return mSlowTargetPace; }		
		public Repeats getParent() { return mParent; };
		public void setParent(Repeats p) { mParent = p; }

		public Workout toWorkout() { 
			Workout w = new Workout();
			w.type = Workout.TYPE_INTERVAL;
			w.distance = mDistance;
			w.duration = mDuration;
			w.fastTargetPace = mFastTargetPace;
			w.slowTargetPace = mSlowTargetPace; 
			return w;
		}
		
		/**
		 * Update the interval params. Arguments are string represention of the new values.
		 * Raises an exception on illegal param values.
		 */
		public void update(String durationStr, String distanceStr, String fastTargetPaceStr, String slowTargetPaceStr) throws Exception {
			double distance = -1.0;
			if (!distanceStr.isEmpty()) {
				distance = Util.distanceFromString(distanceStr);
				if (distance < 0.0) throw new Exception("Failed to parse distance " + distanceStr);
			}
			double duration = -1.0;
			if (!durationStr.isEmpty()) {
				duration = Util.durationFromString(durationStr);
				if (duration < 0.0) throw new Exception("Failed to parse duration " + durationStr);
			}
			double fastTargetPace = Workout.NO_FAST_TARGET_PACE;
			if (!fastTargetPaceStr.isEmpty()) {
				fastTargetPace = Util.paceFromString(fastTargetPaceStr);
				if (fastTargetPace < 0.0) throw new Exception("Failed to parse pace " + fastTargetPaceStr);
			}
			double slowTargetPace = Workout.NO_SLOW_TARGET_PACE;
			if (!slowTargetPaceStr.isEmpty()) {
				slowTargetPace = Util.paceFromString(slowTargetPaceStr);
				if (slowTargetPace < 0.0) throw new Exception("Failed to parse pace " + fastTargetPaceStr);
			}
			if (fastTargetPace > slowTargetPace) {
				throw new Exception(String.format("Empty pace range: [%s(%f), %s(%f)]", fastTargetPaceStr, fastTargetPace, 
						slowTargetPaceStr, slowTargetPace));
			}
			mDistance = distance;
			mDuration = duration;
			mFastTargetPace = fastTargetPace;
			mSlowTargetPace = slowTargetPace;
		}
		
		@Override public String toString() { 
			return String.format("Interval: duration=%f distance=%f pace=[%f,%f]", mDuration, mDistance, mFastTargetPace, mSlowTargetPace);
		}
		
		
		// Element interface implementation
		public RectF getLastBoundingBox() { return mLastBoundingBox; }
		
		public float getHeight() {
			return 48 * SCREEN_DENSITY;
		}
		
		public float getWidth() { 
			return mParent.getWidth() - HORIZONTAL_INDENT_PER_LEVEL;
		}
		
		public void draw(Canvas canvas, float x, float y) {
			mPaint.setTextSize(24 * SCREEN_DENSITY);
			mPaint.setColor(0xffc0ffc0);
			mPaint.setStyle(Paint.Style.FILL);

			// Remember the rectangle for future calls to isSelected() 
			mLastBoundingBox.left = x;
			mLastBoundingBox.right = x + getWidth();
			mLastBoundingBox.top = y;
			mLastBoundingBox.bottom = y + 36 * SCREEN_DENSITY;
			canvas.drawRect(mLastBoundingBox, mPaint);
			
			final StringBuilder b = mTmpStringBuilder;
			b.setLength(0);
			Workout.addIntervalToDisplayStringTo(mDuration, mDistance, mFastTargetPace, mSlowTargetPace, b);
			Log.d(TAG, "TEXT: " + b.toString());
			mPaint.setColor(0xff000000);
			canvas.drawText(b.toString(), x + 2 * SCREEN_DENSITY, y + 25 * SCREEN_DENSITY, mPaint);
			mPaint.setAlpha(128);
			canvas.drawBitmap(BITMAP_REMOVE, x + getWidth() - 30 * SCREEN_DENSITY, y, mPaint);
		}
	}

	private class Repeats implements Element {
		public int mRepeats = 0;
		public final ArrayList<Element> mEntries = new ArrayList<Element>();
		private RectF mLastBoundingBox = new RectF(-1.0f, -1.0f, -1.0f, -1.0f);
		private Repeats mParent = null;
		
		public Repeats(int n) { mRepeats = n; }
		public final int getRepeats() { return mRepeats; }
		public final Repeats getParent() { return mParent; };
		public final void setParent(Repeats p) { mParent = p; }
		
		public final int getNumChildren() { return mEntries.size(); }
		public final Element getChild(int i) { return mEntries.get(i); }
		public final ArrayList<Element> getChildren() { return mEntries; }

		public Workout toWorkout() { 
			Workout w = new Workout();
			w.type = Workout.TYPE_REPEATS;
			w.repeats = mRepeats;
			w.children = new Workout[mEntries.size()];
			for (int i = 0; i < mEntries.size(); ++i) {
				w.children[i] = mEntries.get(i).toWorkout();
			}
			return w;
		}
		
		public float getWidth() { 
			int n = 0;
			Repeats parent = this;
			while (parent != mRoot) {
				n++;
				parent = parent.getParent();
			}
			return SCREEN_WIDTH - HORIZONTAL_INDENT_PER_LEVEL * n;
		}
		
		/**
		 * Add @p elem before @p existing. @p existing must be in the list
		 */
		public final void addBefore(Element existing, Element elem) {
			mEntries.add(mEntries.indexOf(existing), elem); 
		}
		
		/**
		 * Add @p elem after @p existing. @p existing must be in the list
		 */
		public final void addAfter(Element existing, Element elem) {
			mEntries.add(mEntries.indexOf(existing) + 1, elem); 
			elem.setParent(this);
		}

		public final void prepend(Element elem) {
			mEntries.add(0, elem);
			elem.setParent(this);
		}
		
		public final void append(Element elem) {
			mEntries.add(elem);
			elem.setParent(this);
		}

		public final void removeChildAtIndex(int i) {
			mEntries.get(i).setParent(null);
			mEntries.remove(i);
		}

		public void replaceChildAtIndex(int i, Element elem) {
			mEntries.set(i, elem);
			elem.setParent(this);
		}
		
		/**
		 * Update the interval params. Arguments are string represention of the new values.
		 * Throws an exception on invalid params.
		 */
		public void update(String repeatsStr) throws Exception {
			int n = Integer.parseInt(repeatsStr);
			if (n <= 0) {
				throw new Exception(repeatsStr + ": Negative repeats not allowed");
			}
			mRepeats = n;
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

		public RectF getLastBoundingBox() { return mLastBoundingBox; }

		public float getHeight() {
			float height = 48 * SCREEN_DENSITY;
			for (Element entry : mEntries) height += entry.getHeight();
			return height;
		}

		public void draw(Canvas canvas, float x, float y) {
			mPaint.setTextSize(24 * SCREEN_DENSITY);

			mPaint.setColor(0xffffc0c0);
			mPaint.setStyle(Paint.Style.FILL);
			canvas.drawRect(x, y, x + getWidth(), y + 36 * SCREEN_DENSITY, mPaint);
			mPaint.setColor(0xff000000);
			canvas.drawText(String.format("Repeat %d times", mRepeats), x + 2 * SCREEN_DENSITY, y + 25 * SCREEN_DENSITY, mPaint);
			
			mPaint.setAlpha(128);
			canvas.drawBitmap(BITMAP_REMOVE, x + getWidth() - 30 * SCREEN_DENSITY, y, mPaint);
			mLastBoundingBox.left = x;
			mLastBoundingBox.right = x + getWidth();
			mLastBoundingBox.top = y;

			y += 48 * SCREEN_DENSITY;
			for (Element entry : mEntries) {
				entry.draw(canvas, x + HORIZONTAL_INDENT_PER_LEVEL, y);
				y += entry.getHeight();
			}
			mLastBoundingBox.bottom = y;
		}	  
	}

	public static boolean isSelected(float x, float y, RectF bbox) { 
		return x >= bbox.left&& x < bbox.right && y >= bbox.top && y < bbox.bottom;
	}

	public static boolean canObjectMoveBelow(float x, float y, RectF bbox) {
		float height = bbox.bottom - bbox.top;
		return y >= bbox.top + height / 2 && y < bbox.bottom + height / 2;
	}

	private Repeats mRoot = new Repeats(1);

	private Element fromWorkout(Workout workout) {
		if (workout.type == Workout.TYPE_REPEATS) {
			Repeats r = new Repeats(workout.repeats);
			if (workout.children != null) {  // Repeats, not Root
				for (Workout child : workout.children) {
					r.append(fromWorkout(child));
				}
			}
			return r;
		} else {
			if (Util.ASSERT_ENABLED && workout.type != Workout.TYPE_INTERVAL)
				Util.crash(getContext(), "Wrong workout: " + workout.toString());
			Interval i = new Interval();
			i.mDistance = workout.distance;
			i.mDuration = workout.duration;
			i.mFastTargetPace = workout.fastTargetPace;
			i.mSlowTargetPace = workout.slowTargetPace;
			return i;
		}
	}
	
	public WorkoutCanvasView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setOnTouchListener(this);

		Interval interval = new Interval();
		interval.mDistance = Util.METERS_PER_MILE;
		interval.mFastTargetPace = -1.0;
		interval.mSlowTargetPace = 330 / Util.METERS_PER_MILE;
		mRoot.append(interval);

		interval = new Interval();
		interval.mDuration = 300;
		interval.mFastTargetPace = 400/ Util.METERS_PER_MILE;
		interval.mSlowTargetPace = 430 / Util.METERS_PER_MILE;
		mRoot.append(interval);

		Repeats r = new Repeats(95);

		interval = new Interval();
		interval.mDuration = 300;
		interval.mFastTargetPace = 400/ Util.METERS_PER_MILE;
		interval.mSlowTargetPace = 430 / Util.METERS_PER_MILE;
		r.append(interval);

		interval = new Interval();
		interval.mDuration = 250;
		interval.mFastTargetPace = 400/ Util.METERS_PER_MILE;
		interval.mSlowTargetPace = 430 / Util.METERS_PER_MILE;
		r.append(interval);
		mRoot.append(r);

		interval = new Interval();
		interval.mFastTargetPace = 400/ Util.METERS_PER_MILE;
		interval.mSlowTargetPace = 430 / Util.METERS_PER_MILE;
		mRoot.append(interval);
	}

	public void addNewInterval() {
		Interval interval = new Interval();
		interval.mDistance = Util.METERS_PER_MILE;
		interval.mFastTargetPace = 400/ Util.METERS_PER_MILE;
		interval.mSlowTargetPace = 430 / Util.METERS_PER_MILE;
		mRoot.append(interval);
		invalidate();
	}

	public void addNewRepeats() {
		Repeats repeats = new Repeats(5);
		mRoot.append(repeats);
		invalidate();
	}
	
	//
	// onTouch processing
	//
	private Element mSelectedElement;
	
	// The touch location on the DOWN event
	private float mTouchStartX, mTouchStartY;
	
	// The time of the DOWN event. Seconds since 1970/1/1
	private double mTouchStartTime; 
	
	// The current touch location.
	private float mTouchCurrentX, mTouchCurrentY;
	
	private float mMoveDeltaX, mMoveDeltaY;
	
	// Whether the current touch event is considered to be a click or drag.
	private boolean mIsClick = false; 

	public boolean onTouch(View v, MotionEvent event) {
		final int action = event.getAction();
		final double now = System.currentTimeMillis() / 1000.0;
		if (action == MotionEvent.ACTION_DOWN) {
			mSelectedElement = findElementAtPoint(mRoot, event.getX(), event.getY());
			if (mSelectedElement != null) {
				RectF rect = mSelectedElement.getLastBoundingBox();
				mTouchStartX = event.getX();
				mTouchStartY = event.getY();				
				mMoveDeltaX = mTouchStartX - rect.left;
				mMoveDeltaY = mTouchStartY - rect.top;
				mTouchStartTime = now;
				mIsClick = true;  
				replaceElement(mSelectedElement, getPlaceholder(mSelectedElement));
			}
			return true;
		}
		if (action == MotionEvent.ACTION_MOVE && mSelectedElement != null) {
			mTouchCurrentX = event.getX();
			mTouchCurrentY = event.getY();
			
			if (now - mTouchStartTime >= 1.0) {
				mIsClick = false;
			}
			if (Math.abs(mTouchCurrentY - mTouchStartY) + Math.abs(mTouchCurrentX - mTouchStartX) >= 10.0) {
				mIsClick = false;
			}
			findMoveDestination(mRoot, mSelectedElement, event.getX(), event.getY());
			invalidate();
			return true;
		}
		if (action == MotionEvent.ACTION_UP) {
			if (mSelectedElement != null) {
				replaceElement(mPlaceholder, mSelectedElement);
				
				// TODO: duplicate code
				mTouchCurrentX = event.getX();
				mTouchCurrentY = event.getY();
				if (now - mTouchStartTime >= 1.0) {
					mIsClick = false;
				}
				if (Math.abs(mTouchCurrentY - mTouchStartY) + Math.abs(mTouchCurrentX - mTouchStartX) >= 10.0) {
					mIsClick = false;
				}
				if (mIsClick) {
					RectF bbox = mSelectedElement.getLastBoundingBox();
					if (mTouchCurrentX >= bbox.right - 30 * SCREEN_DENSITY &&
						mTouchCurrentX < bbox.right &&
						mTouchCurrentY >= bbox.top &&
						mTouchCurrentY < bbox.bottom) {
						// The remove mark ("X") was pressed.
						removeElement(mRoot, mSelectedElement);
					} else if (mSelectedElement instanceof Interval) {
						showDialog(new IntervalDialog(this, (Interval)mSelectedElement));
					} else if (mSelectedElement instanceof Repeats) {
						showDialog(new RepeatsDialog(this, (Repeats)mSelectedElement));
					}
				}
			}
			mSelectedElement = null;
			removeElement(mRoot, mPlaceholder);
			invalidate();
			return true;
		}
		return true;
	}

	static private Element findElementAtPoint(Repeats container, float x, float y) {
		for (Element elem : container.getChildren()) {
			if (elem instanceof Repeats) {
				// Find the children first since they have smaller bounding boxes since allows
				// for more specific matches.
				Element e2 = findElementAtPoint((Repeats)elem, x, y);
				if (e2 != null) return e2;
			}
			if (isSelected(x, y, elem.getLastBoundingBox())) return elem;
		}
		return null;
	}

	private boolean findMoveDestination(Repeats container, Element movingEntry, float x, float y) {
		for (Element elem : container.getChildren()) {
			if (elem == mPlaceholder) continue;
			
			// Search inside children first, since they have smaller bounding boxes so they will find
			// more specific matches.
			if (elem instanceof Repeats) {
				if (findMoveDestination((Repeats)elem, movingEntry, x, y)) return true;
			}

			final RectF rect = elem.getLastBoundingBox();
			final float height = rect.bottom - rect.top;
			
			final boolean xInBounds = x >= rect.left && x < rect.right;
			
			// If the point is in the upper 1/3 of the rect, create space above the elem.
			if (xInBounds && y < rect.top + height / 3 && y >= rect.top - 10) {
				PlaceholderDuringMove placeholder = getPlaceholder(movingEntry);
				removeElement(mRoot, placeholder);
				container.addBefore(elem, placeholder);
				return true;
			}
			// If the point is in the bottom 1/3 of the rect, create space below the elem.
			if (xInBounds && y >= rect.bottom - height / 3 && y < rect.bottom - 10) {
				PlaceholderDuringMove placeholder = getPlaceholder(movingEntry);
				removeElement(mRoot, placeholder);
				
				// If the element is a container, and the bbox of the moving element is to the right of the container
				// bbox, 
				// make the selectedElem the child of the element. Note that without this sort of hack,
				// there's no way to make an element a child of an empty container.
				if (elem instanceof Repeats && 
						movingEntry.getLastBoundingBox().left >= elem.getLastBoundingBox().left + HORIZONTAL_INDENT_PER_LEVEL * 1.5) {
					((Repeats)elem).prepend(placeholder);
				} else {
					container.addAfter(elem, placeholder);
				}
				return true;
			}
		}
		return false;
	}

	private boolean replaceElement(Element toRemove, Element toAdd) {
		removeElement(mRoot, toAdd);
		return replaceElement(mRoot, toRemove, toAdd);
	}

	private boolean replaceElement(Repeats container, Element toRemove, Element toAdd) {
		final int N = container.getNumChildren();
		for (int i = 0; i < N; ++i) {
			final Element entry = container.getChild(i);
			if (entry == toRemove) {
				container.replaceChildAtIndex(i, toAdd);
				return true;
			}
			if (entry instanceof Repeats) {
				if (replaceElement((Repeats)entry, toRemove, toAdd)) return true;
			}
		}
		return false;
	}

	private boolean removeElement(Repeats container, Element toRemove) {
		final int N = container.getNumChildren();
		for (int i = 0; i < N; ++i) {
			final Element entry = container.getChild(i);
			if (entry == toRemove) {
				container.removeChildAtIndex(i);
				return true;
			}
			if (entry instanceof Repeats) {
				if (removeElement((Repeats)entry, toRemove)) return true;
			}
		}
		return false;
	}

	//
	// Screen drawing
	//
	private final Paint mPaint = new Paint();
	private final StringBuilder mTmpStringBuilder = new StringBuilder();

	@Override public void onDraw(Canvas canvas) {
		mPaint.setAntiAlias(true);
		
		float y = 0;
		final float density = getContext().getResources().getDisplayMetrics().scaledDensity;
		float x = 20 * density;

		for (Element entry : mRoot.getChildren()) {
			entry.draw(canvas, x, y);
			y += entry.getHeight();
		}
		if (mSelectedElement != null) {
			mSelectedElement.draw(canvas, mTouchCurrentX - mMoveDeltaX, mTouchCurrentY - mMoveDeltaY);
		}
	}
	
	public void showDialog(DialogFragment fragment) {
	    // DialogFragment.show() will take care of adding the fragment
	    // in a transaction.  We also want to remove any currently showing
	    // dialog, so make our own transaction and take care of that here.
		FragmentManager fm = ((Activity)getContext()).getFragmentManager();
	    FragmentTransaction ft = fm.beginTransaction();
	    Fragment prev = fm.findFragmentByTag("interval_dialog");
	    if (prev != null) {
	        ft.remove(prev);
	    }
	    ft.addToBackStack(null);
	    fragment.show(ft, "interval_dialog");
	}

	public void setWorkout(Workout workout) {
		Log.d(TAG, "Set workout: " + workout.toString());
		mSelectedElement = null;
		mRoot = (Repeats)fromWorkout(workout);
		invalidate();
	}
	
	public Workout getWorkout() {
		Workout workout = mRoot.toWorkout();
		return workout;
	}
}
