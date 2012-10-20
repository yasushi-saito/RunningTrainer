package com.ysaito.runningtrainer;

/**
 * TODO: create a more formal credits statement
 * 
 * CREDITS: http://button-download.com (Creative commons)
 * LOGO: http://openclipart.org/detail/2883/running-pig-by-liftarn
 * 
 */

/**
 * TODO: show GPS strength
 * TODO: elevation gain and loss should be in feets, not miles
 * TODO: enable/disable dependent settings
 * TODO: satellite/map view mode value should be process-global.
 * TODO: smooth GPS readouts and reduce sampling rate
 * TODO: periodic timer activity voice readouts (water!, gu!, etc)
 * TODO: automatic syncing of records on reconnect and/or token authorization
 * TODO: show runkeeper sync status somewhere
 * TODO: run syncer as a Service.
 * TODO: pause detection should take GPS accuracy into account.
 * TODO: sync all. 
 * TODO: reliably check if TTS voice data has been downloaded.
 * TODO: remove the stats view row when none of the views show anything
 * TODO: change the workout editor so that the interval moves inside a repeat more reliably
 * TODO: current time readout
 * TODO: in workout editor canvas, set a reasonable default interval spec
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

public class MainActivity extends Activity {
	static final String TAG = "Main";

	// Tab name (eg, "WORKOUT") to the tab listener
	private final HashMap<String, MyTabListener> mTabs = new HashMap<String, MyTabListener>();
	
	// Class name -> Fragment instance. Used to detach all background fragments reliably.
	private final HashMap<String, Fragment> mFragments = new HashMap<String, Fragment>();
	
	public Fragment findOrCreateFragment(String className) {
		final FragmentManager manager = getFragmentManager();
		Fragment fragment = manager.findFragmentByTag(className);
		
		if (fragment == null) {
		    fragment = Fragment.instantiate(this, className, null);
		    FragmentTransaction ft = manager.beginTransaction();
			ft.add(android.R.id.content, fragment, className);
			ft.detach(fragment);
			ft.commit();
		} 
		mFragments.remove(className);
		mFragments.put(className, fragment);
		return fragment;
	}

	public void setFragmentForTab(String tabText, Fragment fragment) {
		final MyTabListener tab = mTabs.get(tabText);
		tab.setFragment(fragment);
	}

	private void appendTab(String tabText, Fragment fragment) {
		// Check if the tab already exists. This shouldn't be necessary in principle, but
		// I've found that sometimes onCreate() is called twice w/ tabs already in place.
		final ActionBar bar = getActionBar();
		for (int i = 0; i < bar.getTabCount(); ++i) {
			ActionBar.Tab tab = bar.getTabAt(i);
			if (tab.getText().equals(tabText)) {
				return;
			}
		}
		
		ActionBar.Tab tab = getActionBar().newTab();
		MyTabListener listener = new MyTabListener(this, tabText, fragment);
		mTabs.put(tabText, listener);
		tab.setText(tabText);
		tab.setTabListener(listener);
		bar.addTab(tab);
	}

	@Override public void onDestroy() { 
		super.onDestroy();
		Plog.d(TAG, "onDestroy");
	}
	@Override public void onResume() { super.onResume(); Plog.d(TAG, "onResume"); }
	@Override public void onPause() { super.onPause(); Plog.d(TAG, "onPause"); }
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	Plog.init(getApplicationContext());
    	Plog.d(TAG, "onCreate");
    	super.onCreate(savedInstanceState);
    	
    	Settings.Initialize(getApplicationContext());
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS); 
        
        final ActionBar bar = getActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        bar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
                
        appendTab("Record", findOrCreateFragment("com.ysaito.runningtrainer.RecordingFragment"));
        appendTab("Log", findOrCreateFragment("com.ysaito.runningtrainer.RecordListFragment"));
        appendTab("Workout", findOrCreateFragment("com.ysaito.runningtrainer.WorkoutListFragment"));
        appendTab("Setting", findOrCreateFragment("com.ysaito.runningtrainer.SettingsFragment"));

        if (getExternalFilesDir(null) == null) {
        	Util.error(this, "SD card is not found on this device. No record will be kept");
        }
        
        final HealthGraphClient hgClient = HealthGraphClient.getSingleton();
        hgClient.startAuthentication(this);
        hgClient.getUser(new HealthGraphClient.GetResponseListener() {
        	public void onFinish(Exception e, Object o) {
        		if (e != null) {
        			Log.e(TAG, "GET finished with exception: " + e.toString());
        		} else if (o != null) {
        			Log.d(TAG, "GET ok: " + ((JsonUser)o).toString());
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
        private final MainActivity mActivity;
        private final String mName;
        private Fragment mFragment = null;
        
        public MyTabListener(MainActivity activity, String name, Fragment fragment) {
        	mActivity = activity;
        	mName = name;
        	mFragment = fragment;
        }

        public void setFragment(Fragment fragment) {
        	final FragmentManager manager = mActivity.getFragmentManager();
        	final FragmentTransaction ft = manager.beginTransaction();
        	ft.detach(mFragment);
			ft.attach(fragment);
			ft.commit();
			Plog.d(TAG, "TabSetFragment[" + mName + "]: " + mFragment.toString() + "->" + fragment.toString());
        	mFragment = fragment;
        }
        
        public void onTabSelected(Tab tab, FragmentTransaction ft) {
			Plog.d(TAG, "TabSelected[" + mName + "]");
        	for (HashMap.Entry<String, Fragment> entry : mActivity.mFragments.entrySet()) {
        		Fragment frag = entry.getValue();
        		if (frag != mFragment) ft.detach(frag);
        	}
        	
        	ft.attach(mFragment);
        }

        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        	Plog.d(TAG, "TabUnselected[" + mName + "]");
        	// ft.detach(mFragment);
        }

        public void onTabReselected(Tab tab, FragmentTransaction ft) {
        }
    }
}
