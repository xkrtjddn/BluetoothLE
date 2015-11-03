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
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TimerTask;
import java.util.*;
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
    private Switch mSwitch;
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
    private String LEDSTATE = null;

    private Timer mTimer;

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
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();

            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                //displayGattServices(mBluetoothLeService.getSupportedGattServices());

            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

            }
        }
    };

    private final Switch.OnCheckedChangeListener ledSwitchListener = new Switch.OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            buttonEvent = true;
            if (isChecked) {
                LEDSTATE = "ON";
                sendEvent(true);
            } else {
                LEDSTATE = "OFF";
                sendEvent(false);
            }
            buttonEvent = false;
        }
    };

    String temp = "ON";

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
                                int i = state.length() - 1;
                                LEDSTATE = state.substring(0, i);

                                if(!temp.equals(LEDSTATE)) {
                                    Message msg = Message.obtain();
                                    ledChangeHandler.sendMessage(msg);
                                }
                                temp = LEDSTATE;
                            }
                        }
                    } catch (Exception e) {
                        showMessage(e.getMessage());
                    }
                }

                if(mSwitch.isChecked()) {
                    if(!mDataField.getText().equals("ON")) {
                        sendEvent(true);
                        Message msg = Message.obtain();
                        ledChangeHandler.sendMessage(msg);
                    }
                } else if (mDataField.getText().equals("ON")) {
                    sendEvent(false);
                    Message msg = Message.obtain();
                    ledChangeHandler.sendMessage(msg);
                }

            }
        }
    };

    private final ImageView.OnClickListener ledButtonChangeListener =
            new ImageView.OnClickListener() {

                @Override
                public void onClick(View v) {
                    sendEvent();
                }
            };

    private void sendEvent() {
        List<BluetoothGattService> gattServices = mBluetoothLeService.getSupportedGattServices();
        if (gattServices == null) return;

        buttonEvent = true;

        for (BluetoothGattService gattService : gattServices) {
            if ((gattService.getUuid().toString()).equals(SampleGattAttributes.UUID_BLUEINNO_PROFILE_SERVICE_UUID)) {
                BluetoothGattCharacteristic blueCharacteristic = gattService.getCharacteristic(UUID_BLUEINNO_PROFILE_SEND_UUID);

                if (blueCharacteristic != null) {

                    if (LEDSTATE == null || !LEDSTATE.equals("ON")) {//스위치를 눌렀을때 스위치가 꺼져 있으면
                        //스위치 상태 변경 => 켜짐
                        String offStr = "A";
                        blueCharacteristic.setValue(offStr);
                        LEDSTATE = "ON";
                    } else {
                        //스위치 상태 변경 => 꺼짐
                        String offStr = "OFF";
                        blueCharacteristic.setValue(offStr);
                        LEDSTATE = "OFF";
                    }

                    mBluetoothLeService.writeRemoteCharacteristic(blueCharacteristic);
                }
            }
        }

        buttonEvent = false;
    }

    private void sendEvent(boolean state) {
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
                    String offStr;
                    if (state) {//스위치를 눌렀을때 스위치가 꺼져 있으면
                        //스위치 상태 변경 => 켜짐
                        offStr = "A";
                        LEDSTATE = "ON";

                        //setLed(true);
                        //mDataField.setText("ON");

                        while (true) {
                            sendCharacteristic.setValue(offStr);                                    //  데이터 보내는 부분
                            mBluetoothLeService.writeRemoteCharacteristic(sendCharacteristic);      //  데이터 보내는 부분

                            // 보내는 데이터와 다시 받는 데이터가 같은지 확인하기 위해서 데이터 받는 부분
                            String blueState = null;
                            mGatt.readCharacteristic(readCharacteristic);
                            if (blueService.getCharacteristic(UUID_BLUEINNO_PROFILE_RECEIVE_UUID).getValue() != null) {
                                blueState = new String(blueService.getCharacteristic(UUID_BLUEINNO_PROFILE_RECEIVE_UUID).getValue());
                            }
                            if ( blueState != null) {
                                int i = blueState.length() - 1;
                                blueState = blueState.substring(0, i);

                                if ( blueState.equals(LEDSTATE)) break;     //블루투스 led 상태랑 어플 상태랑 같으면 무한루프 종료
                            }
                        }

                    } else {
                        //스위치 상태 변경 => 꺼짐
                        offStr = "OFF";
                        LEDSTATE = "OFF";

                        //setLed(false);
                        //mDataField.setText("OFF");

                        while (true) {
                            sendCharacteristic.setValue(offStr);                                    //  데이터 보내는 부분
                            mBluetoothLeService.writeRemoteCharacteristic(sendCharacteristic);      //  데이터 보내는 부분

                            // 보내는 데이터와 다시 받는 데이터가 같은지 확인하기 위해서 데이터 받는 부분
                            String blueState = null;
                            mGatt.readCharacteristic(readCharacteristic);
                            if (blueService.getCharacteristic(UUID_BLUEINNO_PROFILE_RECEIVE_UUID).getValue() != null) {
                                blueState = new String(blueService.getCharacteristic(UUID_BLUEINNO_PROFILE_RECEIVE_UUID).getValue());
                            }
                            if ( blueState != null) {
                                int i = blueState.length() - 1;
                                blueState = blueState.substring(0, i);

                                if ( blueState.equals(LEDSTATE)) break;     //블루투스 led 상태랑 어플 상태랑 같으면 무한루프 종료
                            }
                        }
                    }
                }
            }
        }
    }


    private void setLed(boolean state) {
        if (state) {
            //LED켜짐
            ledImage.setImageResource(R.drawable.motor_r_min);
        } else {
            //LED꺼짐
            ledImage.setImageResource(R.drawable.motor_r_max);
        }
    }

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
        ledImage.setOnClickListener(ledButtonChangeListener);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        mSwitch = (Switch) findViewById(R.id.led_switch);
        mSwitch.setOnCheckedChangeListener(ledSwitchListener);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        mTimer = new Timer(true);
        mTimer.scheduleAtFixedRate(mTask, 500, 50);
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

        mTimer.cancel();
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

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    // 지원 GATT 서비스 / 특성을 반복하는 방법을 보여준다.
    // ExpandableListView 에 바인딩된 데이터 구조를 채운다.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[]{LIST_NAME, LIST_UUID},
                new int[]{android.R.id.text1, android.R.id.text2},
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[]{LIST_NAME, LIST_UUID},
                new int[]{android.R.id.text1, android.R.id.text2}
        );
        //mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    // LED 상태 변경 핸들러
    Handler ledChangeHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (!buttonEvent) {       // 버튼 이벤트 중일때는 동작 안함
                mDataField.setText(LEDSTATE);

                if (LEDSTATE.equals("ON")) {
                    setLed(true);
                    mSwitch.setChecked(true);
                } else {
                    setLed(false);
                    mSwitch.setChecked(false);
                }
            }
        }
    };

    // 메시지를 화면에 표시
    public void showMessage(String strMsg) {
        // 메시지 텍스트를 핸들러에 전달
        Message msg = Message.obtain(mHandler, 0, strMsg);
        mHandler.sendMessage(msg);
        Log.d("STATE", strMsg);
    }

    // 메시지 화면 출력을 위한 핸들러
    Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == 0) {
                String strMsg = (String) msg.obj;
                Toast.makeText(getApplicationContext(), strMsg, Toast.LENGTH_SHORT);
            }
        }
    };
}
