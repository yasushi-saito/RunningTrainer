package com.ysaito.runningtrainer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;

public class StartActivity extends Activity {
	static final String TAG = "Start";
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        Log.d(TAG, "RunningTrainer started");
        
        Button startButton = (Button)findViewById(R.id.start_button);
        startButton.setOnClickListener(new Button.OnClickListener() {
        	public void onClick(View v) { 
        		startActivity(new Intent(v.getContext(), RecordActivity.class));
        	}
        });

    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

}
