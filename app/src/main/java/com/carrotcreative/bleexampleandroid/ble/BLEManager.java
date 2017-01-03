package com.carrotcreative.bleexampleandroid.ble;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.List;

/**
 * Created by angelsolis on 12/13/16.
 */

@TargetApi(21)
public class BLEManager {
    private static final String MY_UUID = "70212055-3409-8943-1500-4061e0b5495e";
    private static BluetoothLeScanner mLEScanner;
    private static BluetoothAdapter mBluetoothAdapter;
    private static ScanSettings settings;
    private static final String TAG = BLEManager.class.getSimpleName();
    private static boolean isDeviceFound = false;

    /**
     * BLEManager Listener
     */
    public interface OnBluetoothManagerListener {
        void onDeviceFound(BluetoothDevice device);
    }

    private static OnBluetoothManagerListener mOnBluetoothManagerListener = new OnBluetoothManagerListener() {
        @Override
        public void onDeviceFound(BluetoothDevice device) {
            // Do Nothing..
        }
    };

    public BLEManager setOnDeviceFoundListener(OnBluetoothManagerListener callback) {
        mOnBluetoothManagerListener = callback;
        return this;
    }

    /**
     * Start Scanning
     */
    public void startScanning() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (Build.VERSION.SDK_INT >= 21) {
            Log.d(TAG, "StartScanning");
            if (mLEScanner == null) {
                mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                        .build();
            }
            mLEScanner.startScan(null, settings, mScanCallback);
        }
    }

    /**
     * Stop Scanning
     */
    public void stopScanning() {
        if (mBluetoothAdapter == null) {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
        }
        if (Build.VERSION.SDK_INT >= 21) {
            Log.d(TAG, "stopScanning");
            mLEScanner.stopScan(mScanCallback);
        }
    }

    private static ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result.getScanRecord() == null || result.getScanRecord().getDeviceName() == null) {
                return;
            }

            List<ParcelUuid> serviceUuids = result.getScanRecord().getServiceUuids();

            String uuid = "";
            if (serviceUuids.size() > 0) {
                if (serviceUuids.get(0) != null) {
                    uuid = serviceUuids.get(0).toString();
                }
            }
            // check if the uuid matches the required uuid
            if (uuid.startsWith(MY_UUID)) {
                Log.d(TAG, "onScanResult :: found same UUID");
                if (!isDeviceFound) {
                    isDeviceFound = true;
                    final BluetoothDevice bluetoothDevice = result.getDevice();
                    mOnBluetoothManagerListener.onDeviceFound(bluetoothDevice);
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i(TAG, "onBatchScanResults :: result: " + sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "onScanFailed :: Scan Failed Error Code: " + errorCode);
        }
    };
}
