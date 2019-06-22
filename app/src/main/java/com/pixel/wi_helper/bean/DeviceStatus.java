package com.pixel.wi_helper.bean;

import android.bluetooth.BluetoothCodecConfig;

public class DeviceStatus {



    private String name;
    private boolean isConnected;
    private int batteryLevel;
    private BluetoothCodecConfig codecConfig;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    public boolean isConnected() {
        return isConnected;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public BluetoothCodecConfig getCodecConfig() {
        return codecConfig;
    }

    public void setCodecConfig(BluetoothCodecConfig codecConfig) {
        this.codecConfig = codecConfig;
    }

    public String getCodecConfigDesc() {
        if (codecConfig != null) {
            String ldacq;
            String sampler;
            String bitd;
            String codec = codecConfig.getCodecName();
            switch ((int) codecConfig.getCodecSpecific1()) {
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

            switch (codecConfig.getSampleRate()) {
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

            switch (codecConfig.getBitsPerSample()) {
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
        return "unknown codec";
    }

}
