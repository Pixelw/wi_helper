package com.pixel.wi_helper;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothCodecConfig;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final int CONF_FIT_HQ_MUSIC = 1;
    private static final int CONF_PWRSAV_VIDEO = 2;

    private TextView textView;
    private MainService.MBinder mBinder;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d("new intent","welcome back");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.textView);
        Button buttonRefresh = findViewById(R.id.button);
        Button buttonCodec = findViewById(R.id.button2);
        Button buttonStart = findViewById(R.id.button3);
        Button buttonSer = findViewById(R.id.button4);

        Intent startIntent = new Intent(this,MainService.class);
        startService(startIntent);
        final Intent bindIntent = new Intent(getApplicationContext(), MainService.class);
        bindService(bindIntent, connection,BIND_AUTO_CREATE);
        buttonRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (mBinder.isPlaying()) {
                    case 1:
                        textView.setText("not playing");
                        break;
                    case 2:
                        textView.setText("playing");
                        break;
                    case 0:
                        textView.setText("not connected");
                        break;
                    default:
                        break;
                }

            }
        });

        buttonCodec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ContentValues values = mBinder.getCurrentCodec();
                if (values != null){
                    String ldacq;
                    String sampler;
                    String bitd;
                    switch (values.getAsInteger("LDAC_quality")) {
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

                    switch (values.getAsInteger("samplingRate")) {
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

                    switch (values.getAsInteger("bitDepth")) {
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
                    String str = values.getAsString("codecType") + " " + sampler + " " + bitd + " " + ldacq;
                    textView.setText(str);
                }else {
                    textView.setText(R.string.noconf);
                }
            }
        });

        buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //setCodecByMode(CONF_FIT_HQ_MUSIC);
                mBinder.startBattTest();
            }
        });


        buttonSer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                textView.setText(mBinder.showStatus());
            }
        });
    }


    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBinder = (MainService.MBinder) service;
            if (!mBinder.getBtReady()) {
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent, 1);
            }else {
                mBinder.getBtConnected();
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Toast.makeText(MainActivity.this,"Stop service",Toast.LENGTH_SHORT).show();
        }
    };

    private void stopMainService(){
        stopService(new Intent(this,MainService.class));
        unbindService(connection);
    }
    @SuppressWarnings("ConstantConditions")
    private void setCodecByMode(int mode) {
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

        switch (mode) {
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
            case 2://video or powersaving
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

        mBinder.setCodec(config);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(connection);
        Log.d("exit", "onDestroy: activity");
    }

}
