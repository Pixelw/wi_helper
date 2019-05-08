package com.pixel.wi_helper;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothCodecConfig;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private TextView tvDeviceName;
    private TextView tvConnectStat;
    private TextView tvBatteryLvl;
    private ImageView imgBattery;
    private ImageView imgCodecLogo;
    private TextView tvCodecName;
    private TextView tvCodecStat;
    private ImageView imgSetHQ;
    private ImageView imgSetPowersaving;
    private Button btnTest;
    private int bluetoothDenied = 0;
    private MainService.MBinder serviceBinder;
    private boolean exitingHelper = false;



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
        imgBattery = findViewById(R.id.ac_battMeter);
        imgCodecLogo = findViewById(R.id.ac_codecLogo);
        tvCodecName = findViewById(R.id.ac_codecText);
        tvCodecStat = findViewById(R.id.ac_codecStatus);
        imgSetHQ = findViewById(R.id.ac_codecHq);
        imgSetPowersaving = findViewById(R.id.ac_codecBatt);
        btnTest = findViewById(R.id.btnTest);

        Intent startIntent = new Intent(this, MainService.class);
        startService(startIntent);
        final Intent bindIntent = new Intent(getApplicationContext(), MainService.class);
        bindService(bindIntent, connection, BIND_AUTO_CREATE);

        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this,
                        String.valueOf(serviceBinder.deviceIsConnected()),Toast.LENGTH_LONG).show();
            }
        });
        imgSetHQ.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serviceBinder.setCodecByPreset(1);
            }
        });

        imgSetPowersaving.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serviceBinder.setCodecByPreset(2);
            }
        });
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
        if (exitingHelper){
            System.exit(0);
        }
    }

    private void updateDashboard(BluetoothCodecConfig config) {
        if (config != null) {
            switch (config.getCodecType()) {
                case 0:
                    tvCodecName.setText(R.string.sbc);
                    imgCodecLogo.setImageDrawable(null);
                    break;
                case 1:
                    tvCodecName.setText(R.string.aac);
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
                    imgCodecLogo.setImageResource(R.drawable.ldac);
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
                    ldacq = "990/909kbps";
                    break;
                case 1001:
                    ldacq = "660/606kbps";
                    break;
                case 1002:
                    ldacq = "330/303kbps";
                    break;
                case 1003:
                    ldacq = "auto";
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
            String str = sampler + " " + bitd + " " + ldacq;
            tvCodecStat.setText(str);
        }
    }

    private void updateDashboard(int bat) {
        if (bat >= 0 && bat <= 100) {
            tvBatteryLvl.setText(bat + "%");
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
                    if (bluetoothDenied == 5){
                        exitHelper();
                    }else {
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
        Intent stopIntent = new Intent(this,MainService.class);
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
    }
}
