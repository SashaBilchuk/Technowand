package com.android.ranit.smartthermostat.contract;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.widget.Button;

import com.airbnb.lottie.LottieAnimationView;

public interface MainActivityContract {
    // View
    interface View {
        void initializeComponents();
        void initializeUi();
        void clearComponents();
        void startAnimation(LottieAnimationView animationView, String animationName, boolean loop);
        void stopAnimation(LottieAnimationView animationView);
        void displaySnackBar(String message);
        void changeVisibility(android.view.View view, int visibility);
        void switchButtonText(Button button, String text);
        void disableButtons();
        void enableButtons();

        void prepareConnectButton();
        void prepareDisconnectButton();
        void launchDeviceScanDialog();
        void showDeviceInfoBottomSheetDialog();

        void prepareReadTemperatureButton();
        void prepareNotifyTemperatureButton();
        void prepareReadHumidityButton();
        void prepareNotifyHumidityButton();
        void prepareLedToggleButton();
        void prepareDeviceInfoButton();

        void startScanning();
        void stopScanning();
        void showPreviouslyConnectedDevice();

        void onConnectedBroadcastReceived(BluetoothDevice device);
        void onDisconnectedBroadcastReceived();
        void onLedBroadcastEventReceived(String ledState);

        void bindToService();
        void unbindFromService();

        void registerToBroadcastReceivers();
        void unregisterFromBroadcastReceiver();

        void connectToDevice(String address);
        void disconnectFromDevice();

        void requestPermissions();
        boolean checkPermissionsAtRuntime();
        boolean checkBluetoothStatus();
    }
}
