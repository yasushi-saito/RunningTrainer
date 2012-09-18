package com.ysaito.runningtrainer;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

public class WorkoutEditorFragment extends Fragment {
	
	@Override 
    public View onCreateView(
    		LayoutInflater inflater, 
    		ViewGroup container,
            Bundle savedInstanceState) {
		if (container == null) return null;
        View view = inflater.inflate(R.layout.workout_editor, container, false);
        
        final WorkoutCanvasView canvas = (WorkoutCanvasView)view.findViewById(R.id.canvas);
        Button button = (Button)view.findViewById(R.id.new_interval_button);
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
