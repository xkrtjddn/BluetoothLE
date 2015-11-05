package com.humanix.tsw.bluenix;

import android.annotation.TargetApi;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.util.List;
import java.util.UUID;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BluetoothLeService extends Service implements BluetoothAdapter.LeScanCallback {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    public final static UUID UUID_BLUEINNO_PROFILE_SERVICE_UUID =
            UUID.fromString(SampleGattAttributes.UUID_BLUEINNO_PROFILE_SERVICE_UUID);
    public final static UUID UUID_BLUEINNO_PROFILE_SEND_UUID =
            UUID.fromString(SampleGattAttributes.UUID_BLUEINNO_PROFILE_SEND_UUID);
    public final static UUID UUID_BLUEINNO_PROFILE_RECEIVE_UUID =
            UUID.fromString(SampleGattAttributes.UUID_BLUEINNO_PROFILE_RECEIVE_UUID);

    // GATT 이벤트에 대한 콜백 메서드 구현
    // 연결 변화와 서비스 발견 이벤트 등
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;

            //블루투스가 연결 되어있다면
            if(newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;

                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                //성공적으로 연결한 후 서비스 검색 시도
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());
            }
            //블루투스가 연결 끊기면
            else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        //서비스를 발견 하면
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status){
            if(status == BluetoothGatt.GATT_SUCCESS){
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        //블루투스가 연결 성공하면 데이터 읽기
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status){
            if(status == BluetoothGatt.GATT_SUCCESS){
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        //데이터 변경시 읽기
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    private void broadcastUpdate(final String action){
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final  String action, final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        if(UUID_BLUEINNO_PROFILE_SERVICE_UUID.equals(characteristic.getUuid())) {
            int flag = characteristic.getProperties();
            int format = -1;
            if((flag & 0x01) != 0){
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "Bluenix format UINT16");
            }else{
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "Bluenix format UINT8");
            }
            final int data = characteristic.getIntValue(format, 1);
            Log.d(TAG, String.format("Received data: %d", data));
            intent.putExtra(EXTRA_DATA, String.valueOf(data));
        }else{
            //서비스 외에 모든 프로파일 데이터를 HEX값으로 읽어들임
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) /*+ "\n" + stringBuilder.toString()*/);
            }
        }
        sendBroadcast(intent);
    }

    // 데이터 보내기
    public void writeRemoteCharacteristic(BluetoothGattCharacteristic characteristic) {
        if(mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        if (SampleGattAttributes.BLUEINNO_PROFILE_SEND.equals(characteristic.getUuid().toString())) {
            mBluetoothGatt.writeCharacteristic(characteristic);
        }
        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }


    public BluetoothLeService() {
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent){
        close();
        return super.onUnbind(intent);
    }

    /**
     * 로컬 블루투스 어댑터에 대한 참조 초기화
     *
     * @return 초기화 성공하면 true 반환
     */
    public boolean initialize() {
        // API level 18 이상은 BluetoothManager를 통해 BluetoothAdapter에 대한 참조를 가져온다
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * 블루투스 LE 장치에서 호스팅 GATT 서버에 연결
     *
     * @param address 연결할 디바이스의 주소
     *
     * @return 연결이 성공적으로 시작되는 경우 true 반환
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         연결 결과 콜백을 통해 비동기적으로 보고됩니다.
     */
    public boolean connect(final String address){
        if(mBluetoothAdapter == null || address == null){
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // 이전에 장치가 연결되어 있었으면 다시 연결
        if(mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
            // 연결을 위해 기존 mBluetoothGatt 을 사용
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if(mBluetoothGatt.connect()){
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if(device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        // 디바이스에 직접 연결하려면 자동연결설정을 false로 세팅한다.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * 기존 연결을 끊거나 대기중인 연결을 취소할 수 있다.
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * 주어진 BLE장치를 사용 후, 자원이 남아있는것을 방지하기 위해 종료
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * 읽기 요청. 판독결과는 콜백을 통해 비동기로 보고된다.
     * @param characteristic 읽어들일 특성
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * 쓰기 요청, 판독결과는 콜백을 통해 비동기로 보고된다.
     * @param characteristic 쓰게될 특성
     * @param data 쓸 데이터
     */
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] data){
        Log.d(TAG, "tsw_Blue_writeCharacteristic:" + new String(data));

        characteristic.setValue(data);
        characteristic.setWriteType(2);
        mBluetoothGatt.writeCharacteristic(characteristic);
        Log.d(TAG, "tsw_Blue_writeCharacteristic");
    }

    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled){
        if(mBluetoothAdapter == null || mBluetoothGatt == null ) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }

    /**
     * 연결된 장치에서 지원 GATT 서비스의 목록을 검색. 이 후 호출이 성공적으로 완료되어야함
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    public BluetoothGatt getGattServise(){
        return mBluetoothGatt;
    }

    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {

    }
}
