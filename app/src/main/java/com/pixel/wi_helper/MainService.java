package com.pixel.wi_helper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothCodecConfig;
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
    private int previousBattLevel = -100;

    public MainService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //System Broadcast receivers:
        helper = new BatteryTimer();
        IntentFilter btEventFilter = new IntentFilter();
        btEventFilter.addAction(BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED);
        btEventFilter.addAction(BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED);
        registerReceiver(btEventReceiver, btEventFilter);

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        IntentFilter toolbarActionFilter = new IntentFilter();
        toolbarActionFilter.addAction("SET_QUALITY");
        registerReceiver(toolbarActionReceiver, toolbarActionFilter);
        remoteViews = new RemoteViews(getPackageName(), R.layout.rtoolbar_layout);
        remoteViews.setOnClickPendingIntent(R.id.toolbarModes, PendingIntent.getBroadcast
                (this, 0,
                        new Intent().setAction("SET_QUALITY"), PendingIntent.FLAG_UPDATE_CURRENT));
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 0);
        remoteViews.setOnClickPendingIntent(R.id.toolbarIcon, pi);

        IntentFilter a2dpFilter = new IntentFilter();
        a2dpFilter.addAction("com.pixel.wi_helper.A2DP_READY");
        registerReceiver(a2dpReceiver, a2dpFilter);
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
                                if (BATTERY_TESTING){
                                    helper.mainTimer();
                                }
                                break;
                            case 1:
                                state = "not playing";
                                if (BATTERY_TESTING){
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
                        if (BATTERY_TESTING){
                            nextTimer(ib);
                        }

                        setToolbarBattery(ib);
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
                helper.stopTimer(previousBattLevel == 100);
                helper.startTimer(70);
                break;
            case 50:
                helper.stopTimer(previousBattLevel == 70);
                helper.startTimer(50);
                break;
            case 20:
                helper.stopTimer(previousBattLevel == 50);
                helper.startTimer(20);
                break;
            default:
                helper.stopTimer(previousBattLevel == 20);
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
                remoteViews.setTextViewText(R.id.toolbarText, btCtrl.getMyBluetoothDevice().getName());
                setToolbarBattery(btCtrl.getThisBatteryLevel());
                if (notification != null) {
                    notificationManager.notify(1, notification);
                } else {
                    mBinder.getBtConnected();
                }
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
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    class MBinder extends Binder {
        void testBind() {
            Log.d("binder", "ok");
            makeToast("Test toast");
        }

        int getBatt() {
            return btCtrl.getThisBatteryLevel();
        }

        boolean getBtReady() {
            btCtrl = new BluetoothController();
            return btCtrl.daoGetBtOn();
        }

        boolean getBtConnected() {
            if (btCtrl.daoGetBtConnected()) {
                setNotification();
                return true;
            } else {
                return false;
            }
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
                if (btCtrl.getThisBatteryLevel() == 100) {
                    testingConfig = btCtrl.getBtCurrentConfig();
                    helper.startTimer(100);
                    previousBattLevel = 100;
                    BATTERY_TESTING = true;
                } else {
                    makeToast("battery is not 100%");
                }
            }
        }

        String showStatus() {
            return "100%:" + helper.getDurationSec100()
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
            mBinder.getBtConnected();
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
