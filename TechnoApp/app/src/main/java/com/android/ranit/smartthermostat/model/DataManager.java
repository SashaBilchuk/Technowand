package com.android.ranit.smartthermostat.model;

import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.ranit.smartthermostat.contract.DataManagerContract;
import com.android.ranit.smartthermostat.data.BleDeviceDataObject;

/**
 * Created by: Ranit Raj Ganguly on 21/06/2021
 *
 * Singleton Class used to cache and access non-persistent data throughout
 * the application lifecycle.
 */
public class DataManager implements DataManagerContract {
    private static final String TAG = DataManager.class.getSimpleName();

    // Private instance variable
    private static DataManager INSTANCE;

    private MutableLiveData<BleDeviceDataObject> mBleDeviceMutableLiveData = new MutableLiveData<>();

    // Private Constructor
    private DataManager() {}

    // Public method to get instance of Singleton class
    public static DataManager getInstance() {
        if (INSTANCE == null) {
            Log.d(TAG, "Creating new Instance of DataManager class");
            INSTANCE = new DataManager();
        }
        return INSTANCE;
    }

    /**
     * Updates the Mutable-Live-Data based upon the current Connection state
     * received from ACL_Broadcast_Receiver
     *
     * @param dataObject - Current connection state of the Bluetooth device
     */
    @Override
    public void setBleDeviceLiveData(BleDeviceDataObject dataObject) {
        Log.d(TAG, "setBleDeviceLiveData() called");
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Main-Thread
            mBleDeviceMutableLiveData.setValue(dataObject);
        } else {
            // Background-Thread
            mBleDeviceMutableLiveData.postValue(dataObject);
        }
    }

    /**
     * Retrieves the Live-Data in order to be observed and perform necessary
     * UI operations accordingly from View (MainActivity)
     *
     * @return mBleDeviceMutableLiveData - live data
     */
    @Override
    public LiveData<BleDeviceDataObject> getBleDeviceLiveData() {
        Log.d(TAG, "getBleDeviceLiveData() called");
        return mBleDeviceMutableLiveData;
    }

}
