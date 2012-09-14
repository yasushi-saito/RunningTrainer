package com.ysaito.runningtrainer;

/**
 * TODO: smooth GPS readouts and reduce sampling rate
 * 
 * 
 * TODO: workouts. 
 * TODO: periodic timer activity voice readouts (water!, gu!, etc)
 * 
 * TODO: voice readout of various stats
 * TODO: automatic syncing of records on reconnect and/or token authorization
 * TODO: delete all recordsand
 * TODO: show status in notification tray
 * TODO: show runkeeper sync status somewhere
 * TODO: detect when the user pauses during running
 * TODO: sync all. remember synced records
 * TODO: show past activity in a map
 * TODO: show some indicator when runkeeper communication is happening
 * TODO: notification to show distance, duration, etc.
 * TODO: undo of delete record
 * TODO: reliably check if TTS voice data has been downloaded.
 */
import java.util.Locale;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;

public class MainActivity extends Activity {
	static final String TAG = "Main";

	public void selectTab(String text) {
		final ActionBar bar = getActionBar();
		for (int i = 0; i < bar.getTabCount(); ++i) {
			ActionBar.Tab tab = bar.getTabAt(i);
			if (text.equals(tab.getText())) {
				tab.select();
				return;
			}
		}
	}
	
	public Fragment addTabIfNecessary(String text, String className, String toRightOf) {
		final ActionBar bar = getActionBar();

		final FragmentManager manager = getFragmentManager();
		Fragment fragment = manager.findFragmentByTag(text);
		if (fragment == null) {
		    fragment = Fragment.instantiate(this, className, null);
		    FragmentTransaction ft = manager.beginTransaction();
			ft.add(android.R.id.content, fragment, text);
			ft.detach(fragment);
			ft.commit();
			
			ActionBar.Tab tab = getActionBar().newTab()
					.setText(text)
					.setTabListener(new MyTabListener(this, fragment));
			if (toRightOf == null) {
				bar.addTab(tab, 0);
			} else {
				for (int i = 0; i < bar.getTabCount(); ++i) {
					ActionBar.Tab existing = bar.getTabAt(i);
					if (toRightOf.equals(existing.getText())) {
						bar.addTab(tab, i + 1);
						break;
					}
				}
			}
		}
		return fragment;
	}
	
	private static final int TTS_CHECK_CODE = 123;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
        final ActionBar bar = getActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        bar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);

        addTabIfNecessary("Record", "com.ysaito.runningtrainer.RecordingFragment", null);
        addTabIfNecessary("List", "com.ysaito.runningtrainer.RecordListFragment", "Record");
        addTabIfNecessary("Settings", "com.ysaito.runningtrainer.SettingsFragment", "List");
        Log.d(TAG, "RunningTrainer started");

        if (getExternalFilesDir(null) == null) {
        	Toast toast = Toast.makeText(this, "SD card is not found on this device. No record will be kept", Toast.LENGTH_LONG);
        	toast.show();
        }
        
        final HealthGraphClient hgClient = HealthGraphClient.getSingleton();
        hgClient.startAuthentication(this);
        hgClient.getUser(new HealthGraphClient.GetResponseListener() {
        	public void onFinish(Exception e, Object o) {
        		if (e != null) {
        			Log.e(TAG, "GET finished with exception: " + e.toString());
        		} else if (o != null) {
        			Log.d(TAG, "GET ok: " + ((HealthGraphClient.JsonUser)o).toString());
        		}
        	}
        });
/*        
        // Check the existence of speech synthesis feature
        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, TTS_CHECK_CODE);
  */      
   /*     hgClient.getFitnessActivities(new HealthGraphClient.JsonResponseListener() {
        	public void onFinish(Exception e, Object o) {
        		if (e != null) {
        			Log.e(TAG, "Get fitnessactivities finished with exception: " + e.toString());
        		} else {
        			Log.d(TAG, "Get fitnessactivities ok");
        		}
        	}
        });*/
        mTts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
        	public void onInit(int status) {
        		Log.d(TAG, "TTS initialized");
        		mTts.setLanguage(Locale.US);
        		String myText2 = "60 minutes";
        		mTts.speak(myText2, TextToSpeech.QUEUE_FLUSH, null);
        	}
        });
    }
    
    private TextToSpeech mTts;
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
    
    public static class MyTabListener implements ActionBar.TabListener {
        private final Activity mActivity;
        private final Fragment mFragment;

        public MyTabListener(Activity activity, Fragment fragment) {
        	mActivity = activity;
        	mFragment = fragment;
        }

        public void onTabSelected(Tab tab, FragmentTransaction ft) {
        	ft.attach(mFragment);
        }

        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        	ft.detach(mFragment);
        }

        public void onTabReselected(Tab tab, FragmentTransaction ft) {
            Toast.makeText(mActivity, "Reselected!", Toast.LENGTH_SHORT).show();
        }
    }

    

}
