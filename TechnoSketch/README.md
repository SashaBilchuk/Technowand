# TechnoSketch

This part describes a usage of the Arduino Sketch for recording and classifying gestures into symbols.
Basically, It recieves a signal of 3 axes of acceleration and 3 axes of gyroscope and each time we move a stick it updates a change in location of a chip into a X,Y coordinate from a POV of a chip. Then the coordinates are normalized into a range of -127 to 128 or -0.5 to 0.5 and then into a 32*32 bit picture which describe a single gesture in a form of a stroke-line.


## How To Use:

Given you have uploaded a new model that was trained with your labels, now we need to make it work. 
- Go to `magic_wand.ino`
- Line 108: change `constexpr int label_count = 4;` to a number of differrent symbols you have.
- Line 109: change the names of labels `const char* labels[label_count] = {"Hour Glass", "Heart", "Lumos", "Shield Aura"};` to those of yours.
- Upload the sketch to your arduino.

Now we are ready to pair and do our magic gestures, but first we need to fix us an android app up.
More on that at https://github.com/SashaBilchuk/Technowand/blob/main/TechnoApp/README.md

Created based on https://github.com/petewarden/magic_wand project.
More on Pete Warden at https://petewarden.com/
