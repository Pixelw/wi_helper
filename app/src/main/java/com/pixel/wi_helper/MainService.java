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

    private RemoteViews remoteViews;
    private MBinder mBinder = new MBinder();
    private NotificationManager notificationManager;
    private Notification toolbarNotification;
    private BluetoothController btCtrl;
    private BatteryTimer helper;
    private boolean BATTERY_TESTING = false;
    private BluetoothCodecConfig testingConfig;
    private MainActivity.ABinder aBinder;
    private int pendingActivityAction = 0;
    private PendingIntent pi;
    private long codecLastChangeTime = 0;

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
        helper = new BatteryTimer();
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
        remoteViews = new RemoteViews(getPackageName(), R.layout.rtoolbar_layout);
        remoteViews.setOnClickPendingIntent(R.id.toolbarHQ, PendingIntent.getBroadcast
                (this, 0,
                        new Intent().setAction("com.pixel.wi_helper.hqOnClick"), PendingIntent.FLAG_UPDATE_CURRENT));
        remoteViews.setOnClickPendingIntent(R.id.toolbarPwrs, PendingIntent.getBroadcast
                (this, 0,
                        new Intent().setAction("com.pixel.wi_helper.pwsOnClick"), PendingIntent.FLAG_UPDATE_CURRENT));

        Intent intent = new Intent(this, MainActivity.class);
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        pi = PendingIntent.getActivity(this, 0, intent, 0);
        remoteViews.setOnClickPendingIntent(R.id.toolbarIcon, pi);

        IntentFilter a2dpFilter = new IntentFilter();
        a2dpFilter.addAction("com.pixel.wi_helper.A2DP_READY");
        registerReceiver(a2dpReceiver, a2dpFilter);
        initBluetooth();
        setNotification();
        makeToast("WI-Helper Start");
    }

    private void initBluetooth() {
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
                                    helper.mainTimer();
                                }
                                break;
                            case 1:
                                state = "not playing";
                                if (BATTERY_TESTING) {
                                    helper.pauseTimer();
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
                            nextTimer(ib);
                        }
                        //previousBattLevel = ib;
                        setToolbarBattery(ib);
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
                            remoteViews.setTextViewText(R.id.toolbarStat, configToDesc(codecStatus.getCodecConfig()));
                            if (toolbarNotification != null) {
                                notificationManager.notify(1, toolbarNotification);
                            } else {
                                setNotification();
                            }
                        }
                        codecLastChangeTime = SystemClock.elapsedRealtime();
                        break;
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                        Log.d("deviceDisCon", "onReceive: discon");
                        if (aBinder!=null){
                            aBinder.updateActivityConnStat(btCtrl.isConnected());
                        }
                        break;
                    case BluetoothDevice.ACTION_ACL_CONNECTED:
                        Log.d("deviceIsConn", "onReceive: conn");
                        if (aBinder != null) {
                            aBinder.updateActivityConnStat(btCtrl.isConnected());
//                            aBinder.updateActivityCodecStatus(btCtrl.getBtCurrentConfig());
//                            aBinder.updateActivityBattLevel(btCtrl.getThisBatteryLevel());
                            //seems attributes above would update themselves
                            aBinder.updateActivityDeviceName(btCtrl.getMyBluetoothDevice().getName());
                        }
                        break;
                    default:
                        break;
                }
            }
        }
    };

    private void nextTimer(int ib) {
        switch (ib) {
            case 100:
                break;
            case 70:
                helper.stopTimer(true);
                helper.startTimer(70);
                break;
            case 50:
                helper.stopTimer(true);
                helper.startTimer(50);
                break;
            case 20:
                helper.stopTimer(true);
                helper.startTimer(20);
                break;
            default:
                helper.stopTimer(true);
                helper.save(testingConfig);
                BATTERY_TESTING = false;
                break;

        }
    }

    private BroadcastReceiver toolbarActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case "com.pixel.wi_helper.hqOnClick":
                        btCtrl.setCodecByPreset(1);
                        switchToggle(1);
                        break;
                    case "com.pixel.wi_helper.pwsOnClick":
                        btCtrl.setCodecByPreset(2);
                        switchToggle(2);
                        break;
                    default:
                        break;

                }
            }
        }
    };
    //update infomation when a2dp is ready(fired by broadcast)
    private BroadcastReceiver a2dpReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothCodecConfig btConfig = btCtrl.getBtCurrentConfig();
            if (btConfig != null) {
                remoteViews.setTextViewText(R.id.toolbarStat, configToDesc(btConfig));
                if (btConfig.getSampleRate() == BluetoothCodecConfig.SAMPLE_RATE_44100) {
                    switchToggle(1);
                } else if (btConfig.getCodecType() == BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC) {
                    switchToggle(2);
                } else {
                    switchToggle(0);
                }
                String name = btCtrl.getMyBluetoothDevice().getName();
                remoteViews.setTextViewText(R.id.toolbarText, name);
                int battery = btCtrl.getThisBatteryLevel();
                setToolbarBattery(battery);
                if (aBinder != null) {
                    aBinder.updateActivityCodecStatus(btConfig);
                    aBinder.updateActivityDeviceName(name);
                    aBinder.updateActivityBattLevel(battery);
                }

                serviceIsRunning = true;
            }
        }
    };

    private void setNotification() {
        if (btCtrl.isConnected()) {
            NotificationChannel channel = new NotificationChannel("1",
                    "WI-Helper", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.enableVibration(false);
            channel.enableLights(false);

            notificationManager.createNotificationChannel(channel);
            toolbarNotification = new Notification.Builder(MainService.this, "1")
                    .setSmallIcon(R.drawable.ic_headset_black_24dp)
                    .setWhen(System.currentTimeMillis())
                    .setCustomContentView(remoteViews)
                    .build();
            startForeground(1, toolbarNotification);
        } else {
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

//        final Timer notifiCancelTimer = new Timer();
//        TimerTask timerTask = new TimerTask() {
//            @Override
//            public void run() {
//                if (!deviceIsConnected) {
//                    stopForeground(true);
//                }
//
//            }
//        };
//        notifiCancelTimer.schedule(timerTask, 10000);
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


//        int getBatt() {
//            return btCtrl.getThisBatteryLevel();
//        }

        void btIsNowOn() {
            initBluetooth();
        }

//        boolean deviceIsConnected() {
//            return btCtrl.isConnected();
//        }

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
            btCtrl.setCodecByPreset(i);
        }

//        int isPlaying() {
//            return btCtrl.isPlaying();
//        }

    }

    private void setToolbarBattery(int bat) {
        if (bat >= 0 && bat <= 100) {
            remoteViews.setTextViewText(R.id.toolbarBattStatus, bat + "%");
            if (bat >= 90) {
                remoteViews.setImageViewResource(R.id.toolbarBattMeter, R.drawable.ic_battery_full_black_24dp);
            } else if (bat >= 70) {
                remoteViews.setImageViewResource(R.id.toolbarBattMeter, R.drawable.ic_battery_80_black_24dp);
            } else if (bat >= 60) {
                remoteViews.setImageViewResource(R.id.toolbarBattMeter, R.drawable.ic_battery_60_black_24dp);
            } else if (bat >= 50) {
                remoteViews.setImageViewResource(R.id.toolbarBattMeter, R.drawable.ic_battery_50_black_24dp);
            } else if (bat >= 30) {
                remoteViews.setImageViewResource(R.id.toolbarBattMeter, R.drawable.ic_battery_30_black_24dp);
            } else if (bat >= 20) {
                remoteViews.setImageViewResource(R.id.toolbarBattMeter, R.drawable.ic_battery_20_black_24dp);
                int red = ContextCompat.getColor(getApplicationContext(), R.color.colorAccent);
                remoteViews.setTextColor(R.id.toolbarBattStatus, red);
            } else {
                int red = ContextCompat.getColor(getApplicationContext(), R.color.colorAccent);
                remoteViews.setTextColor(R.id.toolbarBattStatus, red);
                remoteViews.setImageViewResource(R.id.toolbarBattMeter, R.drawable.ic_battery_20_black_24dp);
            }
        } else {
            remoteViews.setTextViewText(R.id.toolbarBattStatus, "--");
            remoteViews.setImageViewResource(R.id.toolbarBattMeter, R.drawable.ic_battery_unknown_black_24dp);
        }
        if (toolbarNotification != null) {
            notificationManager.notify(1, toolbarNotification);
        } else {
            setNotification();
        }

    }

    private void switchToggle(int status) {
        switch (status) {
            case 1:
                remoteViews.setViewVisibility(R.id.toolbarPwrsBg, View.INVISIBLE);
                remoteViews.setViewVisibility(R.id.toolbarHQBg, View.VISIBLE);
                remoteViews.setTextViewText(R.id.toolbarHQ, "");
                remoteViews.setTextViewText(R.id.toolbarPwrs, getString(R.string.quality));
                remoteViews.setTextViewText(R.id.toolbarModesBg, "");
                break;
            case 2:
                remoteViews.setViewVisibility(R.id.toolbarPwrsBg, View.VISIBLE);
                remoteViews.setViewVisibility(R.id.toolbarHQBg, View.INVISIBLE);
                remoteViews.setTextViewText(R.id.toolbarHQ, getString(R.string.stable));
                remoteViews.setTextViewText(R.id.toolbarPwrs, "");
                remoteViews.setTextViewText(R.id.toolbarModesBg, "");
                break;
            default:
                remoteViews.setViewVisibility(R.id.toolbarPwrsBg, View.INVISIBLE);
                remoteViews.setViewVisibility(R.id.toolbarHQBg, View.INVISIBLE);
                remoteViews.setTextViewText(R.id.toolbarHQ, "");
                remoteViews.setTextViewText(R.id.toolbarPwrs, "");
                remoteViews.setTextViewText(R.id.toolbarModesBg, getString(R.string.other));
                break;
        }
        if (toolbarNotification != null) {
            notificationManager.notify(1, toolbarNotification);
        }
    }

    private String configToDesc(BluetoothCodecConfig config) {
        if (config != null) {
            String ldacq;
            String sampler;
            String bitd;
            String codec = config.getCodecName();
            switch ((int) config.getCodecSpecific1()) {
                case 1000:
                    ldacq = "High";
                    break;
                case 1001:
                    ldacq = "Med";
                    break;
                case 1002:
                    ldacq = "Low";
                    break;
                case 1003:
                    ldacq = "Auto";
                    break;
                default:
                    ldacq = "";
                    break;
            }

            switch (config.getSampleRate()) {
                case 1:
                    sampler = "44.1kHz";
                    break;
                case 2:
                    sampler = "48kHz";
                    break;
                case 4:
                    sampler = "88.2kHz";
                    break;
                case 8:
                    sampler = "96kHz";
                    break;
                default:
                    sampler = "";
                    break;
            }

            switch (config.getBitsPerSample()) {
                case 1:
                    bitd = "16bit";
                    break;
                case 2:
                    bitd = "24bit";
                    break;
                case 4:
                    bitd = "32bit";
                    break;
                default:
                    bitd = "";
                    break;
            }
            return codec + " " + sampler + " " + bitd + " " + ldacq;
        }
        return getString(R.string.unknownCodecConfig);
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
