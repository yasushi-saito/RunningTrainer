package com.ysaito.runningtrainer;

import com.google.android.maps.MapActivity;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.net.Uri;
import android.os.Bundle;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;

public class OAuthReceiverActivity extends Activity {
	static final String TAG = "OAuth";
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	Intent intent = getIntent();
    	HealthGraphUtil.Authenticator auth = HealthGraphUtil.getAuthenticator();
    	
    	Log.d(TAG, "onNewIntent: " + intent.toString());
    	if (auth != null) {
    		Uri uri = intent.getData();
    		if (uri != null) auth.onRedirect(uri);
    	}
    }
}
