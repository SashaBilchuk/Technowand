package com.android.ranit.smartthermostat.broadcastReceiver;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.android.ranit.smartthermostat.common.ConnectionStates;
import com.android.ranit.smartthermostat.data.BleDeviceDataObject;
import com.android.ranit.smartthermostat.model.DataManager;

/**
 * Created by: Ranit Raj Ganguly on 21/06/2021
 *
 * Broadcast Receiver used to receive broadcasts and update LiveData
 * for different bluetooth connection states.
 *
 * NOTE: ACL events are triggered for both Bluetooth-Classic and LE type devices.
 * However, for the scope of this project, we filter only LE devices.
 */
public class AclBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = AclBroadcastReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        BluetoothDevice bluetoothDevice;
        String action = intent.getAction();

        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (bluetoothDevice.getType() == BluetoothDevice.DEVICE_TYPE_LE) {
                Log.d(TAG, "onReceive() called with: ACTION_ACL_CONNECTED");

                // Updating Live-Data as per connection-state CONNECTED
                DataManager.getInstance()
                        .setBleDeviceLiveData(new BleDeviceDataObject(ConnectionStates.CONNECTED, bluetoothDevice));
            }
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (bluetoothDevice.getType() == BluetoothDevice.DEVICE_TYPE_LE) {
                Log.e(TAG, "onReceive() called with: ACTION_ACL_DISCONNECTED");

                // Updating Live-Data as per connection-state DISCONNECTED
                DataManager.getInstance()
                        .setBleDeviceLiveData(new BleDeviceDataObject(ConnectionStates.DISCONNECTED, null));
            }
        }
    }
}
