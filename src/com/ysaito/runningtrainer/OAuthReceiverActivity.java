package com.ysaito.runningtrainer;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.content.Intent;
import android.util.Log;

public class OAuthReceiverActivity extends Activity {
	static final String TAG = "OAuth";
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	Intent intent = getIntent();
    	Log.d(TAG, "onNewIntent: " + intent.toString());
    	Uri uri = intent.getData();
    	if (uri != null) HealthGraphClient.onRedirect(uri);
    }
}
