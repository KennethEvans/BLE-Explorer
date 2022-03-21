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

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

/**
 * For a given BLE device, this Activity provides the user interface to connect,
 * display data, and display GATT services and characteristics supported by the
 * device. The Activity communicates with {@code BluetoothLeService}, which in
 * turn interacts with the Bluetooth LE API.
 */
public class DeviceControlActivity extends AppCompatActivity implements IConstants {
    private final static String TAG = DeviceControlActivity.class
            .getSimpleName();

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private boolean mBleSupported;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Capture global exceptions
        Thread.setDefaultUncaughtExceptionHandler((paramThread,
                                                   paramThrowable) -> {
            Log.e(TAG, "Unexpected exception :", paramThrowable);
            // Any non-zero exit code
            System.exit(2);
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.title_devices);
            actionBar.setDisplayHomeAsUpEnabled(false);
        }
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(DEVICE_NAME_CODE);
        mDeviceAddress = intent.getStringExtra(DEVICE_ADDRESS_CODE);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = findViewById(R.id.gatt_services_list);
        mGattServicesList
                .setOnChildClickListener(servicesListChildClickListener);
        mGattServicesList
                .setOnGroupClickListener((parent, v, groupPosition, id) -> {
                    // Clear the data area
                    // mDataField.setVisibility(View.GONE);
                    mDataField.setText("");
                    // Cancel any pending notifications
                    if (mNotifyCharacteristic != null) {
                        mBluetoothLeService.setCharacteristicNotification(
                                mNotifyCharacteristic, false);
                    }
                    mNotifyCharacteristic = null;
                    // Return false so it will continue to be handled as if
                    // there were no listener
                    return false;
                });
        mConnectionState = findViewById(R.id.connection_state);
        mDataField = findViewById(R.id.data_value);
        // mDataField.setVisibility(View.GONE);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
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
        int id = item.getItemId();
        if (id == R.id.menu_connect) {
            mBluetoothLeService.connect(mDeviceAddress);
            return true;
        } else if (id == R.id.menu_disconnect) {
            mBluetoothLeService.disconnect();
            return true;
        } else if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(() -> mConnectionState.setText(resourceId));
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        } else {
            mDataField.setText(R.string.no_data);
        }
    }

    // Demonstrates how to iterate through the supported GATT
    // Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the
    // ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null)
            return;
        String uuid;
        String unknownServiceString = getResources().getString(
                R.string.unknown_service);
        String unknownCharaString = getResources().getString(
                R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData =
                new ArrayList<>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<>();
        mGattCharacteristics =
                new ArrayList<>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(LIST_NAME,
                    GattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<>();
            List<BluetoothGattCharacteristic> gattCharacteristics = gattService
                    .getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> characteristics =
                    new ArrayList<>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic :
                    gattCharacteristics) {
                characteristics.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(LIST_NAME,
                        GattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(characteristics);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter =
                new SimpleExpandableListAdapter(
                        this, gattServiceData,
                        android.R.layout.simple_expandable_list_item_2,
                        new String[]{
                                LIST_NAME, LIST_UUID},
                        new int[]{android.R.id.text1,
                                android.R.id.text2}, gattCharacteristicData,
                        android.R.layout.simple_expandable_list_item_2,
                        new String[]{
                                LIST_NAME, LIST_UUID},
                        new int[]{android.R.id.text1,
                                android.R.id.text2});
        mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter
                .addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName componentName,
                                               IBinder service) {
                    mBluetoothLeService =
                            ((BluetoothLeService.LocalBinder) service)
                                    .getService();
                    if (!mBluetoothLeService.initialize()) {
                        Log.e(TAG, "Unable to initialize Bluetooth");
                        finish();
                    }
                    // Automatically connects to the device upon successful
                    // start-up
                    // initialization.
                    mBluetoothLeService.connect(mDeviceAddress);
                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {
                    mBluetoothLeService = null;
                }
            };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device. This can be a
    // result of read
    // or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final String action = intent.getAction();
                    if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                        mConnected = true;
                        updateConnectionState(R.string.connected);
                        invalidateOptionsMenu();
                    } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED
                            .equals(action)) {
                        mConnected = false;
                        updateConnectionState(R.string.disconnected);
                        invalidateOptionsMenu();
                        clearUI();
                    } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED
                            .equals(action)) {
                        // Show all the supported services and
                        // characteristics on the
                        // user interface.
                        displayGattServices(mBluetoothLeService
                                .getSupportedGattServices());
                    } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                        displayData(intent
                                .getStringExtra(BluetoothLeService.EXTRA_DATA));
                    }
                }
            };

    // If a given GATT characteristic is selected, check for supported features.
    // This sample
    // demonstrates 'Read' and 'Notify' features. See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for
    // the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListChildClickListener = new ExpandableListView.OnChildClickListener() {
        @Override
        public boolean onChildClick(ExpandableListView parent, View v,
                                    int groupPosition, int childPosition,
                                    long id) {
            if (mGattCharacteristics != null) {
                // mDataField.setVisibility(View.VISIBLE);
                final BluetoothGattCharacteristic characteristic =
                        mGattCharacteristics
                                .get(groupPosition).get(childPosition);
                // Set the data field to pending
                mDataField.setText(GattAttributes.lookup(
                        characteristic.getUuid().toString(),
                        getResources().getString(
                                R.string.unknown_characteristic))
                        + "\n" + "Pending");
                // If there is an active notification on a characteristic,
                // clear it first so it doesn't update the data field on the
                // user interface
                if (mNotifyCharacteristic != null) {
                    mBluetoothLeService.setCharacteristicNotification(
                            mNotifyCharacteristic, false);
                    mNotifyCharacteristic = null;
                }
                // First try to read it
                final int charaProp = characteristic.getProperties();
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                    mBluetoothLeService.readCharacteristic(characteristic);
                } else {
                    mDataField.setText(GattAttributes.lookup(
                            characteristic.getUuid().toString(),
                            getResources().getString(
                                    R.string.unknown_characteristic))
                            + "\n" + "Not readable");
                }
                // Then set up a notification if possible
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    mNotifyCharacteristic = characteristic;
                    mBluetoothLeService.setCharacteristicNotification(
                            characteristic, true);
                } else {
                    mDataField.setText(GattAttributes.lookup(
                            characteristic.getUuid().toString(),
                            getResources().getString(
                                    R.string.unknown_characteristic))
                            + "\n" + "Notification NA");
                }
                return true;
            }
            return false;
        }
    };

    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText("");
        // mDataField.setVisibility(View.GONE);
    }

}
