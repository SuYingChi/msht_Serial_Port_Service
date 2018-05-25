package com.mcloyal.demo;

import android.content.Intent;
import android.content.IntentFilter;

import com.mcloyal.demo.receiver.PortReceiver;
import com.mcloyal.serialport.AppLibsContext;
import com.mcloyal.serialport.utils.SpecialUtils;
import com.mcloyal.serialport.utils.logs.LogUtils;

import java.util.ArrayList;

/**
 * Created by rain on 2017/11/6.
 */
public class AppContext extends AppLibsContext {
    @Override
    public void onCreate() {
        super.onCreate();
        /**
         * sdk日志输出控制开关
         * @param context 上下文对象
         * @param e       E级别日志是否输出
         * @param i       I级别日志是否输出
         * @param w       W级别日志是否输出
         * @param d       D级别日志是否输出
         * @param file    是否将日志输出到文件内
         * @param crash   是否记录运行时异常日志
         */
        LogUtils.initLogs(this, false, false, false, false, false, true);

        initPortBroadcast();
    }

    /**
     * 初始化广播事件以及后台服务事件监听串口接收程序
     */
    public void initPortBroadcast() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_TICK);
        PortReceiver receiver = new PortReceiver();
        registerReceiver(receiver, filter);
    }

    @Override
    public void onTerminate() {
        // 程序终止的时候执行
        super.onTerminate();
    }

    @Override
    public void onLowMemory() {
        // 低内存的时候执行
        super.onLowMemory();
    }

    @Override
    public void onTrimMemory(int level) {
        // 程序在内存清理的时候执行
        super.onTrimMemory(level);
    }

}
