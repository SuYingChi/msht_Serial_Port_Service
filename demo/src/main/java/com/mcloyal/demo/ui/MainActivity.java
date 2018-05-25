package com.mcloyal.demo.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.view.View;

import com.mcloyal.demo.R;
import com.mcloyal.demo.widget.ToastUtils;
import com.mcloyal.serialport.constant.Cmd;
import com.mcloyal.serialport.entity.Packet;
import com.mcloyal.serialport.exception.CRCException;
import com.mcloyal.serialport.exception.CmdTypeException;
import com.mcloyal.serialport.exception.FrameException;
import com.mcloyal.serialport.service.PortService;
import com.mcloyal.serialport.utils.ComServiceConnection;
import com.mcloyal.serialport.utils.PacketUtils;
import com.mcloyal.serialport.utils.logs.LogUtils;

import org.xutils.view.annotation.Event;
import org.xutils.x;

import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;

public class MainActivity extends BaseActivity implements Observer, Handler.Callback {
    private final static String TAG = MainActivity.class.getSimpleName();
    private PortService portService;
    private ComServiceConnection serviceConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        x.view().inject(this);
        serviceConnection = new ComServiceConnection(MainActivity.this, new ComServiceConnection.ConnectionCallBack() {
            @Override
            public void onServiceConnected(PortService service) {
                //此处给portService赋值有如下两种方式
                //portService=service;
                portService = serviceConnection.getService();
            }
        });
        bindService(new Intent(mContext, PortService.class), serviceConnection,
                BIND_AUTO_CREATE);
    }

    @Override
    public void update(Observable observable, Object data) {
        PortService.MyObservable myObservable = (PortService.MyObservable) observable;
        if (myObservable != null) {
            boolean skeyEnable = myObservable.isSkeyEnable();
            LogUtils.d(TAG, "第二功能键：" + (skeyEnable ? "启用" : "停用"));
            Packet packet1 = myObservable.getCom1Packet();
            if (packet1 != null) {
                LogUtils.d(TAG, "串口1数据更新：" + packet1.toString());
            }
            Packet packet2 = myObservable.getCom2Packet();
            if (packet2 != null) {
                LogUtils.d(TAG, "串口2数据更新：" + packet2.toString());
                if(Arrays.equals(packet2.getCmd(),new byte[]{0x01,0x07})){
                    //处理接收107类型业务通知
                }
            }
        }
    }

    /**
     * 点击事件
     *
     * @param v
     */
    @Event(value = {R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn31, R.id.btn32, R.id.btn33, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9}, type = View.OnClickListener.class)
    private void onViewClick(View v) {
        switch (v.getId()) {
            case R.id.btn1://AT卡检测
                if (portService != null) {
                    LogUtils.d(TAG, "AT卡检测");
                    portService.sendToCom2(Cmd.AT._CPIN_.getBytes());
                }
                break;
            case R.id.btn2://AT TCP连接
                if (portService != null) {
                    LogUtils.d(TAG, "AT TCP连接");
                    portService.sendToCom2(Cmd.AT._CIP_START_.getBytes());
                }
                break;
            case R.id.btn3://AT 透传
                if (portService != null) {
                    LogUtils.d(TAG, "AT 透传");
                    portService.sendToCom2(Cmd.AT._CIP_MODE_.getBytes());
                }
                break;
            case R.id.btn33://AT 信息查询
                if (portService != null) {
                    LogUtils.d(TAG, "AT 信号查询");
                    portService.sendToCom2(Cmd.AT._CSQ_.getBytes());
                }
                break;

            case R.id.btn31://一次性多条发送
                if (portService != null) {
                    LogUtils.d(TAG, "一次性发送设置透传，TCP连接");
                    portService.sendAtCmd(Cmd.AT._AT_ALL_.getBytes(),2000);
                }
                break;
            case R.id.btn6://网络模块断电重启
                if (portService != null) {
                    LogUtils.d(TAG, "网络模块断电重启");
                    portService.sendNetRestart();
                }
                break;
            case R.id.btn32://退出透传
                if (portService != null) {
                    LogUtils.d(TAG, "退出透传模式");
                    portService.sendToCom2(Cmd.AT._CIP_MODE_OUT_.getBytes());
                }
                break;
            case R.id.btn4://启动灌装
                if (portService != null) {
                    LogUtils.d(TAG, "启动灌装");
                    portService.sendToCom1(Cmd.ComCmd._START_);
                }
                break;
            case R.id.btn5://停止灌装,如果为刷卡时则停止按键无法点击
                if (portService != null) {
                    LogUtils.d(TAG, "停止灌装");
                    portService.sendToCom1(Cmd.ComCmd._END_);
                }
                break;
            case R.id.btn7://去读连接状态标记值
                ToastUtils.toastMessage(mContext, "连接状态：" + portService.isConnection());
                break;
            case R.id.btn8://结算业务
                if (portService != null) {
                    LogUtils.d(TAG, "结算业务");
                    try {
                        byte[] frame = new byte[]{0x00, 0x00, 0x00, 0x00};
                        byte[] type = new byte[]{0x02, 0x07};
                        byte[] packet = PacketUtils.makePackage(frame, type, null);
                        portService.sendToCom2(packet);
                    } catch (CRCException e) {
                        e.printStackTrace();
                    } catch (FrameException e) {
                        e.printStackTrace();
                    } catch (CmdTypeException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case R.id.btn9://套餐查询业务
                if (portService != null) {
                    LogUtils.d(TAG, "套餐查询业务");
                    try {
                        byte[] packet = PacketUtils.queryPackage(mContext);
                        portService.sendToCom2(packet);
                    } catch (CRCException e) {
                        e.printStackTrace();
                    } catch (FrameException e) {
                        e.printStackTrace();
                    } catch (CmdTypeException e) {
                        e.printStackTrace();
                    }
                }
                break;
            default:
                break;
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        return false;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceConnection != null && portService != null) {
            portService.removeObserver(this);
            unbindService(serviceConnection);
        }
    }
}
