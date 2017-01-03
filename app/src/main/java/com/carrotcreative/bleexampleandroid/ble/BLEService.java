package com.carrotcreative.bleexampleandroid.ble;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.carrotcreative.bleexampleandroid.R;
import com.carrotcreative.bleexampleandroid.ui.HomeActivity;
import com.carrotcreative.bleexampleandroid.util.PreferenceManager;
import com.carrotcreative.bleexampleandroid.IBLEService;

import java.util.UUID;

/**
 * Created by angelsolis on 12/14/16.
 */

public class BLEService extends Service
{
    private static final String TAG = "BLEService";
    private static boolean isConnectedToDevice = false;
    BluetoothManager mBluetoothManager;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothGatt mGatt;
    String mBluetoothDeviceAddress;

    public final static String ACTION_GATT_DISCONNECTED =
            "com.asolis.mvpexample.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.asolis.mvpexample.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_GATT_ALREADY_CONNECTED =
            "com.asolis.mvpexample.ACTION_GATT_ALREADY_CONNECTED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.asolis.mvpexample.ACTION_DATA_AVAILABLE";
    public final static String ACTION_DEVICE_NOT_FOUND =
            "com.asolis.mvpexample.ACTION_DEVICE_NOT_FOUND";
    public final static String ACTION_DEVICE_NULL_ERROR =
            "com.asolis.mvpexample.ACTION_DEVICE_NULL_ERROR";
    public final static String ACTION_DEVICE_WITHOUT_BLUETOOTH =
            "com.asolis.mvpexample.ACTION_DEVICE_WITHOUT_BLUETOOTH";
    public final static String ACTION_REQUEST_BLUETOOTH_PERMISSION =
            "com.asolis.mvpexample.ACTION_REQUEST_BLUETOOTH_PERMISSION";

    public final static String EXTRA_DATA = "com.asolis.bleperipheralandroid.EXTRA_DATA";

    @Override
    public IBinder onBind(Intent intent) {
        return new IBLEService.Stub() {
            @Override
            public void init() throws RemoteException
            {
                Log.d(TAG, "init");
                if (!BLEService.isConnectedToDevice) {
                    Log.d(TAG, "init :: isConnectedToDevice: " + isConnectedToDevice);
                    if (initialize()) {
                        // Automatically connects to the device upon successful start-up
                        // initialization.
                        connect(PreferenceManager.getDeviceAddress(getApplicationContext()));
                    } else {
                        Log.d(TAG, "init :: Unable to initialize Bluetooth");
                        if (getSystemService(Context.BLUETOOTH_SERVICE) == null) {
                            broadcastUpdate(ACTION_DEVICE_WITHOUT_BLUETOOTH);
                        } else {
                            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                                broadcastUpdate(ACTION_REQUEST_BLUETOOTH_PERMISSION);
                            }
                        }
                    }
                } else {
                    broadcastUpdate(ACTION_GATT_ALREADY_CONNECTED);
                }
            }

            @Override
            public void endService() throws RemoteException
            {
                isConnectedToDevice = false;
                stopForeground(true);
                broadcastUpdate(ACTION_GATT_DISCONNECTED);
            }

            @Override
            public boolean isDeviceConnected() throws RemoteException
            {
                return isConnectedToDevice;
            }

            @Override
            public void turnLights() throws RemoteException
            {
                writeCharacteristic(CharacteristicHelper.BLE_DEVICE_TURN_LIGHTS);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // make it continue to run until it is stopped.
        return START_STICKY;
    }

    /**
     * After using a given BLE device, the app must call this method to ensure
     * resources are released properly.
     */
    public void close() {
        if (mGatt == null) {
            return;
        }
        mGatt.close();
        mGatt = null;
    }

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                return false;
            }
        }
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            return false;
        }

        return true;
    }

    public boolean connect(final String address) {
        if (address == null) {
            Log.d(TAG, "connect :: address is null");
            broadcastUpdate(ACTION_DEVICE_NULL_ERROR);
            return false;
        }

        // Previously connected device. Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mGatt != null) {
            if (mGatt.connect()) {
                Log.d(TAG, "connect :: Trying to use an existing mBluetoothGatt for connection.");
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);

        if (device == null) {
            Log.w(TAG, "connect :: Device not found. Unable to connect.");
            broadcastUpdate(ACTION_DEVICE_NOT_FOUND);
            return false;
        }
        mGatt = device.connectGatt(this, true, gattCallback);

        Log.d(TAG, "connect :: Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        return true;
    }

    private boolean isRunningonForeground = false;
    /**
     * Gatt Callback
     */
    public BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i(TAG, "onConnectionStateChange :: Status: " + status);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.d(TAG, "onConnectionStateChange :: STATE_CONNECTED");
                    isConnectedToDevice = true;
                    if (!isRunningonForeground) {
                        runAsForeground();
                    }
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.d(TAG, "onConnectionStateChange :: STATE_DISCONNECTED");
                    isConnectedToDevice = false;
                    stopForeground(true);
                    broadcastUpdate(ACTION_GATT_DISCONNECTED);
                    isRunningonForeground = false;
                    break;
                default:
                    Log.d(TAG, "onConnectionStateChange :: STATE_OTHER");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "onServicesDiscovered :: GATT SUCCESS");
                mGatt = gatt;
                if (mGatt.getServices().size() != 0) {
                    BluetoothGattService bleService = gatt.getService(CharacteristicHelper.UUID_BLE_SHIELD_SERVICE);
                    if (bleService != null) {
                        broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                        enableCharacteristicNotification();
                    }
                }
            } else {
                Log.d(TAG, "onServicesDiscovered :: status: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,
                                         int status) {
            Log.d(TAG, "onCharacteristicRead");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.d(TAG, "onCharacteristicChanged");
            // data is available
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    /**
     * Enables or disables notification on a give characteristic.
     */
    public void enableCharacteristicNotification() {
        if (mBluetoothAdapter == null || mGatt == null) {
            Log.d(TAG, "enableCharacteristicNotification :: BluetoothAdapter not initialized");
            return;
        }

        BluetoothGattCharacteristic characteristic = getCharacteristic(CharacteristicHelper.UUID_BLE_SHIELD_NOTIFY);
        mGatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic
                .getDescriptor(UUID.fromString(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mGatt.writeDescriptor(descriptor);
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        Log.d(TAG, "broadcastUpdate :: UUID: " + characteristic.getUuid());
        if (CharacteristicHelper.UUID_BLE_SHIELD_NOTIFY.equals(characteristic.getUuid())) {
            final byte[] data = characteristic.getValue();
            intent.putExtra(EXTRA_DATA, data);
        }
        sendBroadcast(intent);
    }

    public BluetoothGattCharacteristic getCharacteristic(UUID uuid) {
        if (mGatt == null)
            return null;
        return mGatt.getService(CharacteristicHelper.UUID_BLE_SHIELD_SERVICE).getCharacteristic(uuid);
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null || mGatt == null) {
            Log.e(TAG, "writeCharacteristic :: BluetoothAdapter or Gatt not initialized");
            return;
        }
        mGatt.writeCharacteristic(characteristic);
    }

    public void writeCharacteristic(String data) {
        BluetoothGattCharacteristic characteristic = getCharacteristic(CharacteristicHelper.UUID_BLE_SHIELD_TX);
        byte[] tmp = data.getBytes();
        byte[] tx = new byte[16];
        for (int i = 0; i < tmp.length; i++) {
            tx[i] = tmp[i];
        }
        characteristic.setValue(tx);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null || mGatt == null) {
            Log.e(TAG, "writeCharacteristic :: BluetoothAdapter or Gatt not initialized");
            return;
        }
        mGatt.writeCharacteristic(characteristic);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (isConnectedToDevice) {
            runAsForeground();
        }
    }

    private void runAsForeground() {
        isRunningonForeground = true;
        Intent notificationIntent = new Intent(this, HomeActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentText(getString(R.string.connected_to_ble_device))
                .setContentIntent(pendingIntent).build();
        startForeground(1, notification);
    }
}
