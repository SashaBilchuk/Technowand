package com.android.ranit.smartthermostat.service;

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
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.ranit.smartthermostat.common.CharacteristicTypes;
import com.android.ranit.smartthermostat.common.Constants;
import com.android.ranit.smartthermostat.common.GattAttributes;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT16;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;

/**
 * Created by: Ranit Raj Ganguly on 22/06/2021
 */
public class BleConnectivityService extends Service {
    private static final String TAG = BleConnectivityService.class.getSimpleName();

    // Services
    private BluetoothGattService mEnvironmentSensingService;
    private BluetoothGattService mLedService;
    private BluetoothGattService mDeviceInformationService;

    // Characteristics
    private BluetoothGattCharacteristic mTemperatureCharacteristic;
    private BluetoothGattCharacteristic mHumidityCharacteristic;
    private BluetoothGattCharacteristic mLedCharacteristic;
    private BluetoothGattCharacteristic mDeviceNameCharacteristic;
    private BluetoothGattCharacteristic mDeviceModelCharacteristic;

    // Descriptors
    private BluetoothGattDescriptor mTemperatureDescriptor;
    private BluetoothGattDescriptor mHumidityDescriptor;

    // Command-Queue (Since, ONLY 1 characteristic is read at a time)
    private final Queue<Runnable> mCommandQueue = new LinkedList<>();
    private final Handler mBleHandler = new Handler();
    private boolean mIsCommandQueueBusy = false;
    private boolean mIsCommandQueueRetrying = false;
    private int mNumberOfTries = 0;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;

    private String mBluetoothDeviceAddress;
    private String mLedState;
    private boolean mIsNotifyTemperatureEnabled;
    private boolean mIsNotifyHumidityEnabled;

    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public BleConnectivityService getService() {
            return BleConnectivityService.this;
        }
    }

    /**
     * Implementing GATT callback events
     */
    private final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d(TAG, "onConnectionStateChange() called ");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    int bondState = gatt.getDevice().getBondState();

                    // Take action depending on the bond state
                    if (bondState == BOND_NONE || bondState == BOND_BONDED) {
                        Log.d(TAG, "onConnectionStateChange: Connected to device [" + gatt.getDevice().getName() + "], " +
                                "start discovering services");
                        mBluetoothGatt.discoverServices();

                    } else if (bondState == BOND_BONDING) {
                        Log.i(TAG, "onConnectionStateChange: Waiting for bonding to complete");
                    }

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // Close GATT server to clear resources properly
                    mBluetoothGatt.close();
                }
            } else {
                Log.e(TAG, "onConnectionStateChange(): GATT_FAILURE");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.d(TAG, "onServicesDiscovered() called");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                setupTemperatureCharacteristic(gatt);
                setupHumidityCharacteristic(gatt);
                setupDeviceInformationCharacteristic(gatt);
                setupLedCharacteristic(gatt);

                broadcastUpdate(Constants.ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.e(TAG, "onServicesDiscovered(): GATT_FAILURE");
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.d(TAG, "onCharacteristicRead() called");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Prepare broadcast as per Characteristic Type
                prepareBroadcastAsPerCharacteristicType(characteristic);
            } else {
                Log.e(TAG, "onCharacteristicRead(): GATT_FAILURE");
            }
            completedCommand();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d(TAG, "onCharacteristicWrite() called");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(Constants.ACTION_LED_STATUS_AVAILABLE, characteristic);
            } else {
                Log.e(TAG, "onCharacteristicWrite(): GATT_FAILURE");
            }
            completedCommand();
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.d(TAG, "onDescriptorWrite() called");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Temperature
                if (mIsNotifyTemperatureEnabled) {
                    mTemperatureCharacteristic.setValue(new byte[]{1, 1});
                    gatt.writeCharacteristic(mTemperatureCharacteristic);
                }
                // Humidity
                if (mIsNotifyHumidityEnabled) {
                    mHumidityCharacteristic.setValue(new byte[]{1, 1});
                    gatt.writeCharacteristic(mHumidityCharacteristic);
                }
            } else {
                Log.e(TAG, "onDescriptorWrite(): GATT_FAILURE");
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.d(TAG, "onCharacteristicChanged() called ");

            // Prepare broadcast as per Characteristic Type
            prepareBroadcastAsPerCharacteristicType(characteristic);
        }
    };

    /**
     * Sends data to be updated via broadcast as per characteristic type
     *
     * @param characteristic - BluetoothGattCharacteristic type
     */
    private void prepareBroadcastAsPerCharacteristicType(BluetoothGattCharacteristic characteristic) {
        if (characteristic.getUuid().equals(UUID.fromString(GattAttributes.MANUFACTURER_NAME_CHARACTERISTIC_UUID))) {
            broadcastUpdate(Constants.ACTION_MANUFACTURER_NAME_AVAILABLE, characteristic);
        } else if (characteristic.getUuid().equals(UUID.fromString(GattAttributes.MANUFACTURER_MODEL_CHARACTERISTIC_UUID))) {
            broadcastUpdate(Constants.ACTION_MANUFACTURER_MODEL_AVAILABLE, characteristic);
        } else if (characteristic.getUuid().equals(UUID.fromString(GattAttributes.TEMPERATURE_CHARACTERISTIC_UUID))) {
            broadcastUpdate(Constants.ACTION_TEMPERATURE_AVAILABLE, characteristic);
        } else if (characteristic.getUuid().equals(UUID.fromString(GattAttributes.HUMIDITY_CHARACTERISTIC_UUID))) {
            broadcastUpdate(Constants.ACTION_HUMIDITY_AVAILABLE, characteristic);
        } else if (characteristic.getUuid().equals(UUID.fromString(GattAttributes.LED_CHARACTERISTIC_UUID))) {
            broadcastUpdate(Constants.ACTION_LED_STATUS_AVAILABLE, characteristic);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate() called");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind() called");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind() called");
        closeGattServer();
        return super.onUnbind(intent);
    }

    /**
     * Broadcast to update UI from Service based on GATT callbacks
     *
     * @param action - Type of Action (for Intent-Filter)
     */
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    /**
     * Broadcast to update UI and send values from characteristics of Services
     * based on GATT callbacks.
     *
     * @param action - Type of Action (for Intent-Filter)
     * @param characteristic - characteristic read from Service
     */
     private void broadcastUpdate(final String action, BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // Filter and broadcast Data based on 'CharacteristicType'
        if (characteristic.getUuid().equals(UUID.fromString(GattAttributes.MANUFACTURER_NAME_CHARACTERISTIC_UUID))) {
            String manufacturerName = characteristic.getStringValue(0);
            Log.d(TAG, "broadcastUpdate: Received Manufacturer Name: "+manufacturerName);

            intent.putExtra(Constants.DATA_MANUFACTURER_NAME, manufacturerName);
        } else if (characteristic.getUuid().equals(UUID.fromString(GattAttributes.MANUFACTURER_MODEL_CHARACTERISTIC_UUID))) {
            String manufacturerModel = characteristic.getStringValue(0);
            Log.d(TAG, "broadcastUpdate: Received Manufacturer Model: "+manufacturerModel);

            intent.putExtra(Constants.DATA_MANUFACTURER_MODEL, manufacturerModel);
        } else if (characteristic.getUuid().equals(UUID.fromString(GattAttributes.TEMPERATURE_CHARACTERISTIC_UUID))) {
            int temperatureInteger = (characteristic.getIntValue(FORMAT_UINT16, 0));

            // Need a resolution of 0.01 degrees Celsius, so dividing 'temperature' by 100
            float temperature = (temperatureInteger / 100f);
            Log.d(TAG, "broadcastUpdate: Received Temperature: "+temperature);

            intent.putExtra(Constants.DATA_TEMPERATURE, String.valueOf(temperature));
        } else if (characteristic.getUuid().equals(UUID.fromString(GattAttributes.HUMIDITY_CHARACTERISTIC_UUID))) {
             int humidityInteger = (characteristic.getIntValue(FORMAT_UINT16, 0));

             // Need a resolution of 0.01 percent, so dividing 'humidity' by 100
             float humidity = (humidityInteger / 100f);
             Log.d(TAG, "broadcastUpdate: Received Humidity: "+humidity);

             intent.putExtra(Constants.DATA_HUMIDITY, String.valueOf(humidity));
         } else if (characteristic.getUuid().equals(UUID.fromString(GattAttributes.LED_CHARACTERISTIC_UUID))) {
            Log.d(TAG, "broadcastUpdate: LED is: "+mLedState);

            intent.putExtra(Constants.DATA_LED_STATUS, String.valueOf(mLedState));
        }

        sendBroadcast(intent);
    }

    /**
     * Initialize reference to the Bluetooth Adapter (from Bluetooth Manager)
     *
     * @return boolean - Adapter initialization status
     */
    public boolean initializeBluetoothAdapter() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

            if (mBluetoothManager == null) {
                Log.e(TAG, "initializeBluetoothAdapter: Bluetooth Manager couldn't be initialized");
                return false;
            }

            mBluetoothAdapter = mBluetoothManager.getAdapter();
            if (mBluetoothAdapter == null) {
                Log.e(TAG, "initializeBluetoothAdapter: Bluetooth Adapter couldn't be initialized");
                return false;
            }
        }
        return true;
    }

    /**
     * Read the characteristic
     *
     * @param characteristic - bluetoothGattCharacteristic which is to be read
     */
    private void readCharacteristicFromService(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null || characteristic == null) {
            Log.e(TAG, "readCharacteristicFromService: BluetoothAdapter not initialized or characteristic is null"+ characteristic + mBluetoothAdapter + "yo"+ mBluetoothGatt);
            return;
        }

        // Check if characteristic has READ property enabled
        if ((characteristic.getProperties() & PROPERTY_READ) == 0) {
            Log.e(TAG, "readCharacteristicFromService: Characteristic cannot be read");
            return;
        }

        // Enqueue the read command into Queue
        boolean result = mCommandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (!mBluetoothGatt.readCharacteristic(characteristic)) {
                    Log.e(TAG, "run: Failed to read Characteristic: "+characteristic.getUuid());
                    completedCommand();
                } else {
                    mNumberOfTries++;
                }
             }
        });

        if (result) {
            Log.d(TAG, "readCharacteristicFromService: Reading Characteristic with UUID: "+characteristic.getUuid());
            executeNextCommand();
        } else {
            Log.e(TAG, "readCharacteristicFromService: Couldn't enqueue read characteristic");
        }
    }

    /**
     * Write to characteristic with descriptor (notify)
     *
     * @param descriptor - bluetoothGattCharacteristic to be written to
     */
    private void notifyCharacteristicFromService(BluetoothGattDescriptor descriptor) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.e(TAG, "notifyCharacteristicFromService: BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.writeDescriptor(descriptor);
    }

    /**
     * Write to characteristic
     *
     * @param characteristic - bluetoothGattCharacteristic into which to write
     */
    private void writeCharacteristicToService(BluetoothGattCharacteristic characteristic, byte[] data) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null || characteristic == null) {
            Log.e(TAG, "readCharacteristicFromService: BluetoothAdapter not initialized or characteristic is null");
            return;
        }

        // Check if Write is enabled for the characteristic
        if((characteristic.getProperties() & PROPERTY_WRITE) == 0 ) {
            Log.e(TAG, "writeCharacteristicToService: Characteristic doesn't have Write permission");
            return;
        }

        characteristic.setWriteType(WRITE_TYPE_DEFAULT);
        characteristic.setValue(data);
        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /**
     * Reset variables and command-queue once a command is completed
     */
    private void completedCommand() {
        Log.d(TAG, "completedCommand() called");
        mIsCommandQueueBusy = false;
        mIsCommandQueueRetrying = false;
        mCommandQueue.poll();
        executeNextCommand();
    }

    /**
     * Executes next command
     */
    private void executeNextCommand() {
        Log.d(TAG, "executeNextCommand() called");
        // Return, if some existing command is being executed
        if (mIsCommandQueueBusy) {
            return;
        }

        // Check if GATT object is Invalid
        if (mBluetoothGatt == null) {
            Log.e(TAG, "executeNextCommand: GATT is null, so clearing Command Queue");
            mCommandQueue.clear();
            mIsCommandQueueBusy = false;
            return;
        }

        // Execute next command
        if (mCommandQueue.size() > 0) {
            final Runnable currentCommand = mCommandQueue.peek();
            mIsCommandQueueBusy = true;
            mNumberOfTries = 0;

            mBleHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (currentCommand != null) {
                            currentCommand.run();
                        }
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                }
            });
        }
    }

    /**
     * Retry current command enqueued into the Command-Queue
     */
    private void retryCommand() {
        Log.d(TAG, "retryCommand() called");
        mIsCommandQueueBusy = false;
        Runnable currentCommand = mCommandQueue.peek();

        if (currentCommand != null) {
            if (mNumberOfTries >= Constants.COMMAND_QUEUE_MAX_TRIES) {
                Log.e(TAG, "retryCommand: Maximum retry limit reached");
                mCommandQueue.poll();
            } else {
                mIsCommandQueueRetrying = true;
            }
        }

        executeNextCommand();
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address - The device address of the destination device.
     * @return boolean - true if the connection is initiated successfully.
     */
    public boolean connectToBleDevice(String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.e(TAG, "connectToBleDevice: BluetoothAdapter not initialized or unspecified address");
            return false;
        }

        // Reconnecting to previously connected device
        if (address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
            Log.d(TAG, "connectToBleDevice: Reconnecting to device");
            return mBluetoothGatt.connect();
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.e(TAG, "connectToBleDevice: Device not found");
            return false;
        }

        Log.d(TAG, "connectToBleDevice: Initiating Connection with device: "+device.getName());
        mBluetoothGatt = device.connectGatt(this, false, mBluetoothGattCallback);

        mBluetoothDeviceAddress = address;
        return true;
    }

    /**
     * Disconnects from the existing connection or cancels a pending connection
     */
    public void disconnectFromBleDevice() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.e(TAG, "disconnectFromBleDevice: BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * Called from activity to specify characteristic to be read on
     * 'onServiceDiscovered()' depending upon the type
     *
     * @param type - CharacteristicType to be read
     */
    public void readCharacteristicValue(CharacteristicTypes type) {
        // Update global-variable
        Log.d(TAG, "readCharacteristicValue() called with type: "+ type);

        // Read appropriate Characteristics
        if (type.equals(CharacteristicTypes.TEMPERATURE)) {
            readCharacteristicFromService(mTemperatureCharacteristic);
        } else if (type.equals(CharacteristicTypes.HUMIDITY)) {
            readCharacteristicFromService(mHumidityCharacteristic);
        }
    }

    /**
     * Called from activity to specify characteristic to be notified to in
     * 'onServiceDiscovered()' depending upon the type
     *
     * @param type - CharacteristicType to be read
     */
    public void notifyOnCharacteristicChanged(CharacteristicTypes type) {
        Log.d(TAG, "notifyOnCharacteristicChanged() called with type: "+ type);

        // Write to appropriate Characteristic
        if (type.equals(CharacteristicTypes.TEMPERATURE)) {
            mIsNotifyTemperatureEnabled = true;
            notifyCharacteristicFromService(mTemperatureDescriptor);
        } else if (type.equals(CharacteristicTypes.HUMIDITY)) {
            mIsNotifyHumidityEnabled = true;
            notifyCharacteristicFromService(mHumidityDescriptor);
        }
    }

    /**
     * Called from activity to write value into characteristic which is called in
     * 'onCharacteristicWrite'
     *
     * @param ledStatus - ledStatus to be written into characteristic
     */
    public void writeToLedCharacteristic(byte ledStatus) {
        Log.d(TAG, "writeToLedCharacteristic() called with: ledStatus = [" + ledStatus + "]");

        if (ledStatus == 1) {
            mLedState = Constants.ON;
        } else {
            mLedState = Constants.OFF;
        }

        byte[] ledStatusByteArray = new byte[] {ledStatus};
        writeCharacteristicToService(mLedCharacteristic,ledStatusByteArray);
    }

    /**
     * Called upon un-bind of Service to ensure proper release of resources
     */
    private void closeGattServer() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Sets up all properties related to the Temperature characteristic in
     * the Environment Sensing Service.
     *
     * 1. Get the 'Temperature' characteristic from 'Environment Sensing' service
     * 2. Enable Notification for 'Temperature' characteristic (for 'Notify' only)
     *      a. Get the descriptor property (UUID) from the 'characteristic'
     *      b. Write 'notify' to the characteristics 'descriptor' value using the ENABLE_NOTIFICATION_VALUE
     *
     * @param gatt - Gatt from the onServiceDiscovered callback
     */
    private void setupTemperatureCharacteristic(BluetoothGatt gatt) {
        Log.d(TAG, "setupTemperatureCharacteristic() called ");
        mEnvironmentSensingService = gatt
                .getService(UUID.fromString(GattAttributes.ENVIRONMENTAL_SENSING_SERVICE_UUID));
        mTemperatureCharacteristic = mEnvironmentSensingService
                .getCharacteristic(UUID.fromString(GattAttributes.TEMPERATURE_CHARACTERISTIC_UUID));

        gatt.setCharacteristicNotification(mTemperatureCharacteristic, true);
        mTemperatureDescriptor = mTemperatureCharacteristic
                .getDescriptor(UUID.fromString(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
        mTemperatureDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
    }

    /**
     * Sets up all properties related to the Humidity characteristic in
     * the Environment Sensing Service.
     *
     * @param gatt - Gatt from the onServiceDiscovered callback
     */
    private void setupHumidityCharacteristic(BluetoothGatt gatt) {
        Log.d(TAG, "setupHumidityCharacteristic() called");
        mHumidityCharacteristic = mEnvironmentSensingService
                .getCharacteristic(UUID.fromString(GattAttributes.HUMIDITY_CHARACTERISTIC_UUID));

        gatt.setCharacteristicNotification(mHumidityCharacteristic, true);
        mHumidityDescriptor = mHumidityCharacteristic
                .getDescriptor(UUID.fromString(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
        mHumidityDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
    }

    /**
     * Sets up all properties related to the LED characteristic in
     * the Custom LED Service.
     *
     * @param gatt - Gatt from the onServiceDiscovered callback
     */
    private void setupLedCharacteristic(BluetoothGatt gatt) {
        Log.d(TAG, "setupLedCharacteristic() called ");
        mLedService = gatt.getService(UUID.fromString(GattAttributes.LED_SERVICE_UUID));
        mLedCharacteristic = mLedService
                .getCharacteristic(UUID.fromString(GattAttributes.LED_CHARACTERISTIC_UUID));
    }

    /**
     * Sets up all properties related to the Device Model and Manufacturer characteristic in
     * the Device Information Service.
     *
     * @param gatt - Gatt from the onServiceDiscovered callback
     */
    private void setupDeviceInformationCharacteristic(BluetoothGatt gatt) {
        Log.d(TAG, "setupDeviceInformationCharacteristic() called");
        mDeviceInformationService = gatt.getService(UUID.fromString(GattAttributes.DEVICE_INFORMATION_SERVICE_UUID));
        mDeviceNameCharacteristic = mDeviceInformationService
                .getCharacteristic(UUID.fromString(GattAttributes.MANUFACTURER_NAME_CHARACTERISTIC_UUID));
        mDeviceModelCharacteristic = mDeviceInformationService
                .getCharacteristic(UUID.fromString(GattAttributes.MANUFACTURER_MODEL_CHARACTERISTIC_UUID));

        readCharacteristicFromService(mDeviceNameCharacteristic);
        readCharacteristicFromService(mDeviceModelCharacteristic);
    }
}
