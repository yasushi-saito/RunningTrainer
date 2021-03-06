package com.ysaito.runningtrainer;

import java.io.File;
import java.util.ArrayList;

import com.ysaito.runningtrainer.FileManager.ParsedFilename;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

public class WorkoutEditorFragment extends Fragment {
	private final String TAG = "WorkoutEditor";
	private File mWorkoutDir;
	private EditText mWorkoutNameEditor;
	private WorkoutCanvasView mCanvas;
	private JsonWorkout mWorkout = null;
	
	// TODO: Add "no target pace" button
	// TODO: make the code more resilient to corrupt workouts.
	public void setWorkout(JsonWorkout w) { 
		mWorkout = new JsonWorkout(w);
	}
	
	public WorkoutEditorFragment() {
		Plog.d(TAG, "construct " + Util.currentStackTrace());
	}
	
	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Plog.d(TAG, "onCreate");
		setHasOptionsMenu(true);
	}

	@Override
    public void onCreateOptionsMenu (Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.workout_editor_options_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }
	
	@Override
	public void onPrepareOptionsMenu (Menu menu) {
		super.onPrepareOptionsMenu(menu);
		menu.findItem(R.id.workout_editor_undo).setEnabled(mCanvas.hasUndo());
	}	
		
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.workout_editor_undo:
			mCanvas.undo();
			return true;
		case R.id.workout_editor_save:
			saveAndExit();
			return true;
		}
		return false;
	}

	private final void saveAndExit() { 
		final long workoutId = mWorkout.id;
		final JsonWorkout newWorkout = mCanvas.getWorkout();
		
		// the workout will be of "Repeats" type, so make it into a "Root" type
		// TODO: this probably isn't necessary.
		newWorkout.type = JsonWorkout.TYPE_REPEATS;
		newWorkout.repeats = 1; 
		newWorkout.name = mWorkoutNameEditor.getText().toString();
		newWorkout.id = workoutId;

		final ParsedFilename newF = new ParsedFilename();
		newF.putLong(FileManager.KEY_WORKOUT_ID, newWorkout.id);
		newF.putString(FileManager.KEY_WORKOUT_NAME, FileManager.sanitizeString(newWorkout.name));
		final String newBasename = newF.getBasename();
		
		FileManager.runAsync(new FileManager.AsyncRunner<Void>() {
			public Void doInThread() throws Exception {
				FileManager.writeJson(new File(mWorkoutDir, newBasename), newWorkout);
				
				// Delete the old file(s) for the same workout
				ArrayList<ParsedFilename> files = FileManager.listFiles(mWorkoutDir);
				for (FileManager.ParsedFilename other : files) {
					if (other.getLong(FileManager.KEY_WORKOUT_ID, -1) == workoutId &&
							!other.getBasename().equals(newBasename)) {
						FileManager.deleteFile(new File(mWorkoutDir, other.getBasename()));
					}
				}
				return null;
			}
			public void onFinish(Exception error, Void unused) {
				if (error != null) Util.error(getActivity(), "Failed to delete old files: " + error);
				showWorkoutListFragment();
			}
		}, Util.DEFAULT_THREAD_POOL);
	}
	
	@Override 
    public View onCreateView(
    		LayoutInflater inflater, 
    		ViewGroup container,
            Bundle savedInstanceState) {
		Plog.d(TAG, "onCreateView");
		mWorkoutDir = FileManager.getWorkoutDir(getActivity());
		View view = inflater.inflate(R.layout.workout_editor, container, false);
		mWorkoutNameEditor = (EditText)view.findViewById(R.id.edit_workout_name);
        mCanvas = (WorkoutCanvasView)view.findViewById(R.id.canvas);

        Button button = (Button)view.findViewById(R.id.new_interval_button);
        button.setOnClickListener(new Button.OnClickListener() {
        	public void onClick(View v) {
        		mCanvas.addNewInterval();
        	}
        });
        button = (Button)view.findViewById(R.id.new_repeats_button);
        button.setOnClickListener(new Button.OnClickListener() {
        	public void onClick(View v) {
        		mCanvas.addNewRepeats();
        	}
        });
        return view;
	}

	public final void showWorkoutListFragment() {
		MainActivity activity = (MainActivity)getActivity();
		activity.setFragmentForTab("Workout",
				activity.findOrCreateFragment("com.ysaito.runningtrainer.WorkoutListFragment"));
	}
	
	private final MainActivity.OnBackPressedListener mOnBackPressedListener = new MainActivity.OnBackPressedListener() {
		public boolean onBackPressed() {
			showWorkoutListFragment();
			return true;
		}
	};
	
	@Override public void onResume() {
		super.onResume();
		((MainActivity)getActivity()).registerOnBackPressedListener(mOnBackPressedListener);
        if (mWorkout != null) {
        	mCanvas.setWorkout(mWorkout);
        	mWorkoutNameEditor.setText(mWorkout.name);
        } 
	}

	@Override public void onPause() {
		((MainActivity)getActivity()).unregisterOnBackPressedListener(mOnBackPressedListener);
		super.onPause();
	}
	
}	

