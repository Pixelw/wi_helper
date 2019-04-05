package com.pixel.wi_helper;

import com.pixel.wi_helper.bean.HighBatteryAnalytics;
import com.pixel.wi_helper.bean.LowBatteryAnalytics;

import java.util.Timer;
import java.util.TimerTask;


public class BatteryTimer {
    private int durationSec100 = 0;
    private int durationSec70 = 0;
    private int durationSec50 = 0;
    private int durationSec20 = 0;
    private Timer timer;

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

    void startTimer100() {
        timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                durationSec100++;
            }
        };
        timer.schedule(timerTask, 1000, 1000);
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
                break;
        }
    }

    void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    void startTimer70() {
        timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                durationSec70++;
            }
        };
        timer.schedule(timerTask, 1000, 1000);
    }


    void startTimer50() {
        timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                durationSec50++;
            }
        };
        timer.schedule(timerTask, 1000, 1000);
    }

    void startTimer20() {
        timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                durationSec20++;
            }
        };
        timer.schedule(timerTask, 1000, 1000);
    }

    void save(int mode, String config) {
        switch (mode) {
            case 1:
                LowBatteryAnalytics analytics = new LowBatteryAnalytics();
                analytics.setConfig(config);
                analytics.setDuration100(durationSec100);
                analytics.setDuration70(durationSec70);
                analytics.setDuration50(durationSec50);
                analytics.setDuration20(durationSec20);
                analytics.save();
                break;
            case 2:
                HighBatteryAnalytics analytics2 = new HighBatteryAnalytics();
                analytics2.setConfig(config);
                analytics2.setDuration100(durationSec100);
                analytics2.setDuration70(durationSec70);
                analytics2.setDuration50(durationSec50);
                analytics2.setDuration20(durationSec20);
                analytics2.save();
                break;
            default:
                break;
        }
    }
}
