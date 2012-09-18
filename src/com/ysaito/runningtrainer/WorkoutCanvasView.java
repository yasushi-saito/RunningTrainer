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
import android.graphics.Color;
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
import android.widget.Toast;

public class WorkoutCanvasView extends View implements View.OnTouchListener {
	static public final String TAG = "WorkoutCanvasView";

	public static class IntervalDialog extends DialogFragment {
		private View mDistanceBox, mDurationBox;
		private EditText mDistanceEditor;
		private EditText mDurationEditor;		
		private EditText mFastPaceEditor;
		private EditText mSlowPaceEditor;		
		private final View mParentView;
		private final Interval mElem;
		private final Settings mSettings;
		
		public IntervalDialog(View parentView, Interval elem, Settings settings) { 
			mParentView = parentView;
			mElem = elem; 
			mSettings = settings;
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
	        	mDistanceEditor.setText(Util.distanceToString(mElem.getDistance(), mSettings));
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
	        	mFastPaceEditor.setText(Util.paceToString(fast, mSettings));
	        } else {
	        	mFastPaceEditor.setText("");
	        }
	        
	        mSlowPaceEditor = (EditText)v.findViewById(R.id.edit_slow_pace);
	        final double slow = mElem.getSlowTargetPace();
	        if (Workout.hasSlowTargetPace(slow)) {
	        	mSlowPaceEditor.setText(Util.paceToString(slow, mSettings));
	        } else {
	        	mSlowPaceEditor.setText("");
	        }
	        
	    	AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
	                .setTitle("Edit Interval")
	                .setPositiveButton(android.R.string.ok,
	                    new DialogInterface.OnClickListener() {
	                        public void onClick(DialogInterface dialog, int whichButton) {
	                        	String error = mElem.tryUpdate(
	                        			mDurationEditor.getText().toString(),
	                        			mDistanceEditor.getText().toString(),
	                        			mFastPaceEditor.getText().toString(),
	                        			mSlowPaceEditor.getText().toString());
	                        	if (error != null) {
	                        		Toast.makeText(getActivity(), error, Toast.LENGTH_LONG).show();
	                        	} else {
	                        		mParentView.invalidate();
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
		private final Settings mSettings;
		
		public RepeatsDialog(View parentView, Repeats elem, Settings settings) { 
			mParentView = parentView;
			mElem = elem; 
			mSettings = settings;
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
	                        	String error = mElem.tryUpdate(
	                        			mNumRepeatsEditor.getText().toString());
	                        	if (error != null) {
	                        		Toast.makeText(getActivity(), error, Toast.LENGTH_LONG).show();
	                        	} else {
	                        		mParentView.invalidate();
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
		public ArrayList<Element> getChildren();
		public void draw(Canvas canvas, float x, float y);
		public float getHeight();
		public RectF getLastBoundingBox();
	}

	private final float mScreenDensity = getContext().getResources().getDisplayMetrics().scaledDensity;
			
	private class PlaceholderDuringMove implements Element {
		private float mHeight = 1.0f;
		
		private final RectF mLastBoundingBox = new RectF(-1.0f, -1.0f, -1.0f, -1.0f);

		public void setDimension(float height) { 
			mHeight = height; 
		}
		
		public ArrayList<Element> getChildren() { return null; }
		public void draw(Canvas canvas, float x, float y) { 
			mPaint.setStyle(Paint.Style.STROKE);
			mPaint.setColor(0xffc0ffc0);
			mPaint.setStrokeWidth(2 * mScreenDensity);
			mPaint.setPathEffect(new DashPathEffect(new float[] {10,20}, 0));			
			final int screenWidth = getWidth();
			canvas.drawRect(x, y, screenWidth - 5 * mScreenDensity, y + mHeight, mPaint);
		}
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
		mPlaceholder.setDimension(height);
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

		// Accessors
		public double getDuration() { return mDuration; }
		public double getDistance() { return mDistance; }
		public double getFastTargetPace() { return mFastTargetPace; }
		public double getSlowTargetPace() { return mSlowTargetPace; }		

		/**
		 * Update the interval params. Arguments are string represention of the new values.
		 * Returns an error message if the new param values are invalid. Returns null on success.
		 */
		public String tryUpdate(String durationStr, String distanceStr, String fastTargetPaceStr, String slowTargetPaceStr) {
			double distance = -1.0;
			if (!distanceStr.isEmpty()) {
				distance = Util.distanceFromString(distanceStr, mSettings);
				if (distance < 0.0) return "Failed to parse distance " + distanceStr;
			}
			double duration = 1.0;
			if (!durationStr.isEmpty()) {
				duration = Util.durationFromString(durationStr);
				if (duration < 0.0) return "Failed to parse duration " + durationStr;
			}
			double fastTargetPace = Workout.NO_FAST_TARGET_PACE;
			if (!fastTargetPaceStr.isEmpty()) {
				fastTargetPace = Util.paceFromString(fastTargetPaceStr, mSettings);
				if (fastTargetPace < 0.0) return "Failed to parse pace " + fastTargetPaceStr;
			}
			double slowTargetPace = Workout.NO_SLOW_TARGET_PACE;
			if (!slowTargetPaceStr.isEmpty()) {
				slowTargetPace = Util.paceFromString(slowTargetPaceStr, mSettings);
				if (slowTargetPace < 0.0) return "Failed to parse pace " + fastTargetPaceStr;
			}
			if (fastTargetPace > slowTargetPace) {
				return String.format("Empty pace range: [%s(%f), %s(%f)]", fastTargetPaceStr, fastTargetPace, 
						slowTargetPaceStr, slowTargetPace);
			}
			mDistance = distance;
			mDuration = duration;
			mFastTargetPace = fastTargetPace;
			mSlowTargetPace = slowTargetPace;
			return null;
		}
		
		@Override public String toString() { 
			return String.format("Interval: duration=%f distance=%f pace=[%f,%f]", mDuration, mDistance, mFastTargetPace, mSlowTargetPace);
		}
		
		
		// Element interface implementation
		public RectF getLastBoundingBox() { return mLastBoundingBox; }
		
		public ArrayList<Element> getChildren() { return null; }
		public float getHeight() {
			return 48 * mScreenDensity;
		}
		
		public void draw(Canvas canvas, float x, float y) {
			mPaint.setTextSize(24 * mScreenDensity);
			mPaint.setColor(0xffc0ffc0);
			mPaint.setStyle(Paint.Style.FILL);

			final int screenWidth = getWidth();

			// Remember the rectangle for future calls to isSelected() 
			mLastBoundingBox.left = x;
			mLastBoundingBox.right = screenWidth - 5 * mScreenDensity;
			mLastBoundingBox.top = y;
			mLastBoundingBox.bottom = y + 36 * mScreenDensity;
			canvas.drawRect(mLastBoundingBox, mPaint);
			
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
			if (mFastTargetPace <= 0 && mSlowTargetPace <= 0) {
				b.append("No target");
			} else {
				if (mFastTargetPace > 0) {
					b.append(Util.paceToString(mFastTargetPace, mSettings));
				} else {
					b.append("-");
				}
				b.append(" to ");
				if (mSlowTargetPace > 0) {
					b.append(Util.paceToString(mSlowTargetPace, mSettings));
				} else {
					b.append("-");
				}
			}
			mPaint.setColor(0xff000000);
			canvas.drawText(b.toString(), x + 2 * mScreenDensity, y + 25 * mScreenDensity, mPaint);
			
			Bitmap bitmap = BitmapFactory.decodeResource(getResources(), android.R.drawable.ic_delete);
			canvas.drawBitmap(bitmap, x + screenWidth - 2 * bitmap.getWidth(), y, mPaint);
		}
	}

	private class Repeats implements Element {
		public int mRepeats = 0;
		public final ArrayList<Element> mEntries = new ArrayList<Element>();
		private RectF mLastBoundingBox = new RectF(-1.0f, -1.0f, -1.0f, -1.0f);

		public int getRepeats() { return mRepeats; }
		
		/**
		 * Update the interval params. Arguments are string represention of the new values.
		 * Returns an error message if the new param values are invalid. Returns null on success.
		 */
		public String tryUpdate(String repeatsStr) {
			try {
				int n = Integer.parseInt(repeatsStr);
				if (n <= 0) {
					return repeatsStr + ": Negative repeats not allowed";
				}
				mRepeats = n;
				Log.d(TAG, "SET REPEATS: " + mRepeats);
				return null;
			} catch (NumberFormatException e) {
				return e.toString();
			}
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
			mPaint.setTextSize(24 * mScreenDensity);

			mPaint.setColor(0xffffc0c0);
			mPaint.setStyle(Paint.Style.FILL);
			canvas.drawRect(x, y, screenWidth - 5 * mScreenDensity, y + 36 * mScreenDensity, mPaint);
			mPaint.setColor(0xff000000);
			canvas.drawText(String.format("Repeat %d times", mRepeats), x + 2 * mScreenDensity, y + 25 * mScreenDensity, mPaint);
			mLastBoundingBox.left = x;
			mLastBoundingBox.right = screenWidth - x;
			mLastBoundingBox.top = y;

			y += 48 * mScreenDensity;
			for (Element entry : mEntries) {
				entry.draw(canvas, x + 30 * mScreenDensity, y);
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

	private ArrayList<Element> mEntries = new ArrayList<Element>();

	public WorkoutCanvasView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setOnTouchListener(this);

		Interval interval = new Interval();
		interval.mDistance = Util.METERS_PER_MILE;
		interval.mFastTargetPace = -1.0;
		interval.mSlowTargetPace = 330 / Util.METERS_PER_MILE;
		mEntries.add(interval);

		interval = new Interval();
		interval.mDuration = 300;
		interval.mFastTargetPace = 400/ Util.METERS_PER_MILE;
		interval.mSlowTargetPace = 430 / Util.METERS_PER_MILE;
		mEntries.add(interval);

		Repeats r = new Repeats();
		r.mRepeats = 95;

		interval = new Interval();
		interval.mDuration = 300;
		interval.mFastTargetPace = 400/ Util.METERS_PER_MILE;
		interval.mSlowTargetPace = 430 / Util.METERS_PER_MILE;
		r.mEntries.add(interval);

		interval = new Interval();
		interval.mDuration = 250;
		interval.mFastTargetPace = 400/ Util.METERS_PER_MILE;
		interval.mSlowTargetPace = 430 / Util.METERS_PER_MILE;
		r.mEntries.add(interval);

		mEntries.add(r);

		interval = new Interval();
		interval.mFastTargetPace = 400/ Util.METERS_PER_MILE;
		interval.mSlowTargetPace = 430 / Util.METERS_PER_MILE;
		mEntries.add(interval);
	}

	public void addNewInterval() {
		Interval interval = new Interval();
		interval.mDistance = Util.METERS_PER_MILE;
		interval.mFastTargetPace = 400/ Util.METERS_PER_MILE;
		interval.mSlowTargetPace = 430 / Util.METERS_PER_MILE;
		mEntries.add(interval);
		invalidate();
	}

	public void addNewRepeats() {
		Repeats repeats = new Repeats();
		repeats.mRepeats = 5;
		mEntries.add(repeats);
		invalidate();
	}
	
	//
	// onTouch processing
	//
	private Element mMovingEntry;
	
	// The touch location on the DOWN event
	private float mMoveStartX, mMoveStartY;
	
	// The time of the DOWN event. Seconds since 1970/1/1
	private double mMoveStartTime; 
	
	// The current touch location.
	private float mMoveCurrentX, mMoveCurrentY;
	
	private float mMoveDeltaX, mMoveDeltaY;
	
	// Whether the current touch event is considered to be a click or drag.
	private boolean mIsClick = false; 

	public boolean onTouch(View v, MotionEvent event) {
		final int action = event.getAction();
		final double now = System.currentTimeMillis() / 1000.0;
		if (action == MotionEvent.ACTION_DOWN) {
			mMovingEntry = findEntryAtPosition(event.getX(), event.getY());
			if (mMovingEntry != null) {
				RectF rect = mMovingEntry.getLastBoundingBox();
				mMoveStartX = event.getX();
				mMoveStartY = event.getY();				
				mMoveDeltaX = mMoveStartX - rect.left;
				mMoveDeltaY = mMoveStartY - rect.top;
				mMoveStartTime = now;
				mIsClick = true;  
				replaceElement(mMovingEntry, getPlaceholder(mMovingEntry.getHeight()));
			}
			return true;
		}
		if (action == MotionEvent.ACTION_MOVE && mMovingEntry != null) {
			mMoveCurrentX = event.getX();
			mMoveCurrentY = event.getY();
			
			if (now - mMoveStartTime >= 1.0) {
				mIsClick = false;
			}
			if (Math.abs(mMoveCurrentY - mMoveStartY) + Math.abs(mMoveCurrentX - mMoveStartX) >= 10.0) {
				mIsClick = false;
			}

			findMoveDestination(mMovingEntry, event.getX(), event.getY());
			invalidate();
			return true;
		}
		if (action == MotionEvent.ACTION_UP) {
			if (mMovingEntry != null) {
				replaceElement(mPlaceholder, mMovingEntry);
				
				// TODO: duplicate code
				mMoveCurrentX = event.getX();
				mMoveCurrentY = event.getY();
				if (now - mMoveStartTime >= 1.0) {
					mIsClick = false;
				}
				if (Math.abs(mMoveCurrentY - mMoveStartY) + Math.abs(mMoveCurrentX - mMoveStartX) >= 10.0) {
					mIsClick = false;
				}
				if (mIsClick) {
					if (mMovingEntry instanceof Interval) {
						showDialog(new IntervalDialog(this, (Interval)mMovingEntry, mSettings));
					} else if (mMovingEntry instanceof Repeats) {
						showDialog(new RepeatsDialog(this, (Repeats)mMovingEntry, mSettings));
					}
				}
			}
			mMovingEntry = null;
			removeElement(mPlaceholder);
			invalidate();
			return true;
		}
		return true;
	}

	static private Element findEntryAtPositionRec(Element e, float x, float y) {
		// Find the children first since they have smaller bounding boxes since allows
		// for more specific matches.
		ArrayList<Element> children = e.getChildren();
		if (children != null) {
			for (Element child : children) {
				Element e2 = findEntryAtPositionRec(child, x, y);
				if (e2 != null) return e2;
			}
		}
		if (isSelected(x, y, e.getLastBoundingBox())) return e;
		return null;
	}

	private boolean findMoveDestinationRec(ArrayList<Element> list, Element movingEntry, float x, float y) {
		final int N = list.size();
		for (int i = 0; i < N; ++i) {
			final Element entry = list.get(i);
			if (entry == mPlaceholder) continue;
			
			// Search inside children first, since they have smaller bounding boxes so they will find
			// more specific matches.
			ArrayList<Element> children = entry.getChildren();
			if (children != null) {
				if (findMoveDestinationRec(children, movingEntry, x, y)) return true;
			}

			final RectF rect = entry.getLastBoundingBox();
			final float height = rect.bottom - rect.top;
			
			final boolean xInBounds = x >= rect.left && x < rect.right;
			
			// If the point is in the upper 1/3 of the rect, create space above the entry.
			if (xInBounds && y < rect.top + height / 3 && y >= rect.top - 10) {
				PlaceholderDuringMove placeholder = getPlaceholder(movingEntry.getHeight());
				removeElement(placeholder);
				list.add(list.indexOf(entry), placeholder); 
				Log.d(TAG, "Move above: "+ entry.toString());
				return true;
			}
			// If the point is in the bottom 1/3 of the rect, create space below the entry.
			if (xInBounds && y >= rect.bottom - height / 3 && y < rect.bottom- 10) {
				PlaceholderDuringMove placeholder = getPlaceholder(movingEntry.getHeight());
				removeElement(placeholder);
				list.add(list.indexOf(entry) + 1, placeholder); 
				Log.d(TAG, "Move below: "+ entry.toString());
				return true;
			}
		}
		return false;
	}

	private boolean findMoveDestination(Element movingEntry, float x, float y) {
		return findMoveDestinationRec(mEntries, movingEntry, x, y);
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
	private final Paint mPaint = new Paint();
	private final StringBuilder mTmpStringBuilder = new StringBuilder();
	private Settings mSettings = null;

	@Override public void onDraw(Canvas canvas) {
		mSettings = Settings.getSettings(getContext());
		mPaint.setAntiAlias(true);
		
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
}
