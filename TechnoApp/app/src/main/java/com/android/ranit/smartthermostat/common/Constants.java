package com.android.ranit.smartthermostat.common;

public class Constants {
    public static final int SPLASH_ANIMATION_DURATION = 3000;

    public static final int REQUEST_PERMISSION_ALL = 100;
    public static final int REQUEST_PERMISSION_SETTING = 200;
    public static final int REQUEST_ENABLE_BLUETOOTH = 300;

    public static final String ON = "ON";
    public static final String OFF = "OFF";

    // Intent-Filter actions for Broadcast-Receiver
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "smart.thermostat.ACTION_GATT_SERVICES_DISCOVERED";

    public final static String ACTION_MANUFACTURER_NAME_AVAILABLE = "smart.thermostat.ACTION_MANUFACTURER_NAME_AVAILABLE";
    public final static String ACTION_MANUFACTURER_MODEL_AVAILABLE = "smart.thermostat.ACTION_MANUFACTURER_MODEL_AVAILABLE";
    public final static String ACTION_TEMPERATURE_AVAILABLE = "smart.thermostat.ACTION_TEMPERATURE_AVAILABLE";
    public final static String ACTION_HUMIDITY_AVAILABLE = "smart.thermostat.ACTION_HUMIDITY_AVAILABLE";
    public final static String ACTION_LED_STATUS_AVAILABLE = "smart.thermostat.ACTION_LED_STATUS_AVAILABLE";

    public final static String DATA_MANUFACTURER_NAME = "smart.thermostat.DATA_MANUFACTURER_NAME";
    public final static String DATA_MANUFACTURER_MODEL = "smart.thermostat.DATA_MANUFACTURER_MODEL";
    public final static String DATA_TEMPERATURE = "smart.thermostat.DATA_TEMPERATURE";
    public final static String DATA_HUMIDITY = "smart.thermostat.DATA_HUMIDITY";
    public final static String DATA_LED_STATUS = "smart.thermostat.DATA_LED_STATUS";

    public static final int COMMAND_QUEUE_MAX_TRIES = 10;
}
