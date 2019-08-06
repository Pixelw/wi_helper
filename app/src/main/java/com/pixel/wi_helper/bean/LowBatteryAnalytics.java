package com.pixel.wi_helper.bean;

//disabled
public class LowBatteryAnalytics {

    private int id;
    private String config;
    private int duration100;
    private int duration70;
    private int duration50;
    private int duration20;

    private int total;

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
    public int getDuration100() {
        return duration100;
    }

    public void setDuration100(int duration100) {
        this.duration100 = duration100;
    }

    public int getDuration70() {
        return duration70;
    }

    public void setDuration70(int duration70) {
        this.duration70 = duration70;
    }

    public int getDuration50() {
        return duration50;
    }

    public void setDuration50(int duration50) {
        this.duration50 = duration50;
    }

    public int getDuration20() {
        return duration20;
    }

    public void setDuration20(int duration20) {
        this.duration20 = duration20;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }
}