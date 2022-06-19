package com.android.ranit.smartthermostat.contract;

import androidx.lifecycle.LiveData;

import com.android.ranit.smartthermostat.data.BleDeviceDataObject;

public interface DataManagerContract {
    void setBleDeviceLiveData(BleDeviceDataObject dataObject);
    LiveData<BleDeviceDataObject> getBleDeviceLiveData();
}
