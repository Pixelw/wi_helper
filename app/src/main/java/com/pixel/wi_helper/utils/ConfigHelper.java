package com.pixel.wi_helper.utils;

import android.content.Context;
import android.content.SharedPreferences;

import static android.content.Context.MODE_PRIVATE;


public class ConfigHelper {
    private SharedPreferences sharedPreferences;
    public static final int NONE = 0;
    public static final int PRESET_HQ = 1;
    public static final int PRESET_PWR = 2;
    public static final int INDEX_CODEC = 1;
    public static final int INDEX_SAMPING = 2;
    public static final int INDEX_BITDEPTH = 3;


    public ConfigHelper(Context context) {
        sharedPreferences = context.getSharedPreferences("config", MODE_PRIVATE);
    }

    public void savePresetConfig(int preset, int codecIndex, int samplingIndex, int bitIndex) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        switch (preset) {
            case 1: //HQ
                editor.putInt("HqCodecIndex", codecIndex);
                editor.putInt("HqSamplingIndex", samplingIndex);
                editor.putInt("HqBitIndex", bitIndex);
                editor.apply();
                break;
            case 2: //powerSaving
                editor.putInt("PwrCodecIndex", codecIndex);
                editor.putInt("PwrSamplingIndex", samplingIndex);
                editor.putInt("PwrBitIndex", bitIndex);
                editor.apply();
                break;
            default:
                break;

        }


    }

    public int getConfigByKey(String key) {
        return sharedPreferences.getInt(key, 0);
    }

    public int getBtX2Int(String key) {
        int value = sharedPreferences.getInt(key, 0);
        switch (value) {
            case 3:
                return 4;
            case 4:
                return 8;
            default:
                return value;
        }
    }
}
