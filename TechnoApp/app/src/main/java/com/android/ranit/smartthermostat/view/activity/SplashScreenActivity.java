package com.android.ranit.smartthermostat.view.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.airbnb.lottie.LottieAnimationView;
import com.android.ranit.smartthermostat.R;
import com.android.ranit.smartthermostat.common.Constants;
import com.android.ranit.smartthermostat.contract.SplashScreenContract;
import com.android.ranit.smartthermostat.databinding.ActivitySplashScreenBinding;

public class SplashScreenActivity extends AppCompatActivity
        implements SplashScreenContract.View {
    private static final String TAG = SplashScreenActivity.class.getSimpleName();
    private static final String SPLASH_SCREEN_ANIMATION = "splash.json";

    private ActivitySplashScreenBinding mBinding;
    private final Handler mSplashHandler = new Handler();

    /**
     * Runnable for displaying splash animation and launching MainActivity
     * after a specified duration.
     */
    private final Runnable mSplashRunnable = new Runnable() {
        @Override
        public void run() {
            launchMainActivity();
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_splash_screen);

        initializeViews();
        setupSplashHandler();
    }

    @Override
    public void initializeViews() {
        Log.d(TAG, "initializeViews() called");
        setupLottieAnimation(mBinding.splashScreenLottieView, SPLASH_SCREEN_ANIMATION);
    }

    @Override
    public void setupLottieAnimation(LottieAnimationView view, String animationName) {
        Log.d(TAG, "setupLottieAnimation() called with: view = [" + view + "], " +
                "animationName = [" + animationName + "]");
        view.setAnimation(animationName);
        view.playAnimation();
    }

    @Override
    public void launchMainActivity() {
        Log.d(TAG, "launchMainActivity() called");
        Intent intentLaunchMainActivity = new Intent(this, MainActivity.class);
        startActivity(intentLaunchMainActivity);
    }

    @Override
    public void setupSplashHandler() {
        Log.d(TAG, "setupSplashHandler() called");
        mSplashHandler.postDelayed(mSplashRunnable, Constants.SPLASH_ANIMATION_DURATION);
    }
}