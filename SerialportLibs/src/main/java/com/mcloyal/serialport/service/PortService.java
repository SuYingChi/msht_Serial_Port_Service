package com.mcloyal.serialport.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;

import com.mcloyal.serialport.AppLibsContext;
import com.mcloyal.serialport.R;
import com.mcloyal.serialport.constant.Cmd;
import com.mcloyal.serialport.constant.Consts;
import com.mcloyal.serialport.entity.Packet;
import com.mcloyal.serialport.exception.AnalysisException;
import com.mcloyal.serialport.exception.CRCException;
import com.mcloyal.serialport.exception.CmdTypeException;
import com.mcloyal.serialport.exception.FrameException;
import com.mcloyal.serialport.utils.AnalysisUtils;
import com.mcloyal.serialport.utils.ByteUtils;
import com.mcloyal.serialport.utils.NumberUtil;
import com.mcloyal.serialport.utils.PacketUtils;
import com.mcloyal.serialport.utils.SpecialUtils;
import com.mcloyal.serialport.utils.StringUtils;
import com.mcloyal.serialport.utils.logs.LogUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import android_serialport_api.SerialPort;

/**
 * 串口通信常驻后台Service
 */
public class PortService extends Service {
    private final static String TAG = PortService.class.getSimpleName();
    private AppLibsContext appLibsContext = null;
    //主控制板为COM1，参数
    protected SerialPort mSerialPort1;
    protected static OutputStream mOutputStream1;
    private static InputStream mInputStream1;
    private ReadCom1DataThread mReadThread1;
    private ScheduledExecutorService scheduled1;
    private Vector<Byte> data1 = new Vector<Byte>();
    public static volatile int PACKET_LEN1 = 100;//63

    //通讯模块为COM2，参数
    protected SerialPort mSerialPort2;
    protected static OutputStream mOutputStream2;
    private static InputStream mInputStream2;
    private ReadCom2DataThread mReadThread2;
    private ScheduledExecutorService scheduled2;
    private  Vector<Byte> data2 = new Vector<Byte>();
    public static volatile int PACKET_LEN2 = 100;
   // private CountDownTimer Timer;
    //被观察者
    private MyObservable mObservable;

    //网络状态
    private volatile boolean  isConnection = false;//是否连接网络的标记 false表示未联网，true表示联网成功
    private volatile boolean isNetIng = false;//是否在联网中

    //常量值
    private final static int COM1_SERIAL = 1;//COM1常量标记
    private final static int COM2_SERIAL = 2;//COM2常量标记
   // private final static int COM1_COUNT = 4;//COM2常量标记
    private final static int RESTART = 3;//重启服务标记

    private final static int MAX_PGK_TIME = 5;//最大包数，根据改数值判断是否联网
    private static AtomicInteger  pgkTime = new AtomicInteger(0);;//发送包数据计数
    private final static int TCP_MAX_OUT_TIME = 10 * 1000;//TCP连接返回值等待时间

    private ScheduledExecutorService countScheduled;//倒计时线程，如果在指定时间内未收到com1口的105数据，则断电重启
    private static  AtomicInteger  count = new AtomicInteger(0);//
   /* private static int com104Count=0;
    private static int second=0;  //104接收到计时10秒
    private static boolean isStart=true;
    private ScheduledExecutorService secondScheduled;*/

    @Override
    public void onCreate() {
        super.onCreate();
        mObservable = new MyObservable();
        appLibsContext = (AppLibsContext) getApplication();
        //开启COM1接收线程  主板和Android板之前数据通信
        startReadCom1Thread();
        //启动COM1数据拼包线程
        scheduled1 = Executors.newSingleThreadScheduledExecutor();
        scheduled1.scheduleAtFixedRate(new ParserCom1ReceivedDataTask(), 0, 100,
                TimeUnit.MILLISECONDS);

        //开启COM2接收线程 ,通信模块和Android板之间通信
        startCom2Received();
        //启动COM2数据拼包线程
        scheduled2 = Executors.newSingleThreadScheduledExecutor();
        scheduled2.scheduleAtFixedRate(new PraserReadCom2Task(), 0, 100,
                TimeUnit.MILLISECONDS);

        countScheduled = Executors.newSingleThreadScheduledExecutor();
        final long maxCount = 5 * 60;//倒计时最大等大时长
        countScheduled.scheduleAtFixedRate(new Runnable() {
                                               @Override
                                               public void run() {
                                                  int incrementAndGet = count.incrementAndGet();
                                                   if (incrementAndGet > maxCount) {
                                                       count.set(0);
                                                       LogUtils.d(TAG, maxCount + "s 倒计时结束，执行断电重启");
                                                       handler.sendEmptyMessage(RESTART);
                                                   }
                                               }
                                           }, 0, 1,
                TimeUnit.SECONDS);
    }

    /*************************************  主控制板COM1数据接收发（开始）***********************************/
    /**
     * COM1轮询线程任务
     */
    private class ParserCom1ReceivedDataTask implements Runnable {
        public void run() {
            LogUtils.d(TAG, "PraserReadCom1Task线程");
            if (data1 != null && data1.size() >= PACKET_LEN1) {
                Byte[] data = data1.subList(0, PACKET_LEN1).toArray(new Byte[PACKET_LEN1]);
                if (data[0] != Consts.START_) {
                    data1.clear();
                    //data1 = new ArrayList();
                } else {
                    parserCom1Data(data, data.length);
                }
            }
        }
    }

    /**
     * 开启COM1口数据接收线程
     */
    private void startReadCom1Thread() {
        try {
            mSerialPort1 = appLibsContext.getSerialPort1();
            mOutputStream1 = mSerialPort1.getOutputStream();
            mInputStream1 = mSerialPort1.getInputStream();
            mReadThread1 = new ReadCom1DataThread();
            mReadThread1.start();
        } catch (SecurityException e) {
            LogUtils.d(TAG, getString(R.string.error_security));
        } catch (IOException e) {
            LogUtils.d(TAG, getString(R.string.error_unknown));
        } catch (InvalidParameterException e) {
            LogUtils.d(TAG, getString(R.string.error_configuration));
        }
    }

    /**
     * 读取COM1口数据
     */
    private class ReadCom1DataThread extends Thread {
        private volatile  boolean stopRunning =false;

        @Override
        public void run() {
            super.run();
            while (!stopRunning) {
                int size;
                try {
                    SystemClock.sleep(100);
                    byte[] buffer = new byte[PACKET_LEN1];
                    if (mInputStream1 == null) {
                        return;
                    }
                    size = mInputStream1.read(buffer);
                    if (size > 0) {//过滤出现一个单字节的数据包
                        byte[] data = Arrays.copyOfRange(buffer, 0, size);
                        LogUtils.d(TAG, "COM1接收到" + size + " 字节");
                        LogUtils.d(TAG, "COM1接收数据data==" + ByteUtils.byte2hex(data));
                        //有新的数据包
                        if (  data[0] == Consts.START_) {
                            data1.clear();
                            //data1 = new ArrayList<>();
                            if (size >= 3) {//包头+2字节长度
                                int len = NumberUtil.byte2ToUnsignedShort(new byte[]{data[1], data[2]});
                                PACKET_LEN1 = len + 1;//len不包含包头长度，1为包头的长度
                            }
                        }
                        for (byte aData : data) {
                            data1.add(aData);
                        }
                        //在此处进行包条件对比


                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
        }

        public void stopThread() {
            stopRunning =true;
        }
    }

    /**
     * COM1数据解析
     *
     * @param bf
     * @param size
     */
    protected void parserCom1Data(Byte[] bf, int size) {
        byte buffer[] = new byte[size];
        for (int i = 0; i < bf.length; i++) {
            buffer[i] = bf[i];
        }
        data1.clear();
        //data1 = new ArrayList<>();
        LogUtils.d(TAG, "COM1数据包完整 parserCom1Data data==" + ByteUtils.byte2hex(buffer));
        if (size >= PACKET_LEN1) {
            try {
                Packet packet = AnalysisUtils.analysisFrame(buffer, PACKET_LEN1);
                if (packet != null) {
                    //此处无需做CRC校验判断
                    byte[] cmd = packet.getCmd();
                    //不管是否断网都直接转发固定105
                    byte[] frame=packet.getFrame();
                    if (Arrays.equals(cmd, new byte[]{0x01, 0x05})) {
                        LogUtils.d(TAG, "COM1接收到105，重置倒计时操作");
                        count.set(0); //重置倒计时计时
                       // sendToCom1(Cmd.ComCmd._NONET_HEARTBEAT_);
                        responseCom105(frame);
                    }else if (Arrays.equals(cmd, new byte[]{0x01, 0x04})){ //COM1接收到104 直接回复
                        responseCom104(frame);
                    }
                    if (isConnection) {//如果在联网的情况下直接把数据转发到TCP平台
                        LogUtils.d(TAG, "上送到服务器" + ByteUtils.byte2hex(buffer));
                        if (Arrays.equals(cmd, new byte[]{0x01, 0x05})) {
                            int incrementAndGet= pgkTime.incrementAndGet();
                            LogUtils.e(TAG, "数据包发送次数：" + incrementAndGet);
                            if (incrementAndGet > MAX_PGK_TIME) {//如果超过最大包数判断
                                LogUtils.d(TAG, "已经超过最大包数阈值，执行断电重启");
                                handler.sendEmptyMessage(RESTART);
                            }
                        }
                        sendToCom2(buffer);
                    } else {

                        //添加注释
                        /*if (Arrays.equals(cmd, new byte[]{0x01, 0x04})) {//回复
                            sendToCom1(Cmd.ComCmd._NONET_AT104_);
                        }*/

                        //做一次网络连接操作
                        LogUtils.d(TAG, "离线状态，判断是否需要进行断电重启...");
                        handler.sendEmptyMessage(RESTART);
                    }
                    Message msg = Message.obtain();
                    msg.obj = packet;
                    msg.what = COM1_SERIAL;
                    handler.sendMessage(msg);
                }
            } catch (AnalysisException e) {
                e.printStackTrace();
            }
        }
    }

    private void responseCom105(byte[] frame) {
        try {
            //封装数据返回205到主控板
            byte repy[] = PacketUtils.makePackage(frame, new byte[]{0x02, 0x05}, null);
            if (repy != null && repy.length > 0) {
                LogUtils.d(TAG, "回复205指令类型");
                sendToCom1(repy);
            }
        } catch (CRCException e) {
            e.printStackTrace();
        } catch (FrameException e) {
            e.printStackTrace();
        } catch (CmdTypeException e) {
            e.printStackTrace();
        }
    }
    private void responseCom104(byte[] frame) {
        try {
            //封装数据返回204到主控板
            byte repy[] = PacketUtils.makePackage(frame, new byte[]{0x02, 0x04}, null);
            if (repy != null && repy.length > 0) {
                LogUtils.d(TAG, "回复204指令类型");
                sendToCom1(repy);
            }
        } catch (CRCException e) {
            e.printStackTrace();
        } catch (FrameException e) {
            e.printStackTrace();
        } catch (CmdTypeException e) {
            e.printStackTrace();
        }
    }

    /**
     * 向com1发送操作指令
     *
     * @param cmd
     */
    public static void sendToCom1(byte[] cmd) {
        if (mOutputStream1 != null && cmd != null) {
            try {
                LogUtils.d(TAG, "向COM1口发送：" + ByteUtils.byte2hex(cmd));
                mOutputStream1.write(cmd);
                mOutputStream1.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    /*************************************主控制板COM1数据接收发（结束）***********************************/


    /*************************************通信模块COM2数据接收发（开始）***********************************/
    /**
     * COM2轮询线程任务
     */
    private class PraserReadCom2Task implements Runnable {
        public void run() {
            if (data2 != null && data2.size() > 3) {
                int size = data2.size();
                if (size >= PACKET_LEN2) {
                    Byte[] data = data2.subList(0, PACKET_LEN2).toArray(new Byte[PACKET_LEN2]);
                    if (data[0] != Consts.START_) {
                        data2.clear();
                        //data2 = new ArrayList();
                    } else {
                        onDataCom2Received(data, data.length);
                    }
                }
            }
        }
    }

    /**
     * 开启COM2口数据接收线程
     */
    private void startCom2Received() {
        try {
            mSerialPort2 = appLibsContext.getSerialPort2();
            mOutputStream2 = mSerialPort2.getOutputStream();
            mInputStream2 = mSerialPort2.getInputStream();
            //启动接收数据线程,根据isConnect的状态值进行数据读取判断
            mReadThread2 = new ReadCom2DataThread();
            mReadThread2.start();
        } catch (SecurityException e) {
            LogUtils.d(TAG, getString(R.string.error_security));
        } catch (IOException e) {
            LogUtils.d(TAG, getString(R.string.error_unknown));
        } catch (InvalidParameterException e) {
            LogUtils.d(TAG, getString(R.string.error_configuration));
        }
    }

    /**
     * 读取COM2口数据
     * 100毫秒
     */
    private class ReadCom2DataThread extends Thread {
        private volatile boolean stopRunning =false;

        @Override
        public void run() {
            super.run();
            while (!stopRunning) {
                SystemClock.sleep(100);
                if (isConnection) {
                    int size;
                    try {
                        byte[] buffer = new byte[PACKET_LEN2];
                        if (mInputStream2 == null) {
                            LogUtils.d(TAG, "mInputStream2  is null");
                            return;
                        }
                        size = mInputStream2.read(buffer);
                        if (size > 0) {
                            //过滤掉出现一个字节的0x00或者一个字节的0xff
                            byte[] data = Arrays.copyOfRange(buffer, 0, size);
                            String context = new String(data, "UTF-8").trim().toUpperCase();
                            LogUtils.d(TAG, "COM2接收到" + size + " 字节");
                            LogUtils.d(TAG, "COM2字符串：" + context);
                            LogUtils.d(TAG, "COM2接收数据data==" + ByteUtils.byte2hex(data));
                            LogUtils.d(TAG, "COM2接收状态下：isConnection==" + isConnection);
                            if (context.contains("CLOSED") || context.contains("DISCONNECT")) {
                                LogUtils.d(TAG, "接收到CLOSED或者DISCONNECT，判断是否需要进行断电重启...");
                                handler.sendEmptyMessage(RESTART);
                            }
                            if (isConnection) {//网络连接成功的情况下进行数据拼接
                                if ( data[0] == Consts.START_) {
                                    data2.clear();
                                    //data2 = new ArrayList<>();
                                    if (size >= 3) {//包头+2字节长度
                                        int len = NumberUtil.byte2ToUnsignedShort(new byte[]{data[1], data[2]});
                                        PACKET_LEN2 = len + 1;//len不包含包头长度，1为包头的长度
                                    }
                                }
                                for (int i = 0; i < size; i++) {
                                    data2.add(data[i]);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        return;
                    }
                }
            }
        }

        public void stopThread() {
            stopRunning =true;
        }
    }

    /**
     * 发送网络模块重启指令
     */
    public void sendNetRestartCmd() {
        //向主控板发送网络模块断电重启
        if (!isNetIng) {
            isConnection = false;
            pgkTime.set(0);//重置计数
            sendToCom1(Cmd.ComCmd._RESTART_NET_);//发送断电重启
            LogUtils.d(TAG, "开始启动AT操作指令发送");
            isNetIng = true;//网络连接流程标记，正在联网中
            atScheduled = Executors.newSingleThreadScheduledExecutor();
            atScheduled.schedule(new ForAtTask(), 10, TimeUnit.SECONDS);//延时10s
        } else {
            LogUtils.d(TAG, "AT操作指令已经正在运行...");
        }
    }


    private ScheduledExecutorService atScheduled;//AT指令操作线程

/*    private ForAtTaskHandler forAtTaskHandler;

    private static class ForAtTaskHandler extends Handler{
        ForAtTaskHandler(Looper looper){
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            LogUtils.d(TAG,"ForAtTaskHandler 收到At线程的消息");
            String atcontext = (String) msg.obj;
            //接收到处理结果后开始处理结果.............
        }
    }*/


    /**
     * AT指令操作，包含信号检测，透传设置，TCP连接
     */
    private class ForAtTask implements Runnable {
        public void run() {
            //forAtTaskHandler = new ForAtTaskHandler(Looper.myLooper());
            LogUtils.d(TAG, "ForAtTask开始执行");
            boolean isCsq = false;//默认未达到可以进行透传指令的设置操作
            int csqmax = 5;//信号检测最大次数
            //检测AT信号状态
            for (int i = 1; i <= csqmax; i++) {
                LogUtils.e(TAG, "信号检测：开始第" + (i) + "次信号检测");
                //ForAtTask的执行线程会阻塞到sendAtCmd返回结果
                String atcontext = sendAtCmd(Cmd.AT._CIP_CSQ_.getBytes(), 2 * 1000);//延时2s读取管道数据
                LogUtils.d(TAG, "信号检测返回：" + atcontext);
                if (!TextUtils.isEmpty(atcontext) && atcontext.contains("+CSQ") && atcontext.contains("OK")) {
                    //信号检测的返回 +CSQ
//                    LogUtils.d(TAG, "进入信号检测的返回:" + atcontext);
                    LogUtils.d(TAG, "读取信号强度成功");
                    int csq = StringUtils.getcqs(atcontext);
                    LogUtils.d(TAG, "信号强度==" + csq);
                    if (csq >= 10) {//信号强度超过10，进行网络连接
                        isCsq = true;
                        break;
                    }
                }
                SystemClock.sleep(1000);//等待1s继续执行
            }
            if (isCsq) {
                //如果信号检测符合继续进行下一步的条件，则执行设置透传指令发送
                String modeContext = sendAtCmd(Cmd.AT._CIP_MODE_.getBytes(), 1000);
                LogUtils.d(TAG, "透传返回内容：" + modeContext);
                if (!TextUtils.isEmpty(modeContext) && modeContext.contains("OK")) {//设置透传成功
                    String tcpContext = sendAtCmd(Cmd.AT._CIP_START_.getBytes(), TCP_MAX_OUT_TIME);
                    LogUtils.d(TAG, "TCP返回内容：" + tcpContext);
                    if (!TextUtils.isEmpty(tcpContext) && tcpContext.contains("CONNECT") && !tcpContext.contains("DIS") && !tcpContext.contains("FAIL")) {
                        isNetIng = false;//设置网络连接流程结束标记
                        LogUtils.d(TAG, "网络连接成功,启动COM2数据接收服务");
                        pgkTime .set(0);//重置计数
                        isConnection = true;
                    } else {
                        LogUtils.d(TAG, "判断为TCP连接不成功，执断电重启");
                        isNetIng = false;
                        atScheduled.shutdown();
                        handler.sendEmptyMessage(RESTART);//断电重启
                    }
                } else {
                    //透传设置不成功，断电重启
                    LogUtils.d(TAG, csqmax + "透传设置不成功，执断电重启");
                    isNetIng = false;
                    atScheduled.shutdown();
                    handler.sendEmptyMessage(RESTART);//断电重启
                }
            } else {
                LogUtils.d(TAG, csqmax + "次信号检测，均未满足信号大于10，执断电重启");
                isNetIng = false;
                atScheduled.shutdown();
                handler.sendEmptyMessage(RESTART);//断电重启
            }
        }
    }

    /**
     * 网络状态标记字段
     *
     * @return
     */
    public boolean isConnection() {
        return isConnection;
    }

    /**
     * 发送AT控制指令
     *
     * @param atcmd AT控制指令
     * @return 返回下发指令后返回的字符串信息
     */
    public String sendAtCmd(final byte[] atcmd, final long time) {
        final ExecutorService exec = Executors.newFixedThreadPool(1);
        Callable<String> call = new Callable<String>() {
            public String call() throws Exception {//开始执行耗时操作,在子线程
                try {
                    if (mOutputStream2 != null && atcmd != null) {
                        mOutputStream2.write(atcmd);
                        mOutputStream2.flush();
                    }
                    SystemClock.sleep(time);
                    byte[] buffer = new byte[1024];
                    int size = mInputStream2.read(buffer);
                    LogUtils.d(TAG, "AT返回数据size==" + size);
                    if (size > 1) {
                        byte[] data = Arrays.copyOfRange(buffer, 0, size);
                        return new String(data, "UTF-8").trim().toUpperCase();

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        };
        try {
            Future<String> future = exec.submit(call);
            // 关闭线程池
            exec.shutdown();
            //该方法在ForAtTask里执行，所以此时是在ForAtTask的执行线程里了，get阻塞ForAtTask的执行线程，
            return future.get(time + 2000, TimeUnit.MILLISECONDS); //任务处理超时时间在等待的基础上加2000
         /*   Message message = new Message();
            message.obj = obj;
            forAtTaskHandler.sendMessage(message);*/

        } catch (TimeoutException ex) {
            LogUtils.d(TAG, "获取AT指令处理超时，处理失败！");
        } catch (Exception e) {
            LogUtils.d(TAG, "获取AT指令返回错误，处理失败！");
        }
        return null;
    }

    /**
     * COM2数据解析
     *
     * @param bf
     * @param size
     */
    protected void onDataCom2Received(Byte[] bf, int size) {
        byte buffer[] = new byte[size];
        for (int i = 0; i < bf.length; i++) {
            buffer[i] = bf[i];
        }
        data2.clear();
        //data2 = new ArrayList<>();
        LogUtils.d(TAG, "COM2数据包完整 onDataCom2Received data==" + ByteUtils.byte2hex(buffer));
        if (size >= PACKET_LEN2) {
            try {
                Packet packet = AnalysisUtils.analysisFrame(buffer, PACKET_LEN2);
                if (packet != null) {
                    //判断服务器是否返回205，如果存在则重置计数
                    if (Arrays.equals(packet.getCmd(), new byte[]{0x02, 0x05})) {
                        LogUtils.e(TAG, "接收到服务器205指令，重置pgkTime计数为0");
                        pgkTime.set(0);
                    }
                    //服务器下发107包时则启动第二功能键
                    if (Arrays.equals(packet.getCmd(), new byte[]{0x01, 0x07})) {
                        try {
                            //封装数据返回207到服务器
                            byte[] frame = packet.getFrame();
                            byte repy[] = PacketUtils.makePackage(frame, new byte[]{0x02, 0x07}, null);
                            if (repy != null && repy.length > 0) {
                                LogUtils.d(TAG, "回复207指令类型");
                                sendToCom2(repy);
                            }
                        } catch (CRCException e) {
                            e.printStackTrace();
                        } catch (FrameException e) {
                            e.printStackTrace();
                        } catch (CmdTypeException e) {
                            e.printStackTrace();
                        }
                    } else {
                        //COM2接收到的数据直接转发
                        //服务器返回的205不再转发到com1口
                        if (!(Arrays.equals(packet.getCmd(), new byte[]{0x02, 0x05})||
                                Arrays.equals(packet.getCmd(), new byte[]{0x02, 0x04}))) {
                            sendToCom1(buffer);
                        }
                    }
                    Message msg = Message.obtain();
                    msg.obj = packet;
                    msg.what = COM2_SERIAL;
                    handler.sendMessage(msg);
                }
            } catch (AnalysisException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 向com2发送操作指令
     *
     * @param cmd
     */
    public void sendToCom2(byte[] cmd) {
        if (mOutputStream2 != null && cmd != null) {
            try {
                LogUtils.d(TAG, "向COM2口发送：" + ByteUtils.byte2hex(cmd));
                mOutputStream2.write(cmd);
                mOutputStream2.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /*************************************通信模块COM2数据接收发（结束）***********************************/
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
       if (scheduled1 != null&&!scheduled1.isShutdown()) {
           scheduled1.shutdown();
        }
       if (scheduled2 != null&&!scheduled2.isShutdown()) {
           scheduled2.shutdown();
       }
       if (atScheduled != null&&!atScheduled.isShutdown()) {
            atScheduled.shutdown();
        }
        if(countScheduled != null&&!countScheduled.isShutdown()){
            countScheduled.shutdown();
        }
        mReadThread1.stopThread();
        mReadThread2.stopThread();
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return new LocalBinder();
    }

    public final class LocalBinder extends Binder {
        public PortService getService() {
            return PortService.this;
        }
    }

    /************************************观察者模式通知数据更新（开始）*************************************/
    public class MyObservable extends Observable {
        private Packet com1Packet;
        private Packet com2Packet;
        private boolean skeyEnable;//第二功能按键的启用和停用标记，true表示启用，false表示停止

        public boolean isSkeyEnable() {
            return skeyEnable;
        }

        public Packet getCom1Packet() {
            return com1Packet;
        }

        public void setCom1Packet(Packet com1Packet) {
            if (com1Packet != null) {
                this.com2Packet = null;//重置上一次更新的数据
                this.com1Packet = com1Packet;
                setChanged();
                notifyObservers();
                if (Arrays.equals(com1Packet.getCmd(), new byte[]{0x01, 0x04})) {
                    //判断第二功能按键是否启用
                    try {
                        skeyEnable = SpecialUtils.sKeyEnable(com1Packet);
                    } catch (CmdTypeException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        public Packet getCom2Packet() {
            return com2Packet;
        }

        public void setCom2Packet(Packet com2Packet) {
            if (com2Packet != null) {
                this.com1Packet = null;//重置上一次更新的数据
                this.com2Packet = com2Packet;
                setChanged();
                notifyObservers();
                //服务器下发107包时则启动第二功能键
                if (Arrays.equals(com2Packet.getCmd(), new byte[]{0x01, 0x07})) {
                    skeyEnable = true;
                }
            }
        }
    }

    /**
     * 添加观察者
     *
     * @param observer
     */
    public void addObserver(Observer observer) {
        if (mObservable == null) {
            mObservable = new MyObservable();
        }
        mObservable.addObserver(observer);
    }

    /**
     * 移除观察者
     *
     * @param observer
     */
    public void removeObserver(Observer observer) {
        if (mObservable != null) {
            mObservable.deleteObserver(observer);
        }
    }

/*    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case COM1_SERIAL:
                    Packet packet1 = (Packet) msg.obj;
                    if (mObservable != null && packet1 != null) {
                        mObservable.setCom1Packet(packet1);
                    }
                    break;
                case COM2_SERIAL:
                    Packet packet2 = (Packet) msg.obj;
                    if (mObservable != null && packet2 != null) {
                        mObservable.setCom2Packet(packet2);
                    }
                    break;
                case RESTART://重启服务
//                    LogUtils.d(TAG, "handler执行断电重启");
                    sendNetRestartCmd();
                    break;
            }
        }
    };*/
    private Handler handler = new PortServiceHandler(PortService.this);
    private static class PortServiceHandler extends Handler{

        private  WeakReference<PortService> weakPortService;
        private  PortService portService;

        PortServiceHandler(PortService portService){
            this.portService = portService;
            weakPortService= new WeakReference<PortService>(portService);
        }
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case COM1_SERIAL:
                    Packet packet1 = (Packet) msg.obj;
                    if (weakPortService.get().mObservable != null && packet1 != null) {
                        weakPortService.get().mObservable.setCom1Packet(packet1);
                    }
                    break;
                case COM2_SERIAL:
                    Packet packet2 = (Packet) msg.obj;
                    if (weakPortService.get().mObservable != null && packet2 != null) {
                        weakPortService.get().mObservable.setCom2Packet(packet2);
                    }
                    break;
                case RESTART://重启服务
//                    LogUtils.d(TAG, "handler执行断电重启");
                    weakPortService.get().sendNetRestartCmd();
                    break;
            }
        }
    }


    /*private Handler Counthandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case COM1_COUNT:
                    startCountDownTime(1);
                    break;
            }
        }
    };
    private void startCountDownTime(final long time) {
        Timer=new CountDownTimer(time*12000,1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                second++;
            }
            @Override
            public void onFinish() {
                second=0;
                isStart=true;
            }
        };
        Timer.start();

    }*/
    /************************************观察者模式通知数据更新（结束）*************************************/
}
