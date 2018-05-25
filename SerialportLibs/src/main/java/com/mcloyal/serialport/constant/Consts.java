package com.mcloyal.serialport.constant;

import android.os.Environment;

import java.io.File;

/**
 * 常量配置
 */
public class Consts {
    private final static String ROOT_PATH = Environment.getExternalStorageDirectory().getAbsolutePath();//存储卡根路径
    public static final String APP_ROOT = ROOT_PATH + File.separator + "sunncar" + File.separator;//SD卡数据文件跟目录
    public static final String ERROR_PATH = APP_ROOT + File.separator + "errors" + File.separator;//错误日志文件目录
    public static final String LOGS_PATH = APP_ROOT + File.separator + "logs" + File.separator;//运行日志文件目录
    public static final byte START_ = (byte) 0x51;//数据包头
}
