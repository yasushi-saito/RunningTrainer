package com.ysaito.runningtrainer;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class WorkoutEditorFragment extends Fragment {
	
	@Override 
    public View onCreateView(
    		LayoutInflater inflater, 
    		ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.workout_editor, container, false);
	}

}
