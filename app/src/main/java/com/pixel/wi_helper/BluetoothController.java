package com.pixel.wi_helper;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothCodecStatus;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.util.Log;

import java.util.Set;

import static android.security.KeyStore.getApplicationContext;

class BluetoothController {
    private BluetoothA2dp mBluetoothA2dp;
    private final Object mBluetoothA2dpLock = new Object();
    private BluetoothAdapter mBluetoothAdapter;
    private boolean a2dpIsOn;

    BluetoothDevice getMyBluetoothDevice() {
        return myBluetoothDevice;
    }

    private BluetoothDevice myBluetoothDevice;
    private String myDeviceAddress = "null";

    int bluetoothStatus() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter.isEnabled()) {
            getBoundedBtDevices();
            if (myDeviceAddress.equals("null")) {
                return -1;//device not found
            } else {
                myBluetoothDevice = mBluetoothAdapter.getRemoteDevice(myDeviceAddress);
                initBluetoothClasses();
                return 1;//ok
            }
        } else {
            return 0;//bluetooth is off
        }
    }

    boolean daoIsConnected() {
        return myBluetoothDevice.isConnected();
    }

    int getThisBatteryLevel() {
        if (myBluetoothDevice != null) {
            int a = myBluetoothDevice.getBatteryLevel();
            Log.d("C:getBattery?", "getThisBatteryLevel: " + a);
            return a;
        } else {
            return -2;
        }

    }

    int isPlaying() {
        if (myBluetoothDevice.isConnected()) {
            if (mBluetoothA2dp.isA2dpPlaying(myBluetoothDevice)) {
                return 2;//playing
            } else {
                return 1;//not playing
            }
        } else {
            return 0;//not connected
        }
    }

    private void initBluetoothClasses() {
        //checked, bt is on..
        BluetoothProfile.ServiceListener mListener = new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (profile == BluetoothProfile.A2DP) {
                    mBluetoothA2dp = (BluetoothA2dp) proxy;
                    if (mBluetoothA2dp == null) {
                        Log.d("a2dp", "null??");
                    } else {
                        Intent intent = new Intent("com.pixel.wi_helper.A2DP_READY");
                        getApplicationContext().sendBroadcast(intent);
                        a2dpIsOn = true;
                        Log.d("a2dp", "a2dp is ok");
                    }
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {
                if (profile == BluetoothProfile.A2DP) {
                    mBluetoothA2dp = null;
                }
            }
        };
        mBluetoothAdapter.getProfileProxy(getApplicationContext(), mListener, BluetoothProfile.A2DP);
    }

    private void getBoundedBtDevices() {
        Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
        if (devices.size() > 0) {
            for (BluetoothDevice bluetoothDevice : devices) {
                //Log.d("WIH_FOUND", "设备：" + bluetoothDevice.getName() + " " + bluetoothDevice.getAddress());
                if (bluetoothDevice.getName().contains("WI-H700")) {
                    myDeviceAddress = bluetoothDevice.getAddress();
                    Log.d("WIH_FOUND", "found WI-H700");
                    break;
                }
            }
        }
    }

    BluetoothCodecConfig getBtCurrentConfig() {
        BluetoothCodecStatus codecStatus;
        BluetoothCodecConfig codecConfig;
        synchronized (mBluetoothA2dpLock) {
            if (mBluetoothA2dp != null) {
                codecStatus = mBluetoothA2dp.getCodecStatus();
                if (codecStatus != null) {
                    codecConfig = codecStatus.getCodecConfig();
                    Log.d("codecConfig", codecConfig.toString());
                    return codecConfig;
                }
            }
        }
        return null;
    }

    void setBtCodecConfig(BluetoothCodecConfig config) {
        synchronized (mBluetoothA2dpLock) {
            if (mBluetoothA2dp != null) {
                mBluetoothA2dp.setCodecConfigPreference(config);
            }
        }
    }

    void setCodecByPreset(int preset) {
        //default
        int codecType = BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC;
        int codecPriority = BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT;
        int sampleRate = BluetoothCodecConfig.SAMPLE_RATE_48000;
        int bitsPerSample = BluetoothCodecConfig.BITS_PER_SAMPLE_16;
        int channelMode = BluetoothCodecConfig.CHANNEL_MODE_STEREO;
        long codecSpecific1Value = 0;
        long codecSpecific2Value = 0;
        long codecSpecific3Value = 0;
        long codecSpecific4Value = 0;

        switch (preset) {
            case 1: //hq music
                codecType = BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC;
                codecPriority = BluetoothCodecConfig.CODEC_PRIORITY_HIGHEST;
                sampleRate = BluetoothCodecConfig.SAMPLE_RATE_44100;
                bitsPerSample = BluetoothCodecConfig.BITS_PER_SAMPLE_16;
                channelMode = BluetoothCodecConfig.CHANNEL_MODE_STEREO;
                codecSpecific1Value = 1003;
                codecSpecific2Value = 0;
                codecSpecific3Value = 0;
                codecSpecific4Value = 0;
                break;
            case 2://video or power saving
                codecType = BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC;
                codecPriority = BluetoothCodecConfig.CODEC_PRIORITY_HIGHEST;
                sampleRate = BluetoothCodecConfig.SAMPLE_RATE_48000;
                bitsPerSample = BluetoothCodecConfig.BITS_PER_SAMPLE_16;
                channelMode = BluetoothCodecConfig.CHANNEL_MODE_STEREO;
                codecSpecific1Value = 0;
                codecSpecific2Value = 0;
                codecSpecific3Value = 0;
                codecSpecific4Value = 0;
                break;
            default:
                break;
        }

        BluetoothCodecConfig config =
                new BluetoothCodecConfig(codecType, codecPriority, sampleRate, bitsPerSample,
                        channelMode, codecSpecific1Value, codecSpecific2Value, codecSpecific3Value,
                        codecSpecific4Value);

        setBtCodecConfig(config);

    }

    void closeProfile() {
        if (a2dpIsOn){
            mBluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, mBluetoothA2dp);
        }
    }

}
