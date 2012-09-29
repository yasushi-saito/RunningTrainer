package com.ysaito.runningtrainer;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;

public class DisplaySettingsView extends DialogPreference {
	public DisplaySettingsView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setDialogLayoutResource(R.layout.speech_settings_dialog);
	}
	public DisplaySettingsView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setDialogLayoutResource(R.layout.speech_settings_dialog);
	}


	// along with constructors, you will want to override
    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        // view is your layout expanded and added to the dialog
        // find and hang on to your views here, add click listeners etc
        // basically things you would do in onCreate
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
    	super.onDialogClosed(positiveResult);
    	
    	if (positiveResult) {
    		// deal with persisting your values here
    	}
    }
}
