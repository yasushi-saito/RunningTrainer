package com.ysaito.runningtrainer;

import java.io.File;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ysaito.runningtrainer.FileManager.FilenameSummary;

import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

public class WorkoutEditorFragment extends Fragment {
	private File mWorkoutDir;
	private WorkoutCanvasView mCanvas;
	private Workout mWorkout = null;
	
	// TODO: Add "no target pace" button
	// TODO: make the code more resilient to corrupt workouts.
	public void setWorkout(Workout w) { 
		mWorkout = w;
		if (mCanvas != null) mCanvas.setWorkout(w);
	}
	@Override 
    public View onCreateView(
    		LayoutInflater inflater, 
    		ViewGroup container,
            Bundle savedInstanceState) {
		if (container == null) return null;
		mWorkoutDir = FileManager.getWorkoutDir(getActivity());
        View view = inflater.inflate(R.layout.workout_editor, container, false);
        
        mCanvas = (WorkoutCanvasView)view.findViewById(R.id.canvas);
        if (mWorkout != null) mCanvas.setWorkout(mWorkout);
        
        Button button = (Button)view.findViewById(R.id.save_button);
        button.setOnClickListener(new Button.OnClickListener() {
        	public void onClick(View v) {
        		Workout workout = mCanvas.getWorkout();
        		
        		FilenameSummary f = new FilenameSummary();
        		f.putLong(FileManager.KEY_WORKOUT_ID, workout.id);
        		f.putString(FileManager.KEY_WORKOUT_NAME, FileManager.sanitizeString(workout.name));
        		try {
        			// TODO: async
        			FileManager.writeFile(mWorkoutDir, f.getBasename(), workout);
        		} catch (Exception e) {
        			Toast.makeText(getActivity(), "Failed to save workout: " + e.toString(), Toast.LENGTH_LONG).show();
        		}
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

}
