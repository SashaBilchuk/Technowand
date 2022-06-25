# TechnoSketch

Magic Wand example for [TensorFlow Lite Micro](https://www.tensorflow.org/lite/microcontrollers) on the [Arduino Nano 33 BLE Sense](https://store.arduino.cc/usa/tiny-machine-learning-kit).


##How To Use:

Given you have uploaded a new model that was trained with your labels, now we need to make it work. 
- Go to `magic_wand.ino`
- Line 108: change `constexpr int label_count = 4;` to a number of differrent symbols you have.
- Line 109: change the names of labels `const char* labels[label_count] = {"Hour Glass", "Heart", "Lumos", "Shield Aura"};` to those of yours.
- Upload the sketch to your arduino.

Now we are ready to pair and do our magic gestures, but first we need to fix us an android app up.
More on that at 
