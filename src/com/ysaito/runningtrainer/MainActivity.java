package com.ysaito.runningtrainer;

/**
 * TODO: sort record list
 * TODO: smooth GPS readouts and reduce sampling rate
 * TODO: align IntervalDialog text boxes
 * TODO: workouts. 
 * TODO: periodic timer activity voice readouts (water!, gu!, etc)
 * TODO: automatic syncing of records on reconnect and/or token authorization
 * TODO: delete all recordsand
 * TODO: show status in notification tray
 * TODO: show runkeeper sync status somewhere
 * TODO: detect when the user pauses during running
 * TODO: sync all. 
 * TODO: show some indicator when runkeeper communication is happening
 * TODO: notification to show distance, duration, etc.
 * TODO: undo of delete record
 * TODO: reliably check if TTS voice data has been downloaded.
 * TODO: satellite view
 */
import java.util.HashMap;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.app.FragmentTransaction;
import android.util.Log;
import android.view.Menu;
import android.view.Window;
import android.widget.Toast;

public class MainActivity extends Activity {
	static final String TAG = "Main";
	private final HashMap<String, MyTabListener> mTabs = new HashMap<String, MyTabListener>();
	
	public Fragment findOrCreateFragment(String className) {
		final FragmentManager manager = getFragmentManager();
		Fragment fragment = manager.findFragmentByTag(className);

		FragmentTransaction ft = manager.beginTransaction();
		if (fragment == null) {
		    fragment = Fragment.instantiate(this, className, null);
			ft.add(android.R.id.content, fragment, className);
		} 
		
		// Detach the fragment from the screen just in case it's already running.
		ft.detach(fragment);
		ft.commit();
		return fragment;
	}

	public void setFragmentForTab(String tabText, Fragment fragment) {
		final MyTabListener tab = mTabs.get(tabText);
		tab.setFragment(fragment);
	}

	public Fragment appendTab(String tabText, Fragment fragment) {
		final FragmentManager manager = getFragmentManager();
		
		ActionBar.Tab tab = getActionBar().newTab();
		MyTabListener listener = new MyTabListener(this, tab, fragment);
		mTabs.put(tabText, listener);
		tab.setText(tabText);
		tab.setTabListener(listener);
		final ActionBar bar = getActionBar();
		bar.addTab(tab);

		return fragment;
	}

    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
    	Settings.Initialize(getApplicationContext());
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS); 
        
        final ActionBar bar = getActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        bar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
                
        appendTab("Record", findOrCreateFragment("com.ysaito.runningtrainer.RecordingFragment"));
        appendTab("Log", findOrCreateFragment("com.ysaito.runningtrainer.RecordListFragment"));
        appendTab("Workout", findOrCreateFragment("com.ysaito.runningtrainer.WorkoutEditorFragment"));
        appendTab("Setting", findOrCreateFragment("com.ysaito.runningtrainer.SettingsFragment"));

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
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    public interface OnBackPressedListener {
    	// If the method returns false, the event is propagated to the
    	// MainActivity's superclass.
    	public boolean onBackPressed();
    }
    
    // There should be only one foreground fragment, so we need to keep
    // just one listener. This assumption may not hold on tablets (or super-big-screen
    // phones), so rethink once we suuport tablets.
    private OnBackPressedListener mOnBackPressedListener = null;
    
    public void registerOnBackPressedListener(OnBackPressedListener listener) {
    	mOnBackPressedListener = listener;
    }
    
    public void unregisterOnBackPressedListener(OnBackPressedListener listener) {
    	if (mOnBackPressedListener == listener) {
    		mOnBackPressedListener = null;
    	}
    }
    
    @Override public void onBackPressed() {
    	if (mOnBackPressedListener != null) {
    		if (mOnBackPressedListener.onBackPressed()) return; 
    	}
    	super.onBackPressed();
    }
    
    public static class MyTabListener implements ActionBar.TabListener {
        private final Activity mActivity;
    	private final ActionBar.Tab mTab;
        private Fragment mFragment = null;

        public MyTabListener(Activity activity,  ActionBar.Tab tab, Fragment fragment) {
        	mActivity = activity;
        	mTab = tab;
        	mFragment = fragment;
        }

        public void setFragment(Fragment fragment) {
        	final FragmentManager manager = mActivity.getFragmentManager();
        	final FragmentTransaction ft = manager.beginTransaction();
        	ft.detach(mFragment);
			ft.attach(fragment);
			ft.commit();
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
