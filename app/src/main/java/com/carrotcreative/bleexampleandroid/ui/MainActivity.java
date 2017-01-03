package com.carrotcreative.bleexampleandroid.ui;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.carrotcreative.bleexampleandroid.R;
import com.carrotcreative.bleexampleandroid.ble.BLEManager;
import com.carrotcreative.bleexampleandroid.ble.BLEService;
import com.carrotcreative.bleexampleandroid.util.PermissionsUtil;
import com.carrotcreative.bleexampleandroid.util.PreferenceManager;
import com.carrotcreative.bleexampleandroid.util.ServiceUtil;
import com.carrotcreative.bleexampleandroid.IBLEService;


/**
 * Created by angelsolis on 12/14/16.
 */
public class MainActivity extends AppCompatActivity implements BLEManager.OnBluetoothManagerListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String PREF_IS_SCANNING = "pref_is_scanning";

    private static final int REQUEST_ENABLE_BT = 1;
    public BluetoothDevice mBluetoothLeDevice;
    private IBLEService mIBLEService;
    private boolean isScanning = false;
    private BLEManager mBLEManager;
    private Dialog mDialog;
    private final long SCAN_TIME = 5000;
    private final long SCAN_TIME_INTERVAL = 1000;
    private CountDownTimer mTimer = new CountDownTimer(SCAN_TIME, SCAN_TIME_INTERVAL) {

        public void onTick(long millisUntilFinished) {
        }

        public void onFinish() {
            if (mBLEManager != null) {
                mBLEManager.stopScanning();
                Toast.makeText(getApplicationContext(), R.string.not_able_to_find_device, Toast.LENGTH_SHORT).show();
                isScanning = false;
                hideSearchingDialog();
            }
        }
    };

    public static void launch(Activity callingActivity) {
        Intent intent = new Intent(callingActivity, MainActivity.class);
        callingActivity.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkBluetooth();
        initializeBLEManager();
        if (savedInstanceState != null) {
            isScanning = savedInstanceState.getBoolean(PREF_IS_SCANNING);
        }
        Button button = (Button) findViewById(R.id.btn_scan);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startBLEScan();
            }
        });
    }

    private void initializeBLEManager() {
        mBLEManager = new BLEManager();
        mBLEManager.setOnDeviceFoundListener(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(PREF_IS_SCANNING, isScanning);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isScanning && checkBluetooth()) {
            Log.d(TAG, "onDestroy :: isScanning");
            isScanning = false;
            if (mBLEManager != null) {
                mTimer.cancel();
                mBLEManager.stopScanning();
                hideSearchingDialog();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ServiceUtil.isMyServiceRunning(this, BLEService.class)) {
            Log.e("isMyServiceRunning", "true");
            Intent gattServiceIntent = new Intent(this, BLEService.class);
            bindService(gattServiceIntent, mServiceConnection, 0);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_ENABLE_BT:
                    break;
            }
        } else {
            switch (requestCode) {
                case REQUEST_ENABLE_BT:
                    checkBluetooth();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isScanning && checkBluetooth()) {
            Log.d(TAG, "onPause :: isScanning");
            isScanning = false;
            if (mBLEManager != null) {
                mTimer.cancel();
                mBLEManager.stopScanning();
                hideSearchingDialog();
            }
        }
    }

    private void hideSearchingDialog() {
        if(mDialog != null) {
            mDialog.dismiss();
        }
    }

    public void startBLEScan() {
        Log.d(TAG, "startBLEScan");
        if (mDialog == null) {
            doShowSearchingDialog();
        } else {
            mDialog.show();
        }
        // If this is the first activity, start advertising
        isScanning = true;
        if (mBLEManager == null) {
            mBLEManager = null;
            initializeBLEManager();
        }
        mTimer.start();
        PermissionsUtil.checkLocationPermission(this, getString(R.string.error_location_permissions),
                new PermissionsUtil.PermissionUtilCallback() {
                    @Override
                    public void onResult(boolean result) {
                        if (result) {
                            mBLEManager.startScanning();
                        }
                    }
                });
    }

    /**
     * Check if bluetooth is supported
     */
    private boolean checkBluetooth() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // Check if bluetooth is supported
        if (bluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), getString(R.string.bluetooth_not_found), Toast.LENGTH_SHORT).show();
            return false;
        } else if (!bluetoothAdapter.isEnabled()) {
            // Check if bluetooth is enabled
            requestEnablingBluetooth();
            return false;
        }
        return true;
    }

    private void requestEnablingBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    @Override
    public void onDeviceFound(BluetoothDevice device) {
        Log.d(TAG, "onDeviceFound");
        mBluetoothLeDevice = device;
        PreferenceManager.setDeviceAddress(getApplicationContext(), device.getAddress());
        hideSearchingDialog();
        HomeActivity.launch(this);
        finish();
    }

    public void doShowSearchingDialog() {
        mDialog = new Dialog(this);
        mDialog.setContentView(R.layout.searching);
        mDialog.setCancelable(false);
        mDialog.show();
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            if (PreferenceManager.getDeviceAddress(getApplicationContext()) != null) {
                mIBLEService = IBLEService.Stub.asInterface((IBinder) service);
                try {
                    if (mIBLEService.isDeviceConnected()) {
                        HomeActivity.launch(MainActivity.this);
                        finish();
                    } else {
                        mIBLEService.endService();
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mIBLEService = null;
        }
    };
}
