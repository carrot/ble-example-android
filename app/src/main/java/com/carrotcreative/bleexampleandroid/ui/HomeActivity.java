package com.carrotcreative.bleexampleandroid.ui;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.carrotcreative.bleexampleandroid.R;
import com.carrotcreative.bleexampleandroid.ble.BLEService;
import com.carrotcreative.bleexampleandroid.ble.CharacteristicHelper;
import com.carrotcreative.bleexampleandroid.util.ServiceUtil;
import com.carrotcreative.bleexampleandroid.IBLEService;

import java.math.BigInteger;

/**
 * Created by angelsolis on 12/14/16.
 */

public class HomeActivity extends AppCompatActivity
{
    private static final String TAG = HomeActivity.class.getSimpleName();

    public static final int REQUEST_ENABLE_BT = 1;
    public static IBLEService mIBLEService;
    private Dialog mDialog;

    public static void launch(Activity callingActivity) {
        Intent intent = new Intent(callingActivity, HomeActivity.class);
        callingActivity.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Button button = (Button) findViewById(R.id.btn_send);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIBLEService != null) {
                    try {
                        if (mIBLEService.isDeviceConnected()) {
                            mIBLEService.turnLights();
                        }

                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        if (checkBluetooth()) {
            doShowConnectingDialog();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            if (ServiceUtil.isMyServiceRunning(this, BLEService.class)) {
                Intent gattServiceIntent = new Intent(this, BLEService.class);
                bindService(gattServiceIntent, mServiceConnection, 0);
            } else {
                doConnect();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(bleReceiver, intentFilter());
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(bleReceiver);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService(mServiceConnection);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_ENABLE_BT:
                    doShowConnectingDialog();
            }
        } else {
            switch (requestCode) {
                case REQUEST_ENABLE_BT:
                    checkBluetooth();
            }
        }
    }

    public void doShowConnectingDialog() {
        mDialog = new Dialog(this);
        mDialog.setContentView(R.layout.loading);
        mDialog.setCancelable(false);
        mDialog.show();
        doConnect();
    }

    public void doConnect() {
        Intent gattServiceIntent = new Intent(this, BLEService.class);
        startService(gattServiceIntent);
        bindService(gattServiceIntent, mServiceConnection, 0);
    }


    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {

            mIBLEService = IBLEService.Stub.asInterface((IBinder) service);
            try {
                mIBLEService.init();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
        }
    };

    /**
     * Check if bluetooth is supported
     */
    private boolean checkBluetooth() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), getString(R.string.bluetooth_not_found), Toast.LENGTH_SHORT).show();
            return false;
        } else if (!bluetoothAdapter.isEnabled()) {
            // Check if bluetooth is enabled
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return false;
        }
        return true;
    }

    /**
     * BroadcastReceiver
     */
    private final BroadcastReceiver bleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case BLEService.ACTION_GATT_DISCONNECTED:
                    Log.d(TAG, "onReceive :: ACTION_GATT_DISCONNECTED");
                    Toast.makeText(getApplicationContext(), R.string.disconnected, Toast.LENGTH_SHORT).show();
                    break;
                case BLEService.ACTION_GATT_SERVICES_DISCOVERED:
                    Log.d(TAG, "onReceive :: ACTION_GATT_SERVICES_DISCOVERED");
                    mDialog.dismiss();
                    Toast.makeText(getApplicationContext(), R.string.connected, Toast.LENGTH_SHORT).show();
                    if (ServiceUtil.isMyServiceRunning(getApplicationContext(), BLEService.class)) {
                        Intent gattServiceIntent = new Intent(getApplicationContext(), BLEService.class);
                        bindService(gattServiceIntent, mServiceConnection, 0);
                    } else {
                        doConnect();
                    }
                    break;
                case BLEService.ACTION_GATT_ALREADY_CONNECTED:
                    Log.d(TAG, "onReceive :: ACTION_GATT_ALREADY_CONNECTED");
                    mDialog.dismiss();
                    break;
                case BLEService.ACTION_DATA_AVAILABLE:
                    Log.d(TAG, "onReceive :: ACTION_DATA_AVAILABLE");
                    onDataAvailable(intent.getByteArrayExtra(BLEService.EXTRA_DATA));
                    break;
                case BLEService.ACTION_DEVICE_NOT_FOUND:
                    Log.d(TAG, "onReceive :: ACTION_DEVICE_NOT_FOUND");
                    onDeviceNotFound();
                    break;
                case BLEService.ACTION_DEVICE_NULL_ERROR:
                    Log.d(TAG, "onReceive :: ACTION_DEVICE_NULL_ERROR");
                    // device address is null - take them back to MainActivity
                    MainActivity.launch(HomeActivity.this);
                    finish();
                    break;
                case BLEService.ACTION_REQUEST_BLUETOOTH_PERMISSION:
                    Log.d(TAG, "onReceive :: ACTION_REQUEST_BLUETOOTH_PERMISSION");
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                    break;
                case BLEService.ACTION_DEVICE_WITHOUT_BLUETOOTH:
                    Log.d(TAG, "onReceive :: ACTION_DEVICE_WITHOUT_BLUETOOTH");
                    Toast.makeText(getApplicationContext(), R.string.bluetooth_not_found, Toast.LENGTH_SHORT).show();
                    finish();
                    break;
            }
        }
    };

    private void onDeviceNotFound() {
        Toast.makeText(getApplicationContext(), R.string.not_able_to_find_device, Toast.LENGTH_SHORT).show();
        finish();
    }

    private static IntentFilter intentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BLEService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BLEService.ACTION_DEVICE_NOT_FOUND);
        intentFilter.addAction(BLEService.ACTION_DEVICE_NULL_ERROR);
        intentFilter.addAction(BLEService.ACTION_GATT_ALREADY_CONNECTED);
        return intentFilter;
    }

    public void onDataAvailable(byte[] data) {
        // handle the data received from BLE device
        String result = new BigInteger(1, data).toString(16);
        Log.d(TAG, "onDataAvailable :: result: " + result);
        // if the data matches our BLE_LIGHTS hex data identifier then
        // we determine that the status will be the last byte of the array
        // Note: The data we have here is based on how we implemented the BLE device to respond.
        //       This can be done in many ways.
        if (result.startsWith(CharacteristicHelper.BLE_LIGHTS)) {
            String status = result.substring(result.lastIndexOf(CharacteristicHelper.BLE_LIGHTS) + 1);
            switch (Integer.decode(status)) {
                case 0:
                    Toast.makeText(getApplicationContext(), getString(R.string.lights_off), Toast.LENGTH_SHORT).show();
                    break;
                case 1:
                    Toast.makeText(getApplicationContext(), getString(R.string.lights_on), Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }
}
