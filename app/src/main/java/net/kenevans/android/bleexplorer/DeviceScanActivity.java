/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.kenevans.android.bleexplorer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
@SuppressLint("InflateParams")
public class DeviceScanActivity extends AppCompatActivity implements IConstants {
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;
    private ListView mListView;

    private boolean mPermissionsRequested;
    private boolean mAllPermissionsAsked;
    private boolean mBleSupported;

    // Launcher for enabling Bluetooth
    private final ActivityResultLauncher<Intent> enableBluetoothLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Log.d(TAG, "enableBluetoothLauncher: result" +
                                ".getResultCode()=" + result.getResultCode());
                        if (result.getResultCode() != RESULT_OK) {
                            Utils.warnMsg(this, "This app will not work with " +
                                    "Bluetooth disabled");
                        }
                    });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, this.getClass().getSimpleName() + ": onCreate");
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.title_devices);
            actionBar.setDisplayHomeAsUpEnabled(false);
        }
        setContentView(R.layout.list_view);
        mHandler = new Handler();
        mListView = findViewById(R.id.mainListView);
        mListView.setOnItemClickListener((parent, view, position, id) -> onListItemClick(mListView, view, position, id));

        // Use this check to determine whether BLE is supported on the device.
        // Then you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            String msg = getString(R.string.ble_not_supported);
            Utils.errMsg(this, msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            mBleSupported = false;
            return;
        } else {
            mBleSupported = true;
        }

        // Initializes a Bluetooth adapter. For API level 18 and above, get a
        // reference to BluetoothAdapter through BluetoothManager.
        mBluetoothAdapter = null;
        final BluetoothManager bluetoothManager = (BluetoothManager)
                getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Toast.makeText(this, R.string.error_bluetooth_manager,
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        try {
            mBluetoothAdapter = bluetoothManager.getAdapter();
        } catch (NullPointerException ex) {
            Toast.makeText(this, R.string.error_bluetooth_adapter,
                    Toast.LENGTH_LONG).show();
        }

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            String msg = getString(R.string.bluetooth_not_supported);
            Utils.errMsg(this, msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            return;
        }

        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        // Capture global exceptions
        Thread.setDefaultUncaughtExceptionHandler((paramThread,
                                                   paramThrowable) -> {
            Log.e(TAG, "Unexpected exception: ", paramThrowable);
            // Any non-zero exit code
            System.exit(2);
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(!mBleSupported) return false;
        int id = item.getItemId();
        if (id == R.id.menu_scan) {
            mLeDeviceListAdapter.clear();
            startScan();
        }
        if (id == R.id.menu_stop) {
            endScan();
        }
        return true;
    }

    @Override
    protected void onResume() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onResume"
                + " mScanning=" + mScanning);
        super.onResume();

        // Ask for needed permissions
        if (!mPermissionsRequested) {
            Log.d(TAG, "onResume: Requesting Permissions");
            mPermissionsRequested = true;
            requestPermissions();
            return;
        }

        if (!isAllPermissionsGranted(this)) {
            if (!mAllPermissionsAsked) {
                Log.d(TAG, "onResume: Warning permissions not granted");
                mAllPermissionsAsked = true;
                Utils.warnMsg(this, getString(R.string.permission_not_granted));
            } else {
                return;
            }
        }

        // Ensures Bluetooth is enabled on the device. If Bluetooth is not
        // currently enabled,
        // fire an intent to display a dialog asking the user to grant
        // permission to enable it.
        if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBluetoothLauncher.launch(intent);
        }

        // Initializes list view adapter.
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            mLeDeviceListAdapter = new LeDeviceListAdapter();
            mListView.setAdapter(mLeDeviceListAdapter);
            startScan();
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onPause"
                + " mScanning=" + mScanning);
        super.onPause();
        if(!mBleSupported) return;
        Log.d(TAG, "onPause: mScanning=" + mScanning);
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            endScan();
        }
        if (mLeDeviceListAdapter != null) {
            mLeDeviceListAdapter.clear();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[]
            permissions, @NonNull int[] grantResults) {
        Log.d(TAG, this.getClass().getSimpleName()
                + ": onRequestPermissionsResult");
        super.onRequestPermissionsResult(requestCode, permissions,
                grantResults);
        if (requestCode == REQ_ACCESS_PERMISSIONS) {// All (Handle multiple)
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.
                        permission.ACCESS_COARSE_LOCATION)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: COARSE_LOCATION " +
                                "granted");
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: COARSE_LOCATION " +
                                "denied");
                    }
                } else if (permissions[i].equals(Manifest.
                        permission.ACCESS_FINE_LOCATION)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: FINE_LOCATION " +
                                "granted");
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: FINE_LOCATION " +
                                "denied");
                    }
                } else if (permissions[i].equals(Manifest.
                        permission.BLUETOOTH_SCAN)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: BLUETOOTH_SCAN " +
                                "granted");
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: BLUETOOTH_SCAN " +
                                "denied");
                    }
                } else if (permissions[i].equals(Manifest.
                        permission.BLUETOOTH_CONNECT)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: BLUETOOTH_CONNECT" +
                                " " +
                                "granted");
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: BLUETOOTH_CONNECT" +
                                " " +
                                "denied");
                    }
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        // This seems to be necessary with Android 12
        // Otherwise onDestroy is not called
        Log.d(TAG, this.getClass().getSimpleName() + ": onBackPressed");
        finish();
        super.onBackPressed();
    }

    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
        if (device == null)
            return;
        String deviceName, deviceAddress;
        int resultCode;
        if (Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
                        PackageManager.PERMISSION_GRANTED) {
            // Do nothing, don't return
            Log.d(TAG, "  BLUETOOTH_CONNECT not granted");
            deviceName = "Permission error";
            deviceAddress = "Permission error";
            resultCode = RESULT_CANCELED;
        } else {
            deviceName = device.getName();
            deviceAddress = device.getAddress();
            resultCode = RESULT_OK;
        }
        if (mScanning) {
            mBluetoothAdapter.getBluetoothLeScanner().stopScan(mLeScanCallback);
            mScanning = false;
        }
        final Intent intent = new Intent(this, DeviceControlActivity.class);
        intent.putExtra(DEVICE_NAME_CODE, deviceName);
        intent.putExtra(DEVICE_ADDRESS_CODE, deviceAddress);
        setResult(resultCode, intent);
        startActivity(intent);
    }

    private void endScan() {
        Log.d(TAG, this.getClass().getSimpleName()
                + ": endScan: mScanning=" + mScanning);
        if(!mBleSupported) return;
        // Remove the timer
        mHandler.removeCallbacksAndMessages(null);
        // Stop scanning
        if (mScanning) {
            if (Build.VERSION.SDK_INT >= 31 &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) !=
                            PackageManager.PERMISSION_GRANTED) {
                // Do nothing, don't return
                Log.d(TAG, "  BLUETOOTH_SCAN not granted");
            } else {
                mBluetoothAdapter.getBluetoothLeScanner().stopScan(mLeScanCallback);
            }
        }
        mScanning = false;
        invalidateOptionsMenu();
    }

    private void startScan() {
        Log.d(TAG, this.getClass().getSimpleName()
                + ": startScan: mScanning=" + mScanning);
        if(!mBleSupported) return;
        if (!DeviceScanActivity.isAllPermissionsGranted(this)) {
            if (!mAllPermissionsAsked) {
                mAllPermissionsAsked = true;
                Log.d(TAG, "onResume: Warning permissions not granted");
                Utils.warnMsg(this, getString(R.string.permission_not_granted));
            } else {
                return;
            }
        }

        if (!mScanning) {
            // Stops scanning after a pre-defined scan period.
            // Do nothing, don't return
            Runnable mTimer = () -> {
                Log.d(TAG, "Scanning timed out");
                if (Build.VERSION.SDK_INT >= 31 &&
                        checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) !=
                                PackageManager.PERMISSION_GRANTED) {
                    // Do nothing, don't return
                    Log.d(TAG, "  BLUETOOTH_SCAN not granted");
                } else {
                    mBluetoothAdapter.getBluetoothLeScanner().stopScan(mLeScanCallback);
                }
                mScanning = false;
                invalidateOptionsMenu();
            };
            mHandler.postDelayed(mTimer, DEVICE_SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.getBluetoothLeScanner().startScan(null,
                    new ScanSettings.Builder()
                            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                            .setReportDelay(1000)
                            .build(),
                    mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private final ArrayList<BluetoothDevice> mLeDevices;
        private final LayoutInflater mInflator;

        private LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<>();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
        }

        private void addDevice(BluetoothDevice device) {
            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        private BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        private void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = view
                        .findViewById(R.id.device_address);
                viewHolder.deviceName = view
                        .findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            if (Build.VERSION.SDK_INT >= 31 &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
                            PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, this.getClass().getSimpleName()
                        + ": getView: BLUETOOTH_CONNECT not granted");
                return view;
            }
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0) {
                viewHolder.deviceName.setText(deviceName);
                viewHolder.deviceAddress.setText(device.getAddress());
            } else {
                viewHolder.deviceName.setText(R.string.unknown_device);
                viewHolder.deviceAddress.setText("");
            }
            return view;
        }
    }

    // Device scan callback.
    private final ScanCallback mLeScanCallback = new
            ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    Log.d(TAG, this.getClass().getSimpleName() + ": " +
                            "onScanResult");
                    final BluetoothDevice device = result.getDevice();
                    runOnUiThread(() -> {
                        mLeDeviceListAdapter.addDevice(device);
                        mLeDeviceListAdapter.notifyDataSetChanged();
                    });
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    Log.d(TAG, this.getClass().getSimpleName()
                            + ": ScanCallback: onBatchScanResults"
                            + " nResults=" + results.size());
                    // Results is non-null
                    for (ScanResult result : results) {
                        final BluetoothDevice device = result.getDevice();
                        if (Build.VERSION.SDK_INT >= 31 &&
                                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
                                        PackageManager.PERMISSION_GRANTED) {
                            Log.d(TAG, this.getClass().getSimpleName()
                                    + ": onBatchScanResults:"
                                    + " BLUETOOTH_SCAN not granted");
                            return;
                        }
                        Log.d(TAG, "    device=" + device.getName()
                                + " " + device.getAddress());
                        runOnUiThread(() -> {
                            mLeDeviceListAdapter.addDevice(device);
                            mLeDeviceListAdapter.notifyDataSetChanged();
                        });
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    String msg = "Unknown";
                    if (errorCode == SCAN_FAILED_ALREADY_STARTED) {
                        msg = "SCAN_FAILED_ALREADY_STARTED";
                    } else if (errorCode == SCAN_FAILED_APPLICATION_REGISTRATION_FAILED) {
                        msg = "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED";
                    } else if (errorCode == SCAN_FAILED_FEATURE_UNSUPPORTED) {
                        msg = "SCAN_FAILED_FEATURE_UNSUPPORTED";
                    } else if (errorCode == SCAN_FAILED_INTERNAL_ERROR) {
                        msg = "SCAN_FAILED_INTERNAL_ERROR";
                    }
                    Log.d(TAG, "ScanCallback:  onScanFailed " + msg);
                }
            };

    /**
     * Determines if either COARSE or FINE location permission is granted.
     *
     * @return If granted.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isAllPermissionsGranted(Context ctx) {
        boolean granted;
        if (Build.VERSION.SDK_INT >= 31) {
            // Android 12 (S)
            granted = ctx.checkSelfPermission(
                    Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED |
                    ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                            PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 6 (M)
            granted = ctx.checkSelfPermission(
                    Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED |
                    ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED;
        }
        return granted;
    }

    public void requestPermissions() {
        Log.d(TAG, "requestPermissions");
        BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();
            if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent =
                        new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                enableBluetoothLauncher.launch(enableBtIntent);
            }
        }

        if (Build.VERSION.SDK_INT >= 31) {
            // Android 12 (S)
            this.requestPermissions(new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT},
                    REQ_ACCESS_PERMISSIONS);
        } else {
            // Android 6 (M)
            this.requestPermissions(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_ACCESS_PERMISSIONS);
        }
    }

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}