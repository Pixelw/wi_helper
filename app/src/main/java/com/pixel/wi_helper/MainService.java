package com.pixel.wi_helper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothCodecStatus;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.pixel.wi_helper.bean.DeviceStatus;

import java.util.Timer;
import java.util.TimerTask;


/*
以下是业余注释：
服务的启动顺序笔记（防止自己再乱掉）：

onCreate():这里一般由startService启动
初始化蓝牙控制器btCtrl；
计时器辅助类helper；
创建意图过滤器btEventListener，和接收器组成以接收所有蓝牙事件；
创建远程视图，工具栏用，还有工具栏的操作监听；
/初始化蓝牙过程，会设置蓝牙是否开启和设备是否已经连接的标志，供后面判断

onstartcommand(),onBind()都没有代码执行，目的是利用oC只在第一次启动的时候处理启动逻辑，避免重复；


*/


public class MainService extends Service {

    private RemoteViews rvToolbar;
    private MBinder mBinder = new MBinder();
    private NotificationManager notificationManager;
    private Notification toolbarNotification;
    private BluetoothController btCtrl;
//    private BatteryTimer helper;
    private boolean BATTERY_TESTING = false;
    private BluetoothCodecConfig testingConfig;
    private MainActivity.ABinder aBinder;
    private int pendingActivityAction = 0;
    private PendingIntent pi;
    private long codecLastChangeTime = 0;
    private DeviceStatus savedDeviceStatus;

    //    private boolean deviceIsConnected = false;
    private boolean serviceIsRunning = false;
    // private int previousBattLevel = -100;

    public MainService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //System Broadcast receivers:
        btCtrl = new BluetoothController();
//        helper = new BatteryTimer();
        IntentFilter btEventFilter = new IntentFilter();
        btEventFilter.addAction(BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED);
        btEventFilter.addAction(BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED);
        btEventFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        btEventFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        btEventFilter.addAction(BluetoothA2dp.ACTION_CODEC_CONFIG_CHANGED);
        registerReceiver(btEventReceiver, btEventFilter);

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        IntentFilter toolbarActionFilter = new IntentFilter();
        toolbarActionFilter.addAction("com.pixel.wi_helper.hqOnClick");
        toolbarActionFilter.addAction("com.pixel.wi_helper.pwsOnClick");
        registerReceiver(toolbarActionReceiver, toolbarActionFilter);
        rvToolbar = new RemoteViews(getPackageName(), R.layout.rtoolbar_layout);
        rvToolbar.setOnClickPendingIntent(R.id.toolbarHQ, PendingIntent.getBroadcast
                (this, 0,
                        new Intent().setAction("com.pixel.wi_helper.hqOnClick"), PendingIntent.FLAG_UPDATE_CURRENT));
        rvToolbar.setOnClickPendingIntent(R.id.toolbarPwrs, PendingIntent.getBroadcast
                (this, 0,
                        new Intent().setAction("com.pixel.wi_helper.pwsOnClick"), PendingIntent.FLAG_UPDATE_CURRENT));

        Intent intent = new Intent(this, MainActivity.class);
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        pi = PendingIntent.getActivity(this, 0, intent, 0);
        rvToolbar.setOnClickPendingIntent(R.id.toolbarIcon, pi);

        IntentFilter a2dpFilter = new IntentFilter();
        a2dpFilter.addAction("com.pixel.wi_helper.A2DP_READY");
        registerReceiver(a2dpReceiver, a2dpFilter);
        savedDeviceStatus = new DeviceStatus();
        initBluetooth();
        setNotification();
        makeToast("WI-Helper Start");
    }

    private void initBluetooth() {
        /*
            启动时从几种情况中判断蓝牙的状态（BluetoothController.bluetoothStatus）
            1: ok
            0: 蓝牙关闭
            -1：没有找到配对的设备
             pendingActivityAction: 在活动未启动时预定操作

         */
        switch (btCtrl.bluetoothStatus()) {
            case 1:
                pendingActivityAction = 0;
//                deviceIsConnected = btCtrl.isConnected();
                break;
            case 0:
                if (aBinder == null) { //first start, abinder is not instant yet.
                    pendingActivityAction = 1; //a pending flag
                } else {
                    aBinder.turnBluetoothOn();
                }
                break;
            case -1:
                // device not found
                if (aBinder == null) {
                    pendingActivityAction = 2;
                } else {
                    aBinder.targetDeviceNotFound();
                }

                break;
            default:
                break;
        }
    }

    private BroadcastReceiver btEventReceiver = new BroadcastReceiver() {

        int ib = 0;

        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                String action = intent.getAction();
                assert action != null;
                switch (action) {
                    case BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED:
                        String state = "";
                        switch (btCtrl.isPlaying()) {
                            case 2:
                                state = "playing";
                                if (BATTERY_TESTING) {
//                                    helper.mainTimer();
                                }
                                break;
                            case 1:
                                state = "not playing";
                                if (BATTERY_TESTING) {
//                                    helper.pauseTimer();
                                }
                                break;
                            default:
                                break;
                        }
                        Log.d("playingStatChange", state);
                        break;

                    case BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED:
                        ib = intent.getIntExtra(BluetoothDevice.EXTRA_BATTERY_LEVEL, -39);
                        Log.d("battChanged", "now:" + ib);
                        if (BATTERY_TESTING) {
//                            nextTimer(ib);
                        }
                        //previousBattLevel = ib;
                        savedDeviceStatus.setBatteryLevel(ib);
                        updateToolbar();
                        if (aBinder != null) {
                            aBinder.updateActivityBattLevel(ib);
                        }

                        break;

                    case BluetoothA2dp.ACTION_CODEC_CONFIG_CHANGED:

                        long timeBroadcastReceived = SystemClock.elapsedRealtime();
                        Log.d("codecChanged", String.format(
                                "changed at %d, %d between",
                                timeBroadcastReceived, timeBroadcastReceived - codecLastChangeTime));

                        if (timeBroadcastReceived - codecLastChangeTime > 1000) {
                            BluetoothCodecStatus codecStatus = intent.getParcelableExtra(BluetoothCodecStatus.EXTRA_CODEC_STATUS);
                            if (aBinder != null) {
                                aBinder.updateActivityCodecStatus(codecStatus.getCodecConfig());
                            }
                            savedDeviceStatus.setCodecConfig(codecStatus.getCodecConfig());
                            updateToolbar();
                        }
                        codecLastChangeTime = SystemClock.elapsedRealtime();
                        break;
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                        BluetoothDevice intentDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        Log.d("deviceDisConn", intentDevice.getName());
                        if (intentDevice.getAddress().equals(btCtrl.getMyDeviceAddress())){
                            rvToolbar.setTextViewText(R.id.toolbarTimeRemain, getString(R.string.disconnected));
                            updateToolbar();
                            if (aBinder != null) {
                                aBinder.updateActivityConnStat(btCtrl.isConnected());
                            }
                        }
                        break;
                    case BluetoothDevice.ACTION_ACL_CONNECTED:
                        BluetoothDevice intent2Device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        Log.d("deviceIsConn", intent2Device.getName());

                        if (intent2Device.getAddress().equals(btCtrl.getMyDeviceAddress())){
                            savedDeviceStatus.setConnected(true);
                            updateToolbar();
                            rvToolbar.setTextViewText(R.id.toolbarTimeRemain, "");
                            if (aBinder != null) {
                                aBinder.updateActivityConnStat(btCtrl.isConnected());
                                aBinder.updateActivityDeviceName(btCtrl.getMyBluetoothDevice().getName());
                            }
                        }

                        break;
                    default:
                        break;
                }
            }
        }
    };


//    private void nextTimer(int ib) {
//        switch (ib) {
//            case 100:
//                break;
//            case 70:
//                helper.stopTimer(true);
//                helper.startTimer(70);
//                break;
//            case 50:
//                helper.stopTimer(true);
//                helper.startTimer(50);
//                break;
//            case 20:
//                helper.stopTimer(true);
//                helper.startTimer(20);
//                break;
//            default:
//                helper.stopTimer(true);
//                helper.save(testingConfig);
//                BATTERY_TESTING = false;
//                break;
//
//        }
//    }

    private BroadcastReceiver toolbarActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case "com.pixel.wi_helper.hqOnClick":
                        btCtrl.setCodecByPreset(1,getApplicationContext());
                        switchToggle(1, true);
                        break;
                    case "com.pixel.wi_helper.pwsOnClick":
                        btCtrl.setCodecByPreset(2,getApplicationContext());
                        switchToggle(2, true);
                        break;
                    default:
                        break;

                }
            }
        }
    };
    //update information when a2dp is ready(fired by broadcast)
    private BroadcastReceiver a2dpReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            savedDeviceStatus.setName(btCtrl.getMyBluetoothDevice().getName());
            savedDeviceStatus.setCodecConfig(btCtrl.getBtCurrentConfig());
            savedDeviceStatus.setBatteryLevel(btCtrl.getThisBatteryLevel());
            updateToolbar();
            Log.d("a2dpReceiver", "startUp refresh");
            if (aBinder != null) {
                aBinder.updateActivityCodecStatus(savedDeviceStatus.getCodecConfig());
                aBinder.updateActivityDeviceName(savedDeviceStatus.getName());
                aBinder.updateActivityBattLevel(savedDeviceStatus.getBatteryLevel());
            }
            serviceIsRunning = true;
        }
    };

    private void setNotification() {
        if (btCtrl.isConnected()) {
            //蓝牙已连接时，直接弹出工具栏
            NotificationChannel channel = new NotificationChannel("1",
                    "WI-Helper", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.enableVibration(false);
            channel.enableLights(false);

            notificationManager.createNotificationChannel(channel);
            //TODO 把图标改成codec首字母
            toolbarNotification = new Notification.Builder(MainService.this, "1")
                    .setSmallIcon(R.drawable.ic_headset_black_24dp)
                    .setWhen(System.currentTimeMillis())
                    .setCustomContentView(rvToolbar)
                    .build();
            startForeground(1, toolbarNotification);
        } else {

            //尚未链接到耳机时，弹出后台保活的通知栏
            NotificationChannel channel0 = new NotificationChannel("3",
                    "WI-Helper Background Holder", NotificationManager.IMPORTANCE_DEFAULT);
            channel0.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel0.enableVibration(false);
            channel0.enableLights(false);

            notificationManager.createNotificationChannel(channel0);
            Notification notification0 = new Notification.Builder(MainService.this, "3")
                    .setContentTitle("Wi-Helper is running background")
                    .setContentText(getString(R.string.tap_to_open))
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.mipmap.ic_launcher_round)
                    .setContentIntent(pi)
                    .build();
            startForeground(3, notification0);
        }


    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        btCtrl.closeProfile();
        unregisterReceiver(toolbarActionReceiver);
        unregisterReceiver(btEventReceiver);
        unregisterReceiver(a2dpReceiver);
        Log.d("exit", "onDestroy: service");
        makeToast("serviceStopped");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    class MBinder extends Binder {
        void castBinder(MainActivity.ABinder binder) {
            aBinder = binder;
            switch (pendingActivityAction) {
                case 1:
                    aBinder.turnBluetoothOn();
                    break;
                case 2:
                    aBinder.targetDeviceNotFound();
                    break;
                default:
                    break;
            }

            aBinder.updateActivityConnStat(btCtrl.isConnected());
            if (serviceIsRunning) { //refresh when resume
                aBinder.updateActivityCodecStatus(btCtrl.getBtCurrentConfig());
                aBinder.updateActivityBattLevel(btCtrl.getThisBatteryLevel());
                aBinder.updateActivityDeviceName(btCtrl.getMyBluetoothDevice().getName());
            }
        }


        void btIsNowOn() {
            initBluetooth();
        }


//        ContentValues getCurrentCodec() {
//            BluetoothCodecConfig config = btCtrl.getBtCurrentConfig();
//            ContentValues codecValues = new ContentValues();
//            if (config != null) {
//                codecValues.put("codecType", config.getCodecName());
//                codecValues.put("samplingRate", config.getSampleRate());
//                codecValues.put("bitDepth", config.getBitsPerSample());
//                codecValues.put("LDAC_quality", config.getCodecSpecific1());
//                return codecValues;
//            }
//            return null;
//        }

//        void startBattTest() {
//            if (helper.isStarted()) {
//                makeToast("timer is running");
//            } else {
//                int batLevel = btCtrl.getThisBatteryLevel();
//                testingConfig = btCtrl.getBtCurrentConfig();
//                helper.startTimer(batLevel);
//                // previousBattLevel = batLevel;
//                BATTERY_TESTING = true;
//                makeToast("timer start at " + batLevel);
//            }
//        }

//        String showStatus() {
//            return "current Timer: " + helper.getTarget() + "%:" + helper.getSec() +
//                    "s\nsaved value:\n100%:" + helper.getDurationSec100()
//                    + "s\n70%:" + helper.getDurationSec70()
//                    + "s\n50%:" + helper.getDurationSec50()
//                    + "s\n20%:" + helper.getDurationSec20() + "s";
//        }

        void setCodec(BluetoothCodecConfig config) {
            btCtrl.setBtCodecConfig(config);
        }

        void setCodecByPreset(int i) {
            btCtrl.setCodecByPreset(i,getApplicationContext());
        }

//        int isPlaying() {
//            return btCtrl.isPlaying();
//        }

    }

    private void updateToolbar() {
        rvToolbar.setTextViewText(R.id.toolbarText, savedDeviceStatus.getName());
        setToolbarBattery(savedDeviceStatus.getBatteryLevel());
        if (savedDeviceStatus.getCodecConfig() != null) {
            rvToolbar.setTextViewText(R.id.toolbarStat, savedDeviceStatus.getCodecConfigDesc());
            if (savedDeviceStatus.getCodecConfig().getSampleRate() == BluetoothCodecConfig.SAMPLE_RATE_44100) {
                switchToggle(1, false);
            } else if (savedDeviceStatus.getCodecConfig().getCodecType() == BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC) {
                switchToggle(2, false);
            } else {
                switchToggle(0, false);
            }
        }

        if (toolbarNotification != null) {
            notificationManager.notify(1, toolbarNotification);
        } else {
            setNotification();
        }

    }

    private void setToolbarBattery(int bat) {
        if (bat >= 0 && bat <= 100) {
            rvToolbar.setTextViewText(R.id.toolbarBattStatus, bat + "%");
            if (bat >= 90) {
                rvToolbar.setImageViewResource(R.id.toolbarBattMeter, R.drawable.ic_battery_full_black_24dp);
            } else if (bat >= 70) {
                rvToolbar.setImageViewResource(R.id.toolbarBattMeter, R.drawable.ic_battery_80_black_24dp);
            } else if (bat >= 60) {
                rvToolbar.setImageViewResource(R.id.toolbarBattMeter, R.drawable.ic_battery_60_black_24dp);
            } else if (bat >= 50) {
                rvToolbar.setImageViewResource(R.id.toolbarBattMeter, R.drawable.ic_battery_50_black_24dp);
            } else if (bat >= 30) {
                rvToolbar.setImageViewResource(R.id.toolbarBattMeter, R.drawable.ic_battery_30_black_24dp);
            } else if (bat >= 20) {
                rvToolbar.setImageViewResource(R.id.toolbarBattMeter, R.drawable.ic_battery_20_black_24dp);
                int red = ContextCompat.getColor(getApplicationContext(), R.color.colorAccent);
                rvToolbar.setTextColor(R.id.toolbarBattStatus, red);
            } else {
                int red = ContextCompat.getColor(getApplicationContext(), R.color.colorAccent);
                rvToolbar.setTextColor(R.id.toolbarBattStatus, red);
                rvToolbar.setImageViewResource(R.id.toolbarBattMeter, R.drawable.ic_battery_20_black_24dp);
            }
        } else {
            rvToolbar.setTextViewText(R.id.toolbarBattStatus, "");
            rvToolbar.setImageViewResource(R.id.toolbarBattMeter, R.drawable.ic_battery_unknown_black_24dp);
        }

    }

    private void switchToggle(int status, boolean refreshNow) {
        switch (status) {
            case 1:
                rvToolbar.setViewVisibility(R.id.toolbarPwrsBg, View.INVISIBLE);
                rvToolbar.setViewVisibility(R.id.toolbarHQBg, View.VISIBLE);
                rvToolbar.setTextViewText(R.id.toolbarHQ, "");
                rvToolbar.setTextViewText(R.id.toolbarPwrs, getString(R.string.quality));
                rvToolbar.setTextViewText(R.id.toolbarModesBg, "");
                break;
            case 2:
                rvToolbar.setViewVisibility(R.id.toolbarPwrsBg, View.VISIBLE);
                rvToolbar.setViewVisibility(R.id.toolbarHQBg, View.INVISIBLE);
                rvToolbar.setTextViewText(R.id.toolbarHQ, getString(R.string.stable));
                rvToolbar.setTextViewText(R.id.toolbarPwrs, "");
                rvToolbar.setTextViewText(R.id.toolbarModesBg, "");
                break;
            default:
                rvToolbar.setViewVisibility(R.id.toolbarPwrsBg, View.INVISIBLE);
                rvToolbar.setViewVisibility(R.id.toolbarHQBg, View.INVISIBLE);
                rvToolbar.setTextViewText(R.id.toolbarHQ, "");
                rvToolbar.setTextViewText(R.id.toolbarPwrs, "");
                rvToolbar.setTextViewText(R.id.toolbarModesBg, getString(R.string.select));
                break;
        }

        if (toolbarNotification != null && refreshNow) {
            notificationManager.notify(1, toolbarNotification);
        }
    }

    private void makeToast(final String msg) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            }
        });
    }

}
