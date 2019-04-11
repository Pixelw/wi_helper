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
    private int i = 0;
    private MainService.MBinder serviceBinder;


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
                serviceBinder.testBind();
            }
        });

        tvDeviceName = findViewById(R.id.tv_device_name);
        tvConnectStat = findViewById(R.id.tv_connect_status);
        tvBatteryLvl = findViewById(R.id.ac_battLv);
        imgBattery = findViewById(R.id.ac_battMeter);
        imgCodecLogo = findViewById(R.id.ac_codecLogo);
        tvCodecName = findViewById(R.id.ac_codecText);
        tvCodecStat = findViewById(R.id.ac_codecStatus);

        Intent startIntent = new Intent(this, MainService.class);
        startService(startIntent);
        final Intent bindIntent = new Intent(getApplicationContext(), MainService.class);
        bindService(bindIntent, connection, BIND_AUTO_CREATE);
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            serviceBinder = (MainService.MBinder) service;
            ABinder aBinder = new ABinder();
            serviceBinder.castBinder(aBinder);
            if (!serviceBinder.getBtReady()) {
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent, 1);
            } else {
                if (serviceBinder.getBtConnected()){
                    tvConnectStat.setText(R.string.connected);
                }else{
                    tvConnectStat.setText(R.string.nocon);
                }
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Toast.makeText(MainActivity.this, "Stop service", Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(connection);
        Log.d("exit", "onDestroy: activity");
    }

    private void updateDashboard(BluetoothCodecConfig config) {
        if (config != null){
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

    class ABinder {
        void toastOnActivity(String msg) {
            Toast.makeText(MainActivity.this, msg + i, Toast.LENGTH_LONG).show();
            i++;
        }

        void updateActivityCodecStatus(BluetoothCodecConfig config) {
            updateDashboard(config);
        }

        void updateActivityBattLevel(int level) {
            updateDashboard(level);
        }

        void updateActivityName(String name) {
            tvDeviceName.setText(name);
        }
    }
}
