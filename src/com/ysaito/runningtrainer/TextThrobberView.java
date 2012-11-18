package com.ysaito.runningtrainer;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

public class TextThrobberView extends TextView {
	private AnimatorSet mAnimator = null;
	
	public TextThrobberView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public final void startAnimation() {
		if (mAnimator == null) {
			ObjectAnimator fadeOut = ObjectAnimator.ofFloat(this, "alpha", 1f, 0.2f);
			fadeOut.setDuration(500);
			ObjectAnimator fadeIn = ObjectAnimator.ofFloat(this, "alpha", 0.2f, 1f);
			fadeIn.setDuration(500);
			mAnimator = new AnimatorSet();
			
			mAnimator.playSequentially(fadeIn, fadeOut);
			mAnimator.start();
			
			mAnimator.addListener(new Animator.AnimatorListener() {
                public void onAnimationCancel(Animator unused) {
                	
                }
                public void onAnimationEnd(Animator unused) {
                	mAnimator.start();
                }
                public void onAnimationRepeat(Animator arg0) {
                }
                public void onAnimationStart(Animator arg0) {
                }
            });


		}
	}
	
	public final void stopAnimation() {
		if (mAnimator != null) {
			mAnimator.removeAllListeners();
			mAnimator.end();
			mAnimator = null;
		}
	}
}
