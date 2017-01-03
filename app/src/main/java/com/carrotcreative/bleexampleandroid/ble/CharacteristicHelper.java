package com.carrotcreative.bleexampleandroid.ble;

import java.util.UUID;

/**
 * Created by angelsolis on 12/14/16.
 */

public class CharacteristicHelper {

    // client - sending data to ble device
    public static String BLE_DEVICE_TURN_LIGHTS = "led";

    // client receiving data from ble device
    public static final String BLE_LIGHTS = "713d6c6967687473"; // q=lights

    public final static UUID UUID_BLE_SHIELD_TX = UUID
            .fromString(GattAttributes.BLE_SHIELD_TX);
    public final static UUID UUID_BLE_SHIELD_NOTIFY = UUID
            .fromString(GattAttributes.BLE_SHIELD_NOTIFY);
    public final static UUID UUID_BLE_SHIELD_SERVICE = UUID
            .fromString(GattAttributes.BLE_SHIELD_SERVICE);
}
