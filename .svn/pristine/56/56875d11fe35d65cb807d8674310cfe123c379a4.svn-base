package com.mcloyal.demo.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import com.mcloyal.demo.R;

/**
 * 应用程序启动页
 */
public class SplashActivity extends BaseActivity {
    private final static String TAG = SplashActivity.class.getSimpleName();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        super.setContentView(R.layout.activity_splash);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(SplashActivity.this,
                        MainActivity.class));
                finish();
            }
        }, 2000);
    }
}
