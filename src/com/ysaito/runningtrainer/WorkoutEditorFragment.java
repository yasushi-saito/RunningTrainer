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
import android.widget.TextView;
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
        		Workout newWorkout = mCanvas.getWorkout();
		
        		// the workout will be of "Repeats" type, so make it into a "Root" type
        		newWorkout.type = "Root";
        		newWorkout.repeats = -1;  // not used
        		newWorkout.name = mWorkoutNameEditor.getText().toString();
        		newWorkout.id = mWorkout.id;

        		final FilenameSummary f = new FilenameSummary();
        		f.putLong(FileManager.KEY_WORKOUT_ID, newWorkout.id);
        		f.putString(FileManager.KEY_WORKOUT_NAME, FileManager.sanitizeString(newWorkout.name));
        		final String newBasename = f.getBasename();
        		
        		try {
        			// TODO: async
        			FileManager.writeFile(mWorkoutDir, f.getBasename(), newWorkout);
        		} catch (Exception e) {
        			Toast.makeText(getActivity(), "Failed to save workout: " + e.toString(), Toast.LENGTH_LONG).show();
        		}
        		FileManager.listFilesAsync(mWorkoutDir, new FileManager.ListFilesListener() {
					public void onFinish(Exception e, ArrayList<FilenameSummary> files) {
						// Delete the old file(s) for the same workout
						ArrayList<String> toDelete = new ArrayList<String>();
						for (FileManager.FilenameSummary f : files) {
							if (f.getLong(FileManager.KEY_WORKOUT_ID, -1) == mWorkout.id &&
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

