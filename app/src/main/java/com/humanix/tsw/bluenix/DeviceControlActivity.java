package com.humanix.tsw.bluenix;

import android.app.Activity;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;


/**
 * Created by Administrator on 2015-10-19.
 */
public class DeviceControlActivity extends Activity{
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private static final UUID UUID_BLUEINNO_PROFILE_SERVICE_UUID = UUID.fromString("00002220-0000-1000-8000-00805f9b34fb");
    private static final UUID UUID_BLUEINNO_PROFILE_SEND_UUID = UUID.fromString("00002222-0000-1000-8000-00805f9b34fb");
    private static final UUID UUID_BLUEINNO_PROFILE_RECEIVE_UUID = UUID.fromString("00002221-0000-1000-8000-00805f9b34fb");

    private TextView mConnectionState;
    private TextView mDataField;
    private ImageView ledImage;
    private ImageView ledImage_green;
    private Switch mSwitch_red;
    private Switch mSwitch_green;
    private Switch mSwitch_blink;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private BluetoothGatt mGatt;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";
    private String STATE = null;
    private String LEDSTATE_RED = "1B";
    private String LEDSTATE_GREEN = "2B";
    private String LEDSTATE_BLINK = "3B";
    private String mDataRed = null;
    private String mDataGreen = null;
    private String mDataBlink = null;

    private int blinkState = 1;

    private Timer mTimer;
    private String temp = "1B2B3B";

    private Thread blinkThread;

    private boolean buttonEvent = false;        // 버튼 이벤트가 실행중이면 true, 실행중이 아닐 때 false

    //서비스 라이프 사이클 관리
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // 디바이스 초기화가 완료되면 자동적으로 연결
            mBluetoothLeService.connect(mDeviceAddress);

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // ACTION_GATT_CONNECTED :GATT 서버에 연결 .
    // ACTION_GATT_DISCONNECTED :GATT 의 서버와의 연결끊김.
    // ACTION_GATT_SERVICES_DISCOVERED 는 : GATT 서비스를 발견.
    // ACTION_DATA_AVAILABLE : 장치 에서 데이터 송수신.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {      // 연결 상태
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {    // 연결 해제 상태
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();

            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {     // 디바이스 검색 상태
                // Show all the supported services and characteristics on the user interface.
                //displayGattServices(mBluetoothLeService.getSupportedGattServices());

            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {   // 데이터 송수신 가능 상태
                try{
                    if(!blinkThread.isAlive()){
                        blinkThread.start();
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }

            }
        }
    };

    // LED 스위치 변경 이벤트 리스너
    private final Switch.OnCheckedChangeListener ledSwitchListener = new Switch.OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            buttonEvent = true;
            // RED LED STATE SETUP
            if (mSwitch_red.isChecked()) {
                LEDSTATE_RED = "1A";
                setSTATE();
            } else {
                LEDSTATE_RED = "1B";
                setSTATE();
            }

            // GREEN LED STATE SETUP
            if(mSwitch_green.isChecked()) {
                LEDSTATE_GREEN = "2A";
                LEDSTATE_BLINK = "3B";  // 스위치를 켜면 Blink는 OFF
                setSTATE();
            }else{
                LEDSTATE_GREEN = "2B";
                setSTATE();
            }

            // BLINK STATE SETUP
            if(mSwitch_blink.isChecked()) {
                LEDSTATE_BLINK = "3A";
                LEDSTATE_GREEN = "2B";  // Blink를 켜면 스위치는 OFF
                setSTATE();
            }else{
                LEDSTATE_BLINK = "3B";
                setSTATE();
            }

            sendEvent(STATE);
            buttonEvent = false;
        }
    };

    /**
     *  블루투스로부터 데이터를 수신받는 스레드
     */
    private TimerTask mTask = new TimerTask() {
        @Override
        public void run() {
            // 버튼이벤트가 실행중이면 넘김
            if (!buttonEvent) {

                BluetoothGattService blueService = null;
                if (mBluetoothLeService != null) {
                    mGatt = mBluetoothLeService.getGattServise();
                    blueService = mGatt.getService(UUID_BLUEINNO_PROFILE_SERVICE_UUID);
                }

                if (blueService != null) {

                    try {
                        if (blueService.getCharacteristic(UUID_BLUEINNO_PROFILE_RECEIVE_UUID) != null) {
                            String state = null;
                            mGatt.readCharacteristic(blueService.getCharacteristic(UUID_BLUEINNO_PROFILE_RECEIVE_UUID));

                            if (blueService.getCharacteristic(UUID_BLUEINNO_PROFILE_RECEIVE_UUID).getValue() != null) {
                                state = new String(blueService.getCharacteristic(UUID_BLUEINNO_PROFILE_RECEIVE_UUID).getValue());
                            }

                            if (state != null) {
                                int i = state.length();
                                STATE = state.substring(0, i);

                                LEDSTATE_RED = STATE.substring(0, 2);
                                LEDSTATE_GREEN = STATE.substring(2, 4);
                                LEDSTATE_BLINK = STATE.substring(4, 6);
                                //LEDSTATE = state.substring(0, i);

                                if(!temp.equals(STATE)) {               // 방금 수신받은 데이터와 이전의
                                    Message msg = Message.obtain();     // 데이터가 다르면 핸들러 호출을 통한 UI 변경경
                                   ledChangeHandler.sendMessage(msg);
                                }
                                temp = STATE;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);

        ledImage = (ImageView) findViewById(R.id.led_img);
        ledImage_green = (ImageView) findViewById(R.id.led_img_green);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        mSwitch_red = (Switch) findViewById(R.id.led_switch);
        mSwitch_red.setOnCheckedChangeListener(ledSwitchListener);

        mSwitch_green = (Switch) findViewById(R.id.led_switch_green);
        mSwitch_green.setOnCheckedChangeListener(ledSwitchListener);

        mSwitch_blink = (Switch) findViewById(R.id.led_switch_blink);
        mSwitch_blink.setOnCheckedChangeListener(ledSwitchListener);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        mTimer = new Timer(true);
        mTimer.scheduleAtFixedRate(mTask, 500, 50);


        /**
         *  LED 깜빡임을 의한 쓰레드
         *  Blink 스위치가 ON 되었을때만 실행
         */
        blinkThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    if(!buttonEvent) {
                        if (LEDSTATE_BLINK.equals("3A")) {
                            try {
                                //mDataBlink = "ON";
                                Message msg = Message.obtain(); // 쓰레드 내에서는 UI 변경이 안되기 때문에
                                blinkHandler.sendMessage(msg);  // 핸들러를 호출하여 진행

                                // 0.5 초 딜레이
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;

        mTask.cancel();
        mTimer.cancel();
        blinkThread.interrupt();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     *  연결 상태 텍스트 필드 업데이트
     * @param resourceId strings.xml에 적혀있는 상태 ID
     */
    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    /**
     *  STATE 초기화 함수
     */
    public void setSTATE() {
        if(LEDSTATE_RED != null && LEDSTATE_GREEN != null && LEDSTATE_BLINK != null){
            STATE = LEDSTATE_RED + LEDSTATE_GREEN + LEDSTATE_BLINK;
        }
        mDataFldUpdate();   //텍스트 필드 내용을 STATE와 같도록 업데이트
    }

    /**
     *  스위치 상태변경시 호출된는 이벤트
     *  블루투스로 변경된 상태를 송신한 후, 수신되는 데이터가 송신한 데이터와 같아질 때까지 반복
     *  송수신 데이터가 같으면 이벤트 종료
     * @param state 송신할 데이터
     */
    private void sendEvent(String state) {
        List<BluetoothGattService> gattServices = mBluetoothLeService.getSupportedGattServices();
        if (gattServices == null) return;
        BluetoothGattService blueService = null;
        if (mBluetoothLeService != null) {
            mGatt = mBluetoothLeService.getGattServise();
            blueService = mGatt.getService(UUID_BLUEINNO_PROFILE_SERVICE_UUID);
        }

        for (BluetoothGattService gattService : gattServices) {
            if ((gattService.getUuid().toString()).equals(SampleGattAttributes.UUID_BLUEINNO_PROFILE_SERVICE_UUID)) {
                BluetoothGattCharacteristic sendCharacteristic = gattService.getCharacteristic(UUID_BLUEINNO_PROFILE_SEND_UUID);
                BluetoothGattCharacteristic readCharacteristic = gattService.getCharacteristic(UUID_BLUEINNO_PROFILE_RECEIVE_UUID);

                if (sendCharacteristic != null && readCharacteristic != null) {

                    while (true) {
                        sendCharacteristic.setValue(state);                                    //  데이터 보내는 부분
                        mBluetoothLeService.writeRemoteCharacteristic(sendCharacteristic);

                        // 보내는 데이터와 다시 받는 데이터가 같은지 확인하기 위해서 데이터 받는 부분
                        String blueState = null;
                        mGatt.readCharacteristic(readCharacteristic);
                        if (blueService.getCharacteristic(UUID_BLUEINNO_PROFILE_RECEIVE_UUID).getValue() != null) {
                            blueState = new String(blueService.getCharacteristic(UUID_BLUEINNO_PROFILE_RECEIVE_UUID).getValue());
                        }
                        if (blueState != null) {
                            //int i = blueState.length() - 1;
                            blueState = blueState.substring(0, 6);

                            if (blueState.equals(STATE)) {
                                Message msg = Message.obtain();
                                ledChangeHandler.sendMessage(msg);
                                break;     // 블루투스와 어플의 led 상태가 같으면 무한루프 종료
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     *  RED LED 이미지 변경
     * @param state true : ON, false : OFF
     */
    private void setLed(boolean state) {
        if (state) {
            //LED켜짐
            ledImage.setImageResource(R.drawable.motor_r_min);
        } else {
            //LED꺼짐
            ledImage.setImageResource(R.drawable.motor_r_max);
        }
    }

    /**
     *  GREEN LED 이미지 변경
     * @param state true : ON, false : OFF
     */
    private void setLedGreen(boolean state) {
        if(state) {
            // LED ON
            ledImage_green.setImageResource(R.drawable.motor_g_min);
        } else {
            // LED OFF
            ledImage_green.setImageResource(R.drawable.motor_g_max);
        }
    }

    // GREEN LED Blink 쓰레드에서 호출되는 핸들러, 깜빡임 이미지 변경
    Handler blinkHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (!buttonEvent) {
                mDataFldUpdate();

                // blinkState는 정수형이며, 초기값은 1, 홀수일 때 ON, 짝수일 때 OFF, 이미지 변경 후 +1
                if(blinkState%2 == 1) {
                    ledImage_green.setImageResource(R.drawable.motor_g_min);
                    blinkState++;
                }else{
                    ledImage_green.setImageResource(R.drawable.motor_g_max);
                    blinkState++;
                }
            }
        }
    };

    // LED 상태 변경 핸들러
    Handler ledChangeHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (!buttonEvent) {       // 버튼 이벤트 중일때는 동작 안함
                mDataFldUpdate();

                if (mDataRed.equals("ON")) {
                    setLed(true);
                    mSwitch_red.setChecked(true);
                } else {
                    setLed(false);
                    mSwitch_red.setChecked(false);
                }

                if(mDataGreen.equals("ON")) {
                    setLedGreen(true);
                    mSwitch_green.setChecked(true);
                } else {
                    setLedGreen(false);
                    mSwitch_green.setChecked(false);
                }

                // 스위치 상태만 변경
                if(mDataBlink.equals("ON")) {
                    mSwitch_blink.setChecked(true);
                } else {
                    mSwitch_blink.setChecked(false);
                }
            }
        }
    };

    // Receive Data 텍스트 업데이트 메서드
    public void mDataFldUpdate(){
        // A : ON, B : OFF
        if(LEDSTATE_RED.equals("1A")){
            mDataRed = "ON";
        }else {
            mDataRed = "OFF";
        }

        if(LEDSTATE_GREEN.equals("2A")) {
            mDataGreen = "ON";
        } else {
            mDataGreen = "OFF";
        }

        if(LEDSTATE_BLINK.equals("3A")) {
            mDataBlink = "ON";
        } else {
            mDataBlink = "OFF";
        }

        if(mDataRed != null && mDataGreen != null && mDataBlink != null) {
            mDataField.setText(mDataRed + " " + mDataGreen + " " + mDataBlink);
        }
    }
}
