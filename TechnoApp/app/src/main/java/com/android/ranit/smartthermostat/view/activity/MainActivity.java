package com.android.ranit.smartthermostat.view.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.media.MediaPlayer;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import com.airbnb.lottie.LottieAnimationView;
import com.android.ranit.smartthermostat.R;
import com.android.ranit.smartthermostat.common.CharacteristicTypes;
import com.android.ranit.smartthermostat.common.ConnectionStates;
import com.android.ranit.smartthermostat.common.Constants;
import com.android.ranit.smartthermostat.contract.MainActivityContract;
import com.android.ranit.smartthermostat.data.BleDeviceDataObject;
import com.android.ranit.smartthermostat.databinding.ActivityMainBinding;
import com.android.ranit.smartthermostat.databinding.BottomSheetDeviceInformationBinding;
import com.android.ranit.smartthermostat.model.DataManager;
import com.android.ranit.smartthermostat.service.BleConnectivityService;
import com.android.ranit.smartthermostat.view.adapter.BleDeviceAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity
        implements MainActivityContract.View {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String ANIMATION_TEMPERATURE = "temperature.json";
    private static final String ANIMATION_LED_ON = "on.json";
    private static final String ANIMATION_LED_OFF = "off.json";
    private static final String ANIMATION_SCANNING = "scanning.json";
    private static final String ANIMATION_STOPPED = "stopped.json";
    private static final String ANIMATION_HUMIDITY = "humidity.json";

    private ConnectionStates mCurrentState = ConnectionStates.DISCONNECTED;

    private final String[] PERMISSIONS = {
            // Note: Only 'ACCESS_FINE_LOCATION' permission is needed from user at run-time
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private BleConnectivityService mService;

    private ActivityMainBinding mBinding;
    private BottomSheetDeviceInformationBinding mBottomSheetBinding;

    private View mCustomAlertView;
    private RecyclerView mRecyclerView;
    private LottieAnimationView mScanningLottieView;
    private BleDeviceAdapter mRvAdapter;

    private final List<BluetoothDevice> mBleDeviceList = new ArrayList<>();

    private String mManufacturerName;
    private String currentString = "default";
    private String mManufacturerModel;
    private Intent mServiceIntent;
    private boolean mIsLedButtonClicked = false;

    Handler timerHandler = new Handler();
    /**
     * Manage Service life-cycles
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mService = ((BleConnectivityService.LocalBinder) service).getService();
            if (!mService.initializeBluetoothAdapter()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.e(TAG, "onServiceDisconnected: ");
            mService = null;
        }
    };

    /**
     * Device Manufacturer Name Broadcast Receiver
     */
    private final BroadcastReceiver manufacturerNameBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action != null) {
                mManufacturerName = intent.getStringExtra(Constants.DATA_MANUFACTURER_NAME);
                Log.d(TAG, "onReceive() called for with: Manufacture Name = [" + mManufacturerName + "]");
            }
        }
    };

    /**
     * Device Manufacturer Model Broadcast Receiver
     */
    private final BroadcastReceiver manufacturerModelBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action != null) {
                mManufacturerModel = intent.getStringExtra(Constants.DATA_MANUFACTURER_MODEL);
                Log.d(TAG, "onReceive() called for with: Manufacture Model = [" + mManufacturerModel + "]");

                prepareDeviceInfoButton();
            }
        }
    };

    /**
     * Temperature Broadcast Receiver
     */
    private final BroadcastReceiver temperatureBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action != null) {
                String temperature = intent.getStringExtra(Constants.DATA_TEMPERATURE);
                Log.d(TAG, "onReceive() called for with: Temperature = [" + temperature + "]");

                mBinding.tvTemperature.setText(temperature);
            }
        }
    };



    /**
     * LED Status Broadcast Receiver
     */
    private final BroadcastReceiver ledStatusBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action != null) {
                String ledState = intent.getStringExtra(Constants.DATA_LED_STATUS);
                Log.d(TAG, "onReceive() called for with: LED Status = [" + ledState + "]");

                onLedBroadcastEventReceived(ledState);
            }
        }
    };

    /**
     * Observer for current-connection-state of BLE device
     */
    private final Observer<BleDeviceDataObject> mDeviceConnectionStateObserver = new Observer<BleDeviceDataObject>() {
        @Override
        public void onChanged(BleDeviceDataObject bleDeviceDataObject) {
            Log.d(TAG, "onChanged() called with: connectionState = [" + bleDeviceDataObject.getCurrentConnectionState() + "]");
            mCurrentState = bleDeviceDataObject.getCurrentConnectionState();

            if (mCurrentState.equals(ConnectionStates.CONNECTED)) {
                if (bleDeviceDataObject.getBluetoothDevice() != null) {
                    onConnectedBroadcastReceived(bleDeviceDataObject.getBluetoothDevice());
                }
            } else if (mCurrentState.equals(ConnectionStates.DISCONNECTED)) {
                onDisconnectedBroadcastReceived();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        initializeComponents();
        initializeUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerToBroadcastReceivers();
        requestPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkPermissionsAtRuntime()) {
            if (!checkBluetoothStatus()) {
                enableBluetoothRequest();
            }
        }
    }

    /**
     * Humidity Broadcast Receiver
     */
    private final BroadcastReceiver humidityBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action != null) {
                String humidity = intent.getStringExtra(Constants.DATA_HUMIDITY);
                Log.d(TAG, "onReceive() called for with: Humidity = [" + humidity + "]");
                if (!humidity.equals(currentString)){
                    if (humidity.equals("3.0")) {
                        Log.d(TAG, "Yo!0");
                        mBinding.tvHumidity.setBackgroundColor(Color.BLUE);
                        mBinding.tvHumidity.setText("\n\n            ShieldAura");
                        mBinding.tvHumidity.setTextColor(Color.WHITE);

                    }
                    if (humidity.equals("1.0")) {
                        Log.d(TAG, "Yo!1");
                        mBinding.tvHumidity.setBackgroundColor(Color.RED);
                        mBinding.tvHumidity.setText("\n\n\n\n            FireBall");
                        mBinding.tvHumidity.setTextColor(Color.BLACK);

                    }
                    if (humidity.equals("0.0")) {
                        Log.d(TAG, "Yo!2");
                        mBinding.tvHumidity.setBackgroundColor(Color.CYAN);
                        mBinding.tvHumidity.setText("\n\n            Hourglass");
                        mBinding.tvHumidity.setTextColor(Color.BLACK);

                    }
                    if (humidity.equals("2.0")) {
                        Log.d(TAG, "Yo!3");
                        mBinding.tvHumidity.setBackgroundColor(Color.YELLOW);
                        mBinding.tvHumidity.setText("\n\n\n\n            Lumos");
                        mBinding.tvHumidity.setTextColor(Color.BLACK);

                    }
                    currentString = humidity;
                }
            }
        }
    };


    @Override
    protected void onStop() {
        super.onStop();
        unregisterFromBroadcastReceiver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clearComponents();
    }

    @Override
    public void initializeComponents() {
        Log.d(TAG, "initializeComponents() called");

        // Initialize and Bind to Service
        mServiceIntent = new Intent(this, BleConnectivityService.class);
        bindToService();

        // Set initial state of BLE device in Live-Data as DISCONNECTED and bluetoothDevice as 'null'
        DataManager.getInstance()
                .setBleDeviceLiveData(new BleDeviceDataObject(ConnectionStates.DISCONNECTED, null));

        // Attach observer for live-data
        DataManager.getInstance().getBleDeviceLiveData().observeForever(mDeviceConnectionStateObserver);
    }

    @Override
    public void initializeUi() {
        Log.d(TAG, "initializeUi() called");

        // Prepare initial animations
        startAnimation(mBinding.lottieViewTemperature, ANIMATION_TEMPERATURE, true);
        startAnimation(mBinding.lottieViewHumidity, ANIMATION_HUMIDITY, true);
        startAnimation(mBinding.lottieViewLight, ANIMATION_LED_OFF, false);

        prepareConnectButton();
        prepareReadTemperatureButton();
        prepareNotifyTemperatureButton();
        prepareReadHumidityButton();
        prepareNotifyHumidityButton();
        prepareLedToggleButton();
    }

    @Override
    public void clearComponents() {
        Log.d(TAG, "clearComponents() called");
        DataManager.getInstance().getBleDeviceLiveData().removeObserver(mDeviceConnectionStateObserver);
        unbindFromService();
    }

    @Override
    public void startAnimation(LottieAnimationView animationView, String animationName, boolean loop) {
        animationView.setAnimation(animationName);
        animationView.loop(loop);
        animationView.playAnimation();
    }

    @Override
    public void stopAnimation(LottieAnimationView animationView) {
        animationView.cancelAnimation();
    }

    @Override
    public void displaySnackBar(String message) {
        Snackbar.make(mBinding.layoutMain, message, Snackbar.LENGTH_SHORT)
                .show();
    }

    @Override
    public void changeVisibility(View view, int visibility) {
        view.setVisibility(visibility);
    }

    @Override
    public void switchButtonText(Button button, String text) {
        button.setText(text);
    }

    @Override
    public void disableButtons() {
        Log.d(TAG, "disableButtons() called");
        mBinding.btnReadTemperature.setClickable(false);
        mBinding.btnEnableNotify.setClickable(false);
        mBinding.btnToggleLed.setClickable(false);
        mBinding.btnReadHumidity.setClickable(false);
        mBinding.btnEnableNotifyHumidity.setClickable(false);

        mBinding.btnReadTemperature.setAlpha(0.4f);
        mBinding.btnEnableNotify.setAlpha(0.4f);
        mBinding.btnToggleLed.setAlpha(0.4f);
        mBinding.btnReadHumidity.setAlpha(0.4f);
        mBinding.btnEnableNotifyHumidity.setAlpha(0.4f);
    }

    @Override
    public void enableButtons() {
        Log.d(TAG, "enableButtons() called");
        mBinding.btnReadTemperature.setClickable(true);
        mBinding.btnEnableNotify.setClickable(true);
        mBinding.btnToggleLed.setClickable(true);
        mBinding.btnReadHumidity.setClickable(true);
        mBinding.btnEnableNotifyHumidity.setClickable(true);

        mBinding.btnReadTemperature.setAlpha(1f);
        mBinding.btnEnableNotify.setAlpha(1f);
        mBinding.btnToggleLed.setAlpha(1f);
        mBinding.btnReadHumidity.setAlpha(1f);
        mBinding.btnEnableNotifyHumidity.setAlpha(1f);
    }

    @Override
    public void prepareConnectButton() {
        Log.d(TAG, "prepareConnectButton() called");
        mBinding.btnStartScanning.setOnClickListener(connectButtonClickListener);
    }

    @Override
    public void prepareDisconnectButton() {
        Log.d(TAG, "prepareDisconnectButton() called");
        mBinding.btnStartScanning.setOnClickListener(disconnectButtonClickListener);
    }

    @Override
    public void launchDeviceScanDialog() {
        Log.d(TAG, "launchDeviceScanDialog() called");
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(mCustomAlertView)
                .setTitle(R.string.dialog_title)
                .setMessage(R.string.dialog_message)
                .setPositiveButton(R.string.dialog_positive_button, null)
                .setNegativeButton(R.string.dialog_negative_button, null)
                .setNeutralButton(R.string.dialog_neutral_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        stopScanning();
                        dialogInterface.dismiss();
                    }
                })
                .setCancelable(false)
                .create();

        // Implemented in order to avoid auto-dismiss upon click of a dialog button
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                Button positiveButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                Button negativeButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEGATIVE);

                positiveButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.d(TAG, "Start button clicked");
                        startScanning();
                    }
                });

                negativeButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.d(TAG, "Stop button clicked");
                        stopScanning();
                    }
                });
            }
        });
        dialog.show();
    }

    @Override
    public void showDeviceInfoBottomSheetDialog() {
        Log.d(TAG, "showDeviceInfoBottomSheetDialog() called");
        final BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(MainActivity.this);
        mBottomSheetBinding = DataBindingUtil.inflate(LayoutInflater.from(MainActivity.this),
                R.layout.bottom_sheet_device_information, null, false);
        bottomSheetDialog.setContentView(mBottomSheetBinding.getRoot());

        setDataToBottomSheet();

        bottomSheetDialog.show();
    }

    @Override
    public void prepareReadTemperatureButton() {
        Log.d(TAG, "prepareReadTemperatureButton() called");
        mBinding.btnReadTemperature.setOnClickListener(readTemperatureButtonClickListener);
    }

    @Override
    public void prepareNotifyTemperatureButton() {
        Log.d(TAG, "prepareNotifyTemperatureButton() called");
        mBinding.btnEnableNotify.setOnClickListener(notifyTemperatureButtonClickListener);
    }

    @Override
    public void prepareReadHumidityButton() {
        Log.d(TAG, "prepareReadHumidityButton() called");
        mBinding.btnReadHumidity.setOnClickListener(readHumidityButtonClickListener);
    }

    @Override
    public void prepareNotifyHumidityButton() {
        Log.d(TAG, "prepareNotifyHumidityButton() called");
        mBinding.btnEnableNotifyHumidity.setOnClickListener(notifyHumidityButtonClickListener);
    }

    @Override
    public void prepareLedToggleButton() {
        Log.d(TAG, "prepareLedButton() called");
        mBinding.btnToggleLed.setOnClickListener(toggleLedButtonClickListener);
    }

    @Override
    public void prepareDeviceInfoButton() {
        Log.d(TAG, "prepareDeviceInfoButton() called");
        mBinding.btnDeviceInformation.setOnClickListener(deviceInfoButtonClickListener);
    }

    /**
     * Start Scanning for BLE Devices
     * <p>
     * Scanning requires 3 parameters to 'Start Scanning':
     * a) ScanFilter (pass 'null' in case no-specific filtering is required)
     * b) ScanSettings
     * c) ScanCallback
     */
    @Override
    public void startScanning() {
        Log.d(TAG, "startScanning() called");
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        if (mScanningLottieView.getVisibility() != View.VISIBLE) {
            changeVisibility(mScanningLottieView, View.VISIBLE);
        }
        playDialogAnimation(ANIMATION_SCANNING);
        showPreviouslyConnectedDevice();

        // Begin Scan
        Log.d(TAG, "Started Scanning for BLE devices");
        mBluetoothLeScanner.startScan(null, bluetoothLeScanSettings, bluetoothLeScanCallback);
    }

    /**
     * Scanning consumes a lot of battery resource.
     * Hence, stopScan is mandatory
     * <p>
     * 'stopScan' requires one 1 parameter (i.e) 'ScanCallback'
     */
    @Override
    public void stopScanning() {
        Log.d(TAG, "stopScanning() called");

        if (mScanningLottieView.getVisibility() != View.VISIBLE) {
            changeVisibility(mScanningLottieView, View.VISIBLE);
            changeVisibility(mRecyclerView, View.GONE);
        }
        playDialogAnimation(ANIMATION_STOPPED);

        mBluetoothLeScanner.stopScan(bluetoothLeScanCallback);
        mBleDeviceList.clear();
        mRvAdapter.notifyDataSetChanged();
    }

    /**
     * Shows already connected BLE device in the device scanning dialog (if-any)
     */
    @Override
    public void showPreviouslyConnectedDevice() {
        if (DataManager.getInstance().getBleDeviceLiveData().getValue() != null) {
            if (DataManager.getInstance().getBleDeviceLiveData().getValue()
                    .getBluetoothDevice() != null) {
                BluetoothDevice device = DataManager.getInstance().getBleDeviceLiveData().getValue()
                        .getBluetoothDevice();
                Log.d(TAG, "Already Connected to device = [" + device.getName() + "]");

                mBleDeviceList.add(device);
                mCurrentState = ConnectionStates.CONNECTED;
                updateAdapterConnectionState(0);
            } else {
                Log.e(TAG, "Not connected to any BLE device previously");
                mCurrentState = ConnectionStates.DISCONNECTED;
            }
        }
    }

    /**
     * Update UI when CONNECTED broadcast is received
     */
    @Override
    public void onConnectedBroadcastReceived(BluetoothDevice device) {
        Log.d(TAG, "onConnectedBroadcastReceived() called");

        String deviceName = device.getName();
        String mDeviceAddress = device.getAddress();

        enableButtons();
        prepareDisconnectButton();
        updateAdapterConnectionState(-1);

        mBinding.tvDeviceName.setText(deviceName);
        mBinding.tvConnectivityStatus.setText(R.string.connected);
        mBinding.tvConnectivityStatus.setTextColor(getResources().getColor(R.color.green_500));
        changeVisibility(mBinding.btnDeviceInformation, View.VISIBLE);
        switchButtonText(mBinding.btnStartScanning, getResources().getString(R.string.disconnect));
    }

    /**
     * Update UI when DISCONNECTED broadcast is received
     */
    @Override
    public void onDisconnectedBroadcastReceived() {
        Log.d(TAG, "onDisconnectedBroadcastReceived() called");

        disableButtons();
        prepareConnectButton();

        mBinding.tvDeviceName.setText(getString(R.string.no_sensor_connected));
        mBinding.tvConnectivityStatus.setText(R.string.no_sensor_connected);
        mBinding.tvTemperature.setText("0");
        mBinding.tvHumidity.setText("0");
        stopAnimation(mBinding.lottieViewTemperature);
        stopAnimation(mBinding.lottieViewHumidity);
        changeVisibility(mBinding.btnDeviceInformation, View.INVISIBLE);
        mBinding.tvConnectivityStatus.setTextColor(getResources().getColor(R.color.red_500));
        switchButtonText(mBinding.btnStartScanning, getResources().getString(R.string.connect));
    }

    @Override
    public void onLedBroadcastEventReceived(String ledState) {
        Log.d(TAG, "onLedBroadcastEventReceived() called with: ledState = [" + ledState + "]");

        if (ledState.equals(Constants.ON)) {
            startAnimation(mBinding.lottieViewLight, ANIMATION_LED_ON, false);
            switchButtonText(mBinding.btnToggleLed, getString(R.string.led_on));
            mBinding.btnToggleLed.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_on, null));
        } else {
            startAnimation(mBinding.lottieViewLight, ANIMATION_LED_OFF, false);
            switchButtonText(mBinding.btnToggleLed, getString(R.string.led_off));
            mBinding.btnToggleLed.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_off, null));
        }
    }

    @Override
    public void bindToService() {
        Log.d(TAG, "bindToService() called");
        bindService(mServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void unbindFromService() {
        Log.d(TAG, "unbindFromService() called");
        unbindService(mServiceConnection);
    }

    @Override
    public void registerToBroadcastReceivers() {
        Log.d(TAG, "registerToBroadcastReceivers() called");
        registerReceiver(manufacturerNameBroadcastReceiver, new IntentFilter(Constants.ACTION_MANUFACTURER_NAME_AVAILABLE));
        registerReceiver(manufacturerModelBroadcastReceiver, new IntentFilter(Constants.ACTION_MANUFACTURER_MODEL_AVAILABLE));
        registerReceiver(temperatureBroadcastReceiver, new IntentFilter(Constants.ACTION_TEMPERATURE_AVAILABLE));
        registerReceiver(humidityBroadcastReceiver, new IntentFilter(Constants.ACTION_HUMIDITY_AVAILABLE));
        registerReceiver(ledStatusBroadcastReceiver, new IntentFilter(Constants.ACTION_LED_STATUS_AVAILABLE));
    }

    @Override
    public void unregisterFromBroadcastReceiver() {
        Log.d(TAG, "unregisterFromBroadcastReceiver() called");
        unregisterReceiver(manufacturerNameBroadcastReceiver);
        unregisterReceiver(manufacturerModelBroadcastReceiver);
        unregisterReceiver(temperatureBroadcastReceiver);
        unregisterReceiver(humidityBroadcastReceiver);
        unregisterReceiver(ledStatusBroadcastReceiver);
    }

    @Override
    public void connectToDevice(String address) {
        Log.d(TAG, "connectToDevice() called with: address = [" + address + "]");
        mService.connectToBleDevice(address);
    }

    @Override
    public void disconnectFromDevice() {
        Log.d(TAG, "disconnectFromDevice() called");
        mService.disconnectFromBleDevice();
    }

    @Override
    public void requestPermissions() {
        Log.d(TAG, "requestPermissions() called");
        ActivityCompat.requestPermissions(this, PERMISSIONS, Constants.REQUEST_PERMISSION_ALL);
    }

    @Override
    public boolean checkPermissionsAtRuntime() {
        Log.d(TAG, "checkPermissionsAtRuntime() called");
        for (String permission : PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean checkBluetoothStatus() {
        Log.d(TAG, "checkBluetoothStatus() called");
        if (mBluetoothAdapter != null) {
            // Return Bluetooth Enable Status
            return mBluetoothAdapter.isEnabled();
        } else {
            displaySnackBar("This device doesn't support Bluetooth");
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean isAlertDialogInflated = false;
        if (requestCode == Constants.REQUEST_PERMISSION_ALL) {
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    boolean showRationale = shouldShowRequestPermissionRationale(permission);
                    if (!showRationale) {
                        // Called when user selects 'NEVER ASK AGAIN'
                        isAlertDialogInflated = true;

                    } else {
                        // Called when user selects 'DENY'
                        displaySnackBar("Enable permission");
                    }
                }
            }
            inflateEnablePermissionDialog(isAlertDialogInflated);
        }
    }

    /**
     * Shows Alert Dialog when User denies permission permanently
     *
     * @param isTrue - true when user selects on never-ask-again
     */
    private void inflateEnablePermissionDialog(boolean isTrue) {
        if (isTrue) {
            // Inflate Alert Dialog
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Permissions Mandatory")
                    .setMessage("Kindly enable all permissions through Settings")
                    .setPositiveButton("OKAY", (dialogInterface, i) -> {
                        launchAppSettings();
                        dialogInterface.dismiss();
                    })
                    .setCancelable(false)
                    .show();
        }
    }

    /**
     * Launch Enable Bluetooth Request
     */
    private void enableBluetoothRequest() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BLUETOOTH);
    }

    /**
     * Launch App-Settings Screen
     */
    private void launchAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivityForResult(intent, Constants.REQUEST_PERMISSION_SETTING);
    }

    /**
     * Click listener for Connect button
     * Launches alert dialog which facilitates scanning and connection to BLE device.
     */
    private final View.OnClickListener connectButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "connectButton() clicked");
            // Launch Custom Alert-Dialog
            prepareAlertDialog();

            // Start Scanning prior to launch
            startScanning();
            launchDeviceScanDialog();
        }
    };

    /**
     * Click listener for disconnect button
     * Initiates disconnection request to GATT server in order to disconnect from BLE device.
     */
    private final View.OnClickListener disconnectButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "disconnectButton() clicked");
            disconnectFromDevice();
        }
    };

    /**
     * Click listener for Read-temperature button
     * Initiates read-temperature request to BleConnectivityService for Temperature characteristic.
     * <p>
     * Once service is discovered, we filter out the necessary characteristic in 'onServiceDiscovered'
     * callback of the BleGatt and read the same using 'readCharacteristic'.
     * <p>
     * Once characteristic is read, 'onCharacteristicRead' callback of BleGatt is triggered.
     * <p>
     * Note: A characteristic is read only once.
     */
    boolean flag1 = false;
    private final View.OnClickListener readTemperatureButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "readTemperatureButton() clicked");
            mService.readCharacteristicValue(CharacteristicTypes.TEMPERATURE);
            flag1 = true;
        }
    };

    /**
     * Click listener for Notify-Temperature button
     * <p>
     * To set the notification value, we need to tell the sensor to enables us this notification mode.
     * We will write to the characteristic’s descriptor to set the right value: Notify or Indicate.
     */

    private final View.OnClickListener notifyTemperatureButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "notifyTemperatureButton() clicked");
            flag1 = true;
        }
    };

    /**
     * Click listener for Read-humidity button
     * Initiates read-temperature request to BleConnectivityService for Humidity characteristic.
     */
    private final View.OnClickListener readHumidityButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {

            timerHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "readHumidityButton() clicked");
                    mService.readCharacteristicValue(CharacteristicTypes.HUMIDITY);
                    timerHandler.postDelayed(this, 55);
                }
            }, 55);
        }
    };

    /**
     * Click listener for Notify-humidity button
     * Initiates read-temperature request to BleConnectivityService for Humidity characteristic.
     */
    private final View.OnClickListener notifyHumidityButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "notifyHumidityButton() clicked");
            mService.notifyOnCharacteristicChanged(CharacteristicTypes.HUMIDITY);
        }
    };

    /**
     * Click listener for toggle LED button
     */
    private final View.OnClickListener toggleLedButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "toggleLedButton() clicked with state: " + mIsLedButtonClicked);
            byte ledStatus;
            if (mIsLedButtonClicked) {
                ledStatus = 0;
                mIsLedButtonClicked = false;
            } else {
                ledStatus = 1;
                mIsLedButtonClicked = true;
            }
            mService.writeToLedCharacteristic(ledStatus);
        }
    };

    /**
     * Click listener for Device Info button
     */
    private final View.OnClickListener deviceInfoButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            showDeviceInfoBottomSheetDialog();
        }
    };

    /**
     * Sets Manufacturer Name and Model to bottom Sheet received from Broadcast
     */
    private void setDataToBottomSheet() {
        mBottomSheetBinding.tvManufacturerName.setText(mManufacturerName);
        mBottomSheetBinding.tvManufacturerModelName.setText(mManufacturerModel);
    }

    /**
     * Initializing 'ScanSettings' parameter for 'BLE device Scanning' via Builder Pattern
     */
    private final ScanSettings bluetoothLeScanSettings = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
            .build();

    /**
     * Initializing 'ScanCallback' parameter for 'BLE device Scanning'
     * <p>
     * NOTE: onScanResult is triggered whenever a BLE device, matching the
     * ScanFilter and ScanSettings is found.
     * In this callback, we get access to the BluetoothDevice and RSSI
     * objects through the ScanResult
     */
    private final ScanCallback bluetoothLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice bluetoothDevice = result.getDevice();

            // Append device to Scanned devices list
            if (bluetoothDevice.getName() != null) {
                if (!mBleDeviceList.contains(bluetoothDevice)) {
                    Log.d(TAG, "onScanResult: Adding " + bluetoothDevice.getName() + " to list");
                    mBleDeviceList.add(bluetoothDevice);

                    changeVisibility(mRecyclerView, View.VISIBLE);
                    changeVisibility(mScanningLottieView, View.GONE);

                    mRvAdapter.setDeviceList(mBleDeviceList);
                    mRvAdapter.notifyDataSetChanged();
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "onScanFailed() called with: errorCode = [" + errorCode + "]");
        }
    };

    /**
     * Prepare Custom Alert-Dialog
     */
    private void prepareAlertDialog() {
        // Inflate custom layout
        mCustomAlertView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_device_scan, null, false);

        mRecyclerView = mCustomAlertView.findViewById(R.id.rvScannedDevices);
        mScanningLottieView = mCustomAlertView.findViewById(R.id.lottieViewScanning);

        displayDataInRecyclerView(mRecyclerView);
    }

    /**
     * Prepare RecyclerView adapter
     *
     * @param recyclerView - to display Ble devices matching the scan filters and parameters
     */
    private void displayDataInRecyclerView(RecyclerView recyclerView) {
        Log.d(TAG, "displayDataInRecyclerView() called ");
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);

        // Set-up adapter
        mRvAdapter = new BleDeviceAdapter(this, mBleDeviceList,
                new BleDeviceAdapter.DeviceItemClickListener() {
                    @Override
                    public void onDeviceClicked(int position) {
                        if (mCurrentState == ConnectionStates.CONNECTED) {
                            // Disconnect
                            Log.d(TAG, "Disconnecting from Device: " + mBleDeviceList.get(position).getName());

                            disconnectFromDevice();

                            mCurrentState = ConnectionStates.DISCONNECTING;
                            updateAdapterConnectionState(position);
                        } else if (mCurrentState == ConnectionStates.DISCONNECTED) {
                            // Connect
                            Log.d(TAG, "Connecting to Device: " + mBleDeviceList.get(position).getName());

                            connectToDevice(mBleDeviceList.get(position).getAddress());

                            mCurrentState = ConnectionStates.CONNECTING;
                            updateAdapterConnectionState(position);
                        }
                    }
                });

        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(mRvAdapter);
    }

    /**
     * Play lottie-animations within alert-dialog
     *
     * @param animationName - animation to be played
     */
    private void playDialogAnimation(String animationName) {
        mScanningLottieView.setAnimation(animationName);
        mScanningLottieView.playAnimation();
    }

    /**
     * Update Current connection state to Adapter
     */
    private void updateAdapterConnectionState(int position) {
        mRvAdapter.setCurrentDeviceState(mCurrentState, position);
        mRvAdapter.notifyDataSetChanged();
    }
}