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
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

public class MainService extends Service {

    private RemoteViews remoteViews;
    private MBinder mBinder = new MBinder();
    private NotificationManager notificationManager;
    private Notification notification;
    private BluetoothController btCtrl;
    private BatteryTimer helper;
    private boolean BATTERY_TESTING = false;
    private BluetoothCodecConfig testingConfig;
    private MainActivity.ABinder aBinder;
    private boolean pendingTurnBtON = true;
    private boolean deviceIsConnected = false;
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
        btEventFilter.addAction(BluetoothA2dp.ACTION_CODEC_CONFIG_CHANGED);
        registerReceiver(btEventReceiver, btEventFilter);

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        IntentFilter toolbarActionFilter = new IntentFilter();
        toolbarActionFilter.addAction("com.pixel.wi_helper.SET_QUALITY");
        registerReceiver(toolbarActionReceiver, toolbarActionFilter);
        remoteViews = new RemoteViews(getPackageName(), R.layout.rtoolbar_layout);
        remoteViews.setOnClickPendingIntent(R.id.toolbarModes, PendingIntent.getBroadcast
                (this, 0,
                        new Intent().setAction("com.pixel.wi_helper.SET_QUALITY"), PendingIntent.FLAG_UPDATE_CURRENT));
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 0);
        remoteViews.setOnClickPendingIntent(R.id.toolbarIcon, pi);

        IntentFilter a2dpFilter = new IntentFilter();
        a2dpFilter.addAction("com.pixel.wi_helper.A2DP_READY");
        registerReceiver(a2dpReceiver, a2dpFilter);
        initBluetooth();
    }

    private void initBluetooth(){

        if (btCtrl.daoGetBtOn()){
            pendingTurnBtON = false;
            if (btCtrl.daoGetBtConnected()){
                setNotification();
                deviceIsConnected = true;
            }else {
                deviceIsConnected  = false;
            }
        }else{// bluetooth is off
            if (aBinder == null){ //first start, abinder is not instant yet.
                pendingTurnBtON = true; //a pending flag
            }else {
                aBinder.turnBluetoothOn();
            }

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
                        aBinder.updateActivityBattLevel(ib);
                        break;

                    case BluetoothA2dp.ACTION_CODEC_CONFIG_CHANGED:
                        BluetoothCodecStatus bsss = intent.getParcelableExtra(BluetoothCodecStatus.EXTRA_CODEC_STATUS);
                        Log.d("codecChanged", bsss.getCodecConfig().toString());
                        aBinder.updateActivityCodecStatus(bsss.getCodecConfig());
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
            BluetoothCodecConfig config = btCtrl.getBtCurrentConfig();
            if (config != null) {
                if (config.getCodecName().equals("LDAC")) {
                    remoteViews.setImageViewResource(R.id.toolbarModes, R.drawable.ic_battery_std_black_24dp);
                    notificationManager.notify(1, notification);
                    btCtrl.setCodecByMode(2);
                } else if (config.getSampleRate() != 1) {
                    remoteViews.setImageViewResource(R.id.toolbarModes, R.drawable.ic_high_quality_black_24dp);
                    notificationManager.notify(1, notification);
                    btCtrl.setCodecByMode(1);
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
                if (btConfig.getSampleRate() == 1) {
                    remoteViews.setImageViewResource(R.id.toolbarModes, R.drawable.ic_high_quality_black_24dp);
                } else if (btConfig.getCodecName().equals("AAC")) {
                    remoteViews.setImageViewResource(R.id.toolbarModes, R.drawable.ic_battery_std_black_24dp);
                }
                String name =btCtrl.getMyBluetoothDevice().getName();
                remoteViews.setTextViewText(R.id.toolbarText, name);
                int battery = btCtrl.getThisBatteryLevel();
                setToolbarBattery(battery);
                if (notification != null) {
                    notificationManager.notify(1, notification);
                } else {
                    setNotification();
                }
                aBinder.updateActivityCodecStatus(btConfig);
                aBinder.updateActivityDeviceName(name);
                aBinder.updateActivityBattLevel(battery);
                serviceIsRunning = true;
            }
        }
    };

    private void setNotification() {
        NotificationChannel channel = new NotificationChannel("1", "WI-Helper", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.enableVibration(false);
        channel.enableLights(false);
        assert notificationManager != null;
        notificationManager.createNotificationChannel(channel);
        notification = new Notification.Builder(MainService.this, "1")
                .setSmallIcon(R.drawable.ic_headset_black_24dp)
                .setWhen(System.currentTimeMillis())
                .setCustomContentView(remoteViews)
                .build();
        startForeground(1, notification);
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
            if (pendingTurnBtON){
                aBinder.turnBluetoothOn();
            }
            aBinder.updateActivityConnStat(deviceIsConnected);
            if (serviceIsRunning){ //refresh when resume
                aBinder.updateActivityCodecStatus(btCtrl.getBtCurrentConfig());
                aBinder.updateActivityBattLevel(btCtrl.getThisBatteryLevel());
                aBinder.updateActivityDeviceName(btCtrl.getMyBluetoothDevice().getName());
            }
        }


        int getBatt() {
            return btCtrl.getThisBatteryLevel();
        }

        void btIsNowOn() {
            initBluetooth();
        }

        ContentValues getCurrentCodec() {
            BluetoothCodecConfig config = btCtrl.getBtCurrentConfig();
            ContentValues codecValues = new ContentValues();
            if (config != null) {
                codecValues.put("codecType", config.getCodecName());
                codecValues.put("samplingRate", config.getSampleRate());
                codecValues.put("bitDepth", config.getBitsPerSample());
                codecValues.put("LDAC_quality", config.getCodecSpecific1());
                return codecValues;
            }
            return null;
        }

        void startBattTest() {
            if (helper.isStarted()) {
                makeToast("timer is running");
            } else {
                    int batLevel =  btCtrl.getThisBatteryLevel();
                    testingConfig = btCtrl.getBtCurrentConfig();
                    helper.startTimer(batLevel);
                   // previousBattLevel = batLevel;
                    BATTERY_TESTING = true;
                    makeToast("timer start at "+batLevel);
            }
        }

        String showStatus() {
            return "current Timer: " + helper.getTarget() + "%:" + helper.getSec() +
                    "s\nsaved value:\n100%:" + helper.getDurationSec100()
                    + "s\n70%:" + helper.getDurationSec70()
                    + "s\n50%:" + helper.getDurationSec50()
                    + "s\n20%:" + helper.getDurationSec20() + "s";
        }

        void setCodec(BluetoothCodecConfig config) {
            btCtrl.setBtCodecConfig(config);
        }

        int isPlaying() {
            return btCtrl.isPlaying();
        }

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
        if (notification != null) {
            notificationManager.notify(1, notification);
        } else {
            setNotification();
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
