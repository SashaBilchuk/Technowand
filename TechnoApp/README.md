# TechnoApp

##How To Use:

Given you have uploaded a new model that was trained with your labels, and uploaded a fixed sketch to your Arduino Nano 33 BLE - we are ready to do our magic. But first:

- Go to `/TechnoApp/app/src/main/java/com/android/ranit/smartthermostat/view/activity/MainActivity.java`
- Line 823: the function that manages the READ button and manages how often will we check if a signal change (Read a Characteristic).
- Line 832 and 834: change the rate of a read if you want or need.
- Line 232: the function that decides what to do with a recieved signal from arduino.
- Build the app on your android device using Android Studio
- Turn you `GPS Location` on
- Connect to your Arduino Nano 33 BLE device and press Stop to stop the scan ( it takes a lot of energy to scan all the time so we need to turn it off manually)
- Smash the READ button and become a magician. The symbols will appear in accordance to what you programmed at the ` private final BroadcastReceiver humidityBroadcastReceiver = new BroadcastReceiver()` function.


Branched from and tweaked for my purpose of displaying gestures on a screen for a Technowand project I did for an IoT course at Technion 
https://github.com/ranitraj/SmartThermostat
