package com.ysaito.runningtrainer;

/**
 * TODO: create a more formal credits statement
 * 
 * CREDITS: http://button-download.com (Creative commons)
 * LOGO: http://openclipart.org/detail/2883/running-pig-by-liftarn
 * 
 * http://openclipart.org/detail/171405/sports-by-cyberscooty-171405
 */

/**
 * TODO: RecordList: add "view summary" and "view details"
 * TODO: remember sort criteria
 * TODO: get the last GPS location in the recording view
 * TODO: undo of "add interval/repeat" start
 * TODO: enable/disable dependent settings
 * TODO: periodic timer activity voice readouts (water!, gu!, etc)
 * TODO: workout item name & readout
 * TODO: automatic syncing of records on reconnect and/or token authorization
 * TODO: run syncer as a Service.
 * TODO: pause detection should take GPS accuracy into account.
 * TODO: sync all. 
 * TODO: reliably check if TTS voice data has been downloaded.
 * TODO: remove the stats view row when none of the views show anything
 * TODO: change the workout editor so that the interval moves inside a repeat more reliably
 * TODO: in workout editor canvas, set a reasonable default interval spec

 * done (incompletely)	
 * TODO: smooth GPS readouts and reduce sampling rate
 * TODO: show runkeeper sync status somewhere
*/
import java.util.HashMap;

import android.app.ActionBar;
import android.app.ActionBar.LayoutParams;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.app.FragmentTransaction;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
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
		    Plog.d(TAG, "New frag:" + fragment.toString());
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
	
	private GpsStatusView mGpsStatusView;
	private TextThrobberView mTextThrobberView;
	
	/**
	 * 
	 * @param accuracy GPS accuracy, in meters. Pass NO_GPS_STATUS if GPS is not available 
	 */
	public void setGpsStatus(double accuracy) {
		mGpsStatusView.setAccuracy(accuracy);
	}
	
	private int mThrobberNesting = 0;
	
	public void startActionBarThrobber(String text) {
		if (mThrobberNesting == 0) {
			mTextThrobberView.setText(text);
			mTextThrobberView.startAnimation();
			mTextThrobberView.setVisibility(View.VISIBLE);
		}
		++mThrobberNesting;
	}

	public void stopActionBarThrobber() {
		--mThrobberNesting;
		if (mThrobberNesting == 0) {
			mTextThrobberView.stopAnimation();
			mTextThrobberView.setVisibility(View.GONE);
		}
	}
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	Plog.init(getApplicationContext());
    	Plog.d(TAG, "onCreate");
    	if (savedInstanceState != null) {
    		// TODO: we don't currently handle recreation of fragments gracefully.
    		// Perhaps explicitly listing all the fragments to be created in this method would help.
    		//
    		// E.g.:
    		// android.app.FragmentManagerImpl.restoreAllState(FragmentManager.java:1718)
    		// android.app.Activity.onCreate(Activity.java:883)
    		savedInstanceState.clear();
    	}
    	super.onCreate(savedInstanceState);
    	
    	Settings.Initialize(getApplicationContext());
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS); 
        
        final ActionBar bar = getActionBar();

        View barView = getLayoutInflater().inflate(
        		R.layout.action_bar_layout, null);
        mGpsStatusView = (GpsStatusView)barView.findViewById(R.id.action_bar_gps_status);
        setGpsStatus(GpsStatusView.HIDE_GPS_VIEW);
        
        mTextThrobberView = (TextThrobberView)barView.findViewById(R.id.action_bar_text_throbber);
        mTextThrobberView.setVisibility(View.GONE);
        
        bar.setCustomView(barView, 
        		new ActionBar.LayoutParams(
        		LayoutParams.WRAP_CONTENT, 
        		LayoutParams.WRAP_CONTENT,
        		Gravity.LEFT | Gravity.CENTER_VERTICAL));
                   
        final int change = bar.getDisplayOptions() ^ ActionBar.DISPLAY_SHOW_CUSTOM;
        bar.setDisplayOptions(change, ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_USE_LOGO);
        
        
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        bar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
                
        appendTab("Record", findOrCreateFragment("com.ysaito.runningtrainer.RecordingFragment"));
        appendTab("Log", findOrCreateFragment("com.ysaito.runningtrainer.RecordListFragment"));
        appendTab("Workout", findOrCreateFragment("com.ysaito.runningtrainer.WorkoutListFragment"));
        appendTab("Setting", findOrCreateFragment("com.ysaito.runningtrainer.SettingsFragment"));

        if (getExternalFilesDir(null) == null) {
        	Util.error(this, "SD card is not found on this device. No record will be kept");
        }
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
        		Fragment fragment = entry.getValue();
        		if (fragment != mFragment) {
        			Plog.d(TAG, "Frag(detach):" + mName + ": " + fragment.toString());
        			ft.detach(fragment);
        		}
        	}
        	
        	Plog.d(TAG, "Frag(attach):" + mName + ": " + mFragment.toString());
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
