package com.ysaito.runningtrainer;

import android.app.Activity;
import android.app.Fragment;
import android.app.LocalActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

@SuppressWarnings("deprecation")
public abstract class MapWrapperFragment extends Fragment {
	protected abstract Class<?> getActivityClass();
	
	private final String mTag;
    private static final String KEY_STATE_BUNDLE = "localActivityManagerState";
	private LocalActivityManager mLocalActivityManager;
	private Activity mChildActivity = null;

	public MapWrapperFragment(String logTag) { 
		mTag = logTag; 
		Plog.d(mTag, "construct " + Util.currentStackTrace());
	}
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	Plog.d(mTag, "onCreate");
        Bundle state = null;
        if (savedInstanceState != null) {
            state = savedInstanceState.getBundle(KEY_STATE_BUNDLE);
        }
        mLocalActivityManager = new LocalActivityManager(getActivity(), true);
        mLocalActivityManager.dispatchCreate(state);
    }
    
    @Override
    public View onCreateView(
    		LayoutInflater inflater, 
    		ViewGroup container,
            Bundle savedInstanceState) {
    	Plog.d(mTag, "onCreateView");
    	Intent intent = new Intent(getActivity(), getActivityClass());
        Window window = mLocalActivityManager.startActivity("tag", intent); 
        View currentView = window.getDecorView(); 
        currentView.setVisibility(View.VISIBLE); 
        currentView.setFocusableInTouchMode(true); 
        ((ViewGroup) currentView).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        
        mChildActivity = mLocalActivityManager.getActivity("tag");
        return currentView;
    }

    /**
     * @return Get the Activity object (of type getActivityClass()) that's wrapped inside this fragment. May return null if
     * onCreateView hasn't been called.
     */
    public final Activity getChildActivity() { return mChildActivity; }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle(KEY_STATE_BUNDLE,
                mLocalActivityManager.saveInstanceState());
    }

    @Override
    public void onResume() {
        super.onResume();
    	Plog.d(mTag, "onResume");
        mLocalActivityManager.dispatchResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    	Plog.d(mTag, "onPause");
        mLocalActivityManager.dispatchPause(getActivity().isFinishing());
    }

    @Override
    public void onStop() {
        super.onStop();
    	Plog.d(mTag, "onStop");
        mLocalActivityManager.dispatchStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    	Plog.d(mTag, "onDestroy");
        mLocalActivityManager.dispatchDestroy(getActivity().isFinishing());
    }
}
