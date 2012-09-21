package com.ysaito.runningtrainer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
	SharedPreferences mPreferences;
	
	private WorkoutCanvasView mCanvas;
	private Workout mWorkout = null;
	
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
		mPreferences = getActivity().getSharedPreferences("workouts", Context.MODE_WORLD_READABLE);
        View view = inflater.inflate(R.layout.workout_editor, container, false);
        
        mCanvas = (WorkoutCanvasView)view.findViewById(R.id.canvas);
        if (mWorkout != null) mCanvas.setWorkout(w);
        
        Button button = (Button)view.findViewById(R.id.save_button);
        button.setOnClickListener(new Button.OnClickListener() {
        	public void onClick(View v) {
        		Workout workout = canvas.getWorkout();
        		
        		Gson gson = new GsonBuilder().create();
        		SharedPreferences.Editor editor = mPreferences.edit();
        		editor.putString(workout.id, gson.toJson(workout));
        		if (!editor.commit()) {
        			Toast.makeText(getActivity(), "FOO!", Toast.LENGTH_LONG).show();
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
        		canvas.addNewInterval();
        	}
        });
        button = (Button)view.findViewById(R.id.new_repeats_button);
        button.setOnClickListener(new Button.OnClickListener() {
        	public void onClick(View v) {
        		canvas.addNewRepeats();
        	}
        });
        return view;
	}

}
