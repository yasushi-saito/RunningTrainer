package com.ysaito.runningtrainer;

import java.io.File;
import java.util.ArrayList;

import com.ysaito.runningtrainer.FileManager.FilenameSummary;

import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class WorkoutEditorFragment extends Fragment {
	private final String TAG = "WorkoutEditor";
	private File mWorkoutDir;
	private EditText mWorkoutNameEditor;
	private WorkoutCanvasView mCanvas;
	private Workout mWorkout = null;
	
	// TODO: Add "no target pace" button
	// TODO: make the code more resilient to corrupt workouts.
	public void setWorkout(Workout w) { 
		mWorkout = new Workout(w);
	}
	
	@Override 
    public View onCreateView(
    		LayoutInflater inflater, 
    		ViewGroup container,
            Bundle savedInstanceState) {
		mWorkoutDir = FileManager.getWorkoutDir(getActivity());
		View view = inflater.inflate(R.layout.workout_editor, container, false);
		mWorkoutNameEditor = (EditText)view.findViewById(R.id.edit_workout_name);
        mCanvas = (WorkoutCanvasView)view.findViewById(R.id.canvas);

        Button button = (Button)view.findViewById(R.id.save_button);
        button.setOnClickListener(new Button.OnClickListener() {
        	public void onClick(View v) {
        		final long workoutId = mWorkout.id;
        		final Workout newWorkout = mCanvas.getWorkout();
		
        		// the workout will be of "Repeats" type, so make it into a "Root" type
        		// TODO: this probably isn't necessary.
        		newWorkout.type = Workout.TYPE_REPEATS;
        		newWorkout.repeats = 1; 
        		newWorkout.name = mWorkoutNameEditor.getText().toString();
        		newWorkout.id = workoutId;

        		final FilenameSummary f = new FilenameSummary();
        		f.putLong(FileManager.KEY_WORKOUT_ID, newWorkout.id);
        		f.putString(FileManager.KEY_WORKOUT_NAME, FileManager.sanitizeString(newWorkout.name));
        		final String newBasename = f.getBasename();

        		FileManager.writeFileAsync(mWorkoutDir, f.getBasename(), newWorkout, new FileManager.ResultListener() {
        			public void onFinish(Exception e) {
        				if (e != null) {
        					Util.error(getActivity(), "Failed to save workout: " + e);
        					return;
        				}
        				deleteOldFilesForWorkout(workoutId, newBasename);
        			}
        		});
        	}
        });
        
        button = (Button)view.findViewById(R.id.cancel_button);
        button.setOnClickListener(new Button.OnClickListener() {
        	public void onClick(View v) {

        	}
        });
        
        button = (Button)view.findViewById(R.id.new_interval_button);
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

	private void deleteOldFilesForWorkout(final long workoutId, final String newBasename) {
		FileManager.listFilesAsync(mWorkoutDir, new FileManager.ListFilesListener() {
			public void onFinish(Exception e, ArrayList<FilenameSummary> files) {
				// Delete the old file(s) for the same workout
				ArrayList<String> toDelete = new ArrayList<String>();
				for (FileManager.FilenameSummary f : files) {
					if (f.getLong(FileManager.KEY_WORKOUT_ID, -1) == workoutId &&
							!f.getBasename().equals(newBasename)) {
						toDelete.add(f.getBasename());
					}
				}
				if (toDelete.size() > 0) {
					String[] array = toDelete.toArray(new String[0]);
					FileManager.deleteFilesAsync(mWorkoutDir, array, new FileManager.ResultListener() {
						public void onFinish(Exception e) {
							showWorkoutListFragment();
							// TODO: handle errors
						}
					});
				} else {
					showWorkoutListFragment();							
				}
			}
		});
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

