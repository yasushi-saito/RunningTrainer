package com.ysaito.runningtrainer;

import com.google.android.maps.MapActivity;

import android.os.Bundle;
import android.view.Menu;

public class MainActivity extends MapActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
    
    @Override
    public boolean isRouteDisplayed() { return false; }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    
}
