package net.kenevans.android.bleexplorer;

import java.util.HashMap;

/**
 * This class includes a small subset of standard GATT attributes for
 * demonstration purposes.
 */
public class GattAttributes {
    private static final HashMap<String, String> attributes = new HashMap<>();
    public static final String BASE_UUID = "00000000-0000-1000-8000" +
			"-00805f9b34fb";
    public static final String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000" +
			"-1000-8000-00805f9b34fb";
    public static final String HEART_RATE_MEASUREMENT = "00002a37-0000-1000" +
			"-8000-00805f9b34fb";
    public static final String BATTERY_LEVEL = "00002a19-0000-1000-8000" +
			"-00805f9b34fb";
    public static final String CUSTOM_MEASUREMENT = "befdff11-c979-11e1-9b21" +
			"-0800200c9a66";
    public static final String TEST_MODE = "befdff12-c979-11e1-9b21" +
			"-0800200c9a66";

    // The names of some of the services and characteristics. Should be lower
    // case.
    static {
        // Services
        attributes
                .put("00001800-0000-1000-8000-00805f9b34fb", "Generic Access");
        attributes.put("00001801-0000-1000-8000-00805f9b34fb",
                "Generic Attribute");
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb",
                "Device Information Service");
        attributes.put("0000180d-0000-1000-8000-00805f9b34fb",
                "Heart Rate Service");
        attributes.put("0000180f-0000-1000-8000-00805f9b34fb",
                "Battery Service");

        attributes.put("befdffb0-c979-11e1-9b21-0800200c9a66",
                "Firmware Update Service");
        attributes.put("befdff10-c979-11e1-9b21-0800200c9a66",
                "HxM2 Custom Data Service");

        // Polar
        attributes.put("fb005c80-02e7-f387-1cad-8acd2d8df0c8",
                "PMD Service");

        // Characteristics
        attributes.put("00002a00-0000-1000-8000-00805f9b34fb", "Device Name");
        attributes.put("00002a01-0000-1000-8000-00805f9b34fb", "Appearance");
        attributes.put("00002a02-0000-1000-8000-00805f9b34fb",
                "Peripheral Privacy Flag");
        attributes.put("00002a03-0000-1000-8000-00805f9b34fb",
                "Reconnection Address");
        attributes.put("00002a04-0000-1000-8000-00805f9b34fb",
                "Peripheral Preferred Connection Properties");
        attributes.put("00002a05-0000-1000-8000-00805f9b34fb",
                "Service Changed");
        attributes.put("00002a23-0000-1000-8000-00805f9b34fb", "System ID");
        attributes.put("00002a24-0000-1000-8000-00805f9b34fb",
                "Model Number String");
        attributes.put("00002a25-0000-1000-8000-00805f9b34fb",
                "Serial Number String");
        attributes.put("00002a26-0000-1000-8000-00805f9b34fb",
                "Firmware Revision String");
        attributes.put("00002a27-0000-1000-8000-00805f9b34fb",
                "Hardware Revision String");
        attributes.put("00002a28-0000-1000-8000-00805f9b34fb",
                "Software Revision String");
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb",
                "Manufacturer name String");
        attributes.put("00002a38-0000-1000-8000-00805f9b34fb",
                "Body Sensor Location");
        attributes.put("00002a39-0000-1000-8000-00805f9b34fb",
                "Heart Rate Control Point");
        attributes.put("00002a50-0000-1000-8000-00805f9b34fb", "PnP ID");
        attributes.put("00002a2a-0000-1000-8000-00805f9b34fb",
                "IEEE Regulatory Certification Data List");

        attributes.put(HEART_RATE_MEASUREMENT, "Heart Rate Measurement");
        attributes.put(BATTERY_LEVEL, "Battery Level");

        attributes.put(CUSTOM_MEASUREMENT, "Custom Measurement");
        attributes.put(TEST_MODE, "Test Mode");
        attributes.put("befdffb1-c979-11e1-9b21-0800200c9a66",
                "Firmware Update Status");
        attributes.put("befdffb2-c979-11e1-9b21-0800200c9a66",
                "Firmware Image Data");

        // Polar
        attributes.put("fb005c81-02e7-f387-1cad-8acd2d8df0c8",
                "PMD Control Point");
        attributes.put("fb005c82-02e7-f387-1cad-8acd2d8df0c8",
                "PMD Data MTU Characteristic");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid.toLowerCase());
        if (name == null) {
            // Make up a name using part of the UUID
            if (uuid.regionMatches(true, 0, BASE_UUID, 0, 4)
                    && uuid.regionMatches(true, 8, BASE_UUID, 8, 28)) {
                name = defaultName + " " + uuid.substring(4, 8);
            } else {
                name = defaultName + " *" + uuid.substring(4, 8);
            }
        }
        return name;
    }

}
