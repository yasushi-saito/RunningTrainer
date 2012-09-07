package com.ysaito.runningtrainer;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.Scanner;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.app.FragmentTransaction;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;

public class MainActivity extends Activity {
	static final String TAG = "Main";

/*    @Override
    public boolean isRouteDisplayed() { return false; }
*/
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
        final ActionBar bar = getActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        bar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);

        bar.addTab(bar.newTab()
                .setText("Record")
                .setTabListener(new TabListener<RecordingFragment2>(
                        this, "Record", RecordingFragment2.class)));
        
        bar.addTab(bar.newTab()
                .setText("Log")
                .setTabListener(new TabListener<RecordListFragment>(
                        this, "Log", RecordListFragment.class)));
        
        Log.d(TAG, "RunningTrainer started");

        if (getExternalFilesDir(null) == null) {
        	Toast toast = Toast.makeText(this, "SD card is not found on this device. No record will be kept", Toast.LENGTH_LONG);
        	toast.show();
        }
        HealthGraphClient hgClient = HealthGraphClient.getSingleton(
        		this,
        		"0808ef781c68449298005c8624d3700b", 
        		"dda5888cd8d64760a044dc61ae4f44db",
        		"ysaito://oauthresponse");
        hgClient.getUser(new HealthGraphClient.GetResponseListener() {
        	public void onFinish(Exception e, Object o) {
        		if (e != null) {
        			Log.e(TAG, "GET finished with exception: " + e.toString());
        		} else if (o != null) {
        			Log.d(TAG, "GET ok: " + ((HealthGraphClient.JsonUser)o).toString());
        		}
        	}
        });
   /*     hgClient.getFitnessActivities(new HealthGraphClient.JsonResponseListener() {
        	public void onFinish(Exception e, Object o) {
        		if (e != null) {
        			Log.e(TAG, "Get fitnessactivities finished with exception: " + e.toString());
        		} else {
        			Log.d(TAG, "Get fitnessactivities ok");
        		}
        	}
        });*/
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
    
    public static class TabListener<T extends Fragment> implements ActionBar.TabListener {
        private final Activity mActivity;
        private final String mTag;
        private final Class<T> mClass;
        private final Bundle mArgs;
        private Fragment mFragment;

        public TabListener(Activity activity, String tag, Class<T> clz) {
            this(activity, tag, clz, null);
        }

        public TabListener(Activity activity, String tag, Class<T> clz, Bundle args) {
            mActivity = activity;
            mTag = tag;
            mClass = clz;
            mArgs = args;

            // Check to see if we already have a fragment for this tab, probably
            // from a previously saved state.  If so, deactivate it, because our
            // initial state is that a tab isn't shown.
            mFragment = mActivity.getFragmentManager().findFragmentByTag(mTag);
            if (mFragment != null && !mFragment.isDetached()) {
                FragmentTransaction ft = mActivity.getFragmentManager().beginTransaction();
                ft.detach(mFragment);
                ft.commit();
            }
        }

        public void onTabSelected(Tab tab, FragmentTransaction ft) {
            if (mFragment == null) {
                mFragment = Fragment.instantiate(mActivity, mClass.getName(), mArgs);
                ft.add(android.R.id.content, mFragment, mTag);
            } else {
                ft.attach(mFragment);
            }
        }

        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
            if (mFragment != null) {
                ft.detach(mFragment);
            }
        }

        public void onTabReselected(Tab tab, FragmentTransaction ft) {
            Toast.makeText(mActivity, "Reselected!", Toast.LENGTH_SHORT).show();
        }
    }

    

}
