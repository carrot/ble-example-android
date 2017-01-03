// IBLEService.aidl
package com.carrotcreative.bleexampleandroid;

// Declare any non-default types here with import statements

interface IBLEService {
    void init();
    void endService();
    boolean isDeviceConnected();
    void turnLights();
}

