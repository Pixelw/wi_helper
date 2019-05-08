package com.pixel.wi_helper;

import android.bluetooth.BluetoothCodecConfig;

import com.pixel.wi_helper.bean.HighBatteryAnalytics;
import com.pixel.wi_helper.bean.LowBatteryAnalytics;

import java.util.Timer;
import java.util.TimerTask;


public class BatteryTimer {
    private int durationSec100 = 0;
    private int durationSec70 = 0;
    private int durationSec50 = 0;
    private int durationSec20 = 0;
    private int sec = 0;
    private int target;
    private Timer timer;

    public int getSec() {
        return sec;
    }

    public int getTarget() {
        return target;
    }

    public int getDurationSec100() {
        return durationSec100;
    }

    public int getDurationSec70() {
        return durationSec70;
    }

    public int getDurationSec50() {
        return durationSec50;
    }

    public int getDurationSec20() {
        return durationSec20;
    }

    boolean isStarted() {
        return timer != null;
    }

    void resetTimer(int a) {
        switch (a) {
            case 100:
                durationSec100 = 0;
                break;
            case 70:
                durationSec70 = 0;
                break;
            case 50:
                durationSec50 = 0;
                break;
            case 20:
                durationSec20 = 0;
                break;
            default:
                durationSec50 = 0;
                durationSec20 = 0;
                durationSec70 = 0;
                durationSec100 = 0;
                break;
        }
    }

    void startTimer(int a){
        this.target = a;
        mainTimer();
    }

    void pauseTimer(){
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    void stopTimer(boolean save) {
        if (timer != null) {
            timer.cancel();
            timer = null;
            if (save){
                switch (target){
                    case 100:
                        durationSec100 = sec;
                        break;
                    case 70:
                        durationSec70 = sec;
                        break;
                    case 50:
                        durationSec50 = sec;
                        break;
                    case 20:
                        durationSec20 = sec;
                        break;
                    default:
                        break;
                }
            }
        }
        sec = 0;
    }

    //or resume
    void mainTimer(){
        timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                sec++;
            }
        };
        timer.schedule(timerTask,1000,1000);
    }

    void save(BluetoothCodecConfig config) {
        int total = durationSec20 + durationSec100 + durationSec70 + durationSec50;
        if (config != null && total != 0){
            int mode = 0;
            switch (config.getCodecName()) {
                case "AAC":
                case "SBC":
                    mode = 1;
                    break;
                case "aptX":
                case "aptX HD":
                case "LDAC":
                    mode = 2;
                    break;
                default:
                    break;
            }

            switch (mode) {
                case 1:
                    LowBatteryAnalytics analytics = new LowBatteryAnalytics();
                    analytics.setConfig(config.toString());
                    analytics.setDuration100(durationSec100);
                    analytics.setDuration70(durationSec70);
                    analytics.setDuration50(durationSec50);
                    analytics.setDuration20(durationSec20);
                    analytics.setTotal(total);
                    analytics.save();
                    break;
                case 2:
                    HighBatteryAnalytics analytics2 = new HighBatteryAnalytics();
                    analytics2.setConfig(config.toString());
                    analytics2.setDuration100(durationSec100);
                    analytics2.setDuration70(durationSec70);
                    analytics2.setDuration50(durationSec50);
                    analytics2.setDuration20(durationSec20);
                    analytics2.setTotal(total);
                    analytics2.save();
                    break;
                default:
                    break;
            }
        }
    }
}
