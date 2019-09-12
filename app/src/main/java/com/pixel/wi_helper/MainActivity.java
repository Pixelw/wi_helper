package com.pixel.wi_helper;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothCodecConfig;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import com.pixel.wi_helper.utils.ConfigHelper;

public class MainActivity extends AppCompatActivity {

    private TextView tvDeviceName;
    private TextView tvConnectStat;
    private TextView tvBatteryLvl;
    private TextView tvOptions;

    private ImageView imgBattery;
    private ImageView imgCodecLogo;
    private TextView tvCodecName;
    private TextView tvCodecStat1;
    private TextView tvCodecStat2;
    private Button btnSetCodec;
    private int bluetoothDenied = 0;
    private MainService.MBinder serviceBinder;
    private boolean exitingHelper = false;
    private boolean spinnerChangesIsPending = false;
    private int selectCodecIndex = 0;
    private int selectSamplingIndex = 1;
    private int selectBitIndex = 1;
    private Spinner codecsSpinner;
    private Spinner samplingSpinner;
    private Spinner bitSpinner;
    private Vibrator mVibrator;
    int configuringPreset = 0;
    private ConfigHelper savedPreferences;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_scroll);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exitHelper();
            }
        });

        tvDeviceName = findViewById(R.id.tv_device_name);
        tvConnectStat = findViewById(R.id.tv_connect_status);
        tvBatteryLvl = findViewById(R.id.ac_battLv);
        tvOptions = findViewById(R.id.tv_options);
        imgBattery = findViewById(R.id.ac_battMeter);
        imgCodecLogo = findViewById(R.id.ac_codecLogo);
        tvCodecName = findViewById(R.id.ac_codecText);
        tvCodecStat1 = findViewById(R.id.ac_codecStatusLine1);
        tvCodecStat2 = findViewById(R.id.ac_codecStatusLine2);
        final ImageView imgSetHQ = findViewById(R.id.ac_codecHq);
        ImageView imgSetPowersaving = findViewById(R.id.ac_codecBatt);
        btnSetCodec = findViewById(R.id.btnSetCodec);
        codecsSpinner = findViewById(R.id.ac_codecsSpinner);
        samplingSpinner = findViewById(R.id.ac_sampleRateSpinner);
        bitSpinner = findViewById(R.id.ac_bitsSpinner);
        final AlphaAnimation blinkAnimation = new AlphaAnimation(0.0f,1.0f);
        blinkAnimation.setDuration(500);
        blinkAnimation.setRepeatCount(Animation.INFINITE);
        blinkAnimation.setRepeatMode(Animation.RESTART);
        savedPreferences = new ConfigHelper(this);
        mVibrator = (Vibrator)getApplication().getSystemService(VIBRATOR_SERVICE);


        Intent startIntent = new Intent(this, MainService.class);
        startService(startIntent);
        final Intent bindIntent = new Intent(getApplicationContext(), MainService.class);
        bindService(bindIntent, connection, BIND_AUTO_CREATE);

        btnSetCodec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (configuringPreset != 0){
                    savedPreferences.savePresetConfig(configuringPreset,selectCodecIndex,
                            selectSamplingIndex,selectBitIndex);
                } else {
                    BluetoothCodecConfig codecConfig = new BluetoothCodecConfig(selectCodecIndex,
                            BluetoothCodecConfig.CODEC_PRIORITY_HIGHEST,selectSamplingIndex,
                            selectBitIndex,BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                            1003,0,0,0);
                    serviceBinder.setCodec(codecConfig);
                    spinnerChangesIsPending = false;
                    btnSetCodec.setVisibility(View.INVISIBLE);
                }
            }
        });
        imgSetHQ.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serviceBinder.setCodecByPreset(1);
            }
        });

        imgSetHQ.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                //TODO 修改spinner下标
                imgSetHQ.setAnimation(blinkAnimation);
                blinkAnimation.start();
                mVibrator.vibrate(VibrationEffect.createOneShot(20,128));
                configuringPreset = 1;
                codecsSpinner.setSelection(savedPreferences.getPresetConfig("HqCodecIndex"));
                samplingSpinner.setSelection(savedPreferences.getPresetConfig("HqSamplingIndex"));
                bitSpinner.setSelection(savedPreferences.getPresetConfig("HqBitIndex"));


                return false;
            }
        });

        imgSetPowersaving.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serviceBinder.setCodecByPreset(2);
            }
        });

        imgSetPowersaving.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {

                return false;
            }
        });
        codecsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (selectCodecIndex != position) spinnersChanged();
                selectCodecIndex = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        samplingSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (selectSamplingIndex != position) spinnersChanged();
                selectSamplingIndex = position == 0 ? 1 :
                        position == 1 ? 2 :
                                position == 2 ? 4 :
                                        position == 3 ? 8 : 0;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        bitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (selectBitIndex != position) spinnersChanged();
                selectBitIndex = position == 0 ? 1 :
                        position == 1 ? 2 :
                                position == 2 ? 4 : 0;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void configPreset() {
        tvOptions.setText(R.string.presets);


    }

    private void spinnersChanged() {
        if (!spinnerChangesIsPending){
            btnSetCodec.setVisibility(View.VISIBLE);
            spinnerChangesIsPending = true;
        }
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            serviceBinder = (MainService.MBinder) service;
            ABinder aBinder = new ABinder();
            serviceBinder.castBinder(aBinder);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d("onServiceDisconnected", "onServiceDisconnected: ");
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(connection);
        Log.d("exit", "onDestroy: activity");
        if (exitingHelper) {
            System.exit(0);
        }
    }

    private void updateDashboard(BluetoothCodecConfig config) {
        if (config != null) {
            codecsSpinner.setSelection(config.getCodecType(),true);
            switch (config.getCodecType()) {
                case 0:
                    tvCodecName.setText(R.string.sbc_ac);
                    imgCodecLogo.setImageDrawable(null);
                    break;
                case 1:
                    tvCodecName.setText(R.string.aac_ac);
                    imgCodecLogo.setImageDrawable(null);
                    break;
                case 2:
                    tvCodecName.setText(R.string.aptx);
                    imgCodecLogo.setImageDrawable(null);
                    break;
                case 3:
                    tvCodecName.setText(R.string.aptxHD);
                    imgCodecLogo.setImageDrawable(null);
                    break;
                case 4:
                    imgCodecLogo.setImageResource(R.mipmap.ldac);
                    tvCodecName.setText("");
                    break;
                default:
                    break;

            }
            String ldacq;
            String sampler;
            String bitd;
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
                    ldacq = "Def";
                    break;
            }

            switch (config.getSampleRate()) {
                case 1:
                    samplingSpinner.setSelection(0);
                    sampler = "44.1kHz";
                    break;
                case 2:
                    samplingSpinner.setSelection(1);
                    sampler = "48kHz";
                    break;
                case 4:
                    samplingSpinner.setSelection(2);
                    sampler = "88.2kHz";
                    break;
                case 8:
                    samplingSpinner.setSelection(3);
                    sampler = "96kHz";
                    break;
                default:
                    sampler = "";
                    break;
            }

            switch (config.getBitsPerSample()) {
                case 1:
                    bitSpinner.setSelection(0);
                    bitd = "16bit";
                    break;
                case 2:
                    bitSpinner.setSelection(1);
                    bitd = "24bit";
                    break;
                case 4:
                    bitSpinner.setSelection(2);
                    bitd = "32bit";
                    break;
                default:
                    bitd = "";
                    break;
            }
            String str = bitd + " " + ldacq;
            tvCodecStat1.setText(sampler);
            tvCodecStat2.setText(str);
        }
    }

    private void updateDashboard(int bat) {
        if (bat >= 0 && bat <= 100) {
            String str = bat + "%";
            tvBatteryLvl.setText(str);
            if (bat >= 90) {
                imgBattery.setImageResource(R.drawable.ic_battery_full_black_24dp);
            } else if (bat >= 70) {
                imgBattery.setImageResource(R.drawable.ic_battery_80_black_24dp);
            } else if (bat >= 60) {
                imgBattery.setImageResource(R.drawable.ic_battery_60_black_24dp);
            } else if (bat >= 50) {
                imgBattery.setImageResource(R.drawable.ic_battery_50_black_24dp);
            } else if (bat >= 30) {
                imgBattery.setImageResource(R.drawable.ic_battery_30_black_24dp);
            } else if (bat >= 20) {
                imgBattery.setImageResource(R.drawable.ic_battery_20_black_24dp);
                int red = getColor(R.color.colorAccent);
                tvBatteryLvl.setTextColor(red);
            } else {
                imgBattery.setImageResource(R.drawable.ic_battery_20_black_24dp);
                int red = getColor(R.color.colorAccent);
                tvBatteryLvl.setTextColor(red);
            }
        } else {
            tvBatteryLvl.setText("");
            imgBattery.setImageResource(R.drawable.ic_battery_unknown_black_24dp);
        }
    }

    private void updateDashboard(boolean isConnected) {
        if (isConnected) {
            tvConnectStat.setText(R.string.connected);
        } else {
            tvConnectStat.setText(R.string.nocon);
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            switch (resultCode) {
                case RESULT_OK:
                    serviceBinder.btIsNowOn();
                    break;
                case RESULT_CANCELED:
                    bluetoothDenied++;
                    if (bluetoothDenied == 5) {
                        exitHelper();
                    } else {
                        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(intent, 1);
                    }

                    break;
                default:
                    break;
            }
        }
    }

    private void exitHelper() {
        exitingHelper = true;
        Intent stopIntent = new Intent(this, MainService.class);
        stopService(stopIntent);
        finish();
        //unbind progress is scheduled in onDestroy()
    }

    class ABinder {

        void updateActivityCodecStatus(BluetoothCodecConfig config) {
            updateDashboard(config);
        }

        void updateActivityBattLevel(int level) {
            updateDashboard(level);
        }

        void updateActivityDeviceName(String name) {
            tvDeviceName.setText(name);
        }

        void turnBluetoothOn() {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, 1);
        }

        void updateActivityConnStat(boolean isConn) {
            updateDashboard(isConn);
        }

        void targetDeviceNotFound() {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle(R.string.titlepair)
                    .setMessage(R.string.dialogtext)
                    .setPositiveButton(R.string.gotoset, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                            exitHelper();
                        }
                    })
                    .setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            exitHelper();
                        }
                    })
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            exitHelper();
                        }
                    });
            builder.show();
        }
    }
}
