package com.mcloyal.demo.ui;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.mcloyal.demo.widget.LoadingDialog;
import com.mcloyal.serialport.AppLibsContext;
import com.mcloyal.serialport.AppManager;
import com.mcloyal.serialport.AppPreferences;

/**
 * Created by huangzhong on 2015/9/17.
 */
public abstract class BaseActivity extends AppCompatActivity {
    public Context mContext;
    public AppLibsContext appLibsContext;
    public AppPreferences appPreferences;
    public LoadingDialog loadingdialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        mContext = this;
        appLibsContext = (AppLibsContext) this.getApplicationContext();
        appPreferences = AppPreferences.getInstance(appLibsContext);
        AppManager.getAppManager().addActivity(this);
        loadingdialog = new LoadingDialog(mContext);
    }

    protected void onResume() {
        super.onResume();
    }

    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppManager.getAppManager().finishActivity(this);
        if (loadingdialog != null && loadingdialog.isShowing()) {
            loadingdialog.dismiss();
        }
    }
}
