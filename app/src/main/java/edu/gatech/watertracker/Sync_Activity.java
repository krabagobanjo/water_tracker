package edu.gatech.watertracker;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.adafruit.bluefruit.le.connect.app.UartDataChunk;
import com.adafruit.bluefruit.le.connect.app.UartInterfaceActivity;
import com.adafruit.bluefruit.le.connect.ble.BleDevicesScanner;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.ble.BleUtils;

import java.nio.charset.Charset;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

/**
 * Created by ngraves3 on 4/24/16.
 */
public class Sync_Activity extends UartInterfaceActivity implements BleManager.BleManagerListener, BleUtils.ResetBluetoothAdapterListener  {

    private final static String TAG = Sync_Activity.class.getSimpleName();
    private boolean mIsScanPaused = true;
    private BleDevicesScanner mScanner;

    private ArrayList<BluetoothDeviceData> mScannedDevices;
    private BluetoothDeviceData mSelectedDeviceData;
    private Class<?> mComponentToStartWhenConnected = UartActivity.class;

    private volatile int mSentBytes;
    private volatile ArrayList<UartDataChunk> mDataBuffer;

    private boolean mIsEolEnabled;

    private int mReceivedBytes;

    private String mTargetDeviceName = "Adafruit Bluefruit LE";
    private BluetoothDevice targetDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync);
        mBleManager = BleManager.getInstance(this);
        mBleManager.setBleListener(this);
        mSentBytes = 0;
        mReceivedBytes = 0;
        mIsEolEnabled = false;
        mDataBuffer = new ArrayList<>();
        // Maybe launch a background task instead?
        startScan(null, mTargetDeviceName);
    }

    @Override
    public void onConnected() {
        Log.d(TAG, "onConnected");
    }

    @Override
    public void onConnecting() {
        Log.d(TAG, "onConnecting");
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "onDisconnected");
    }

    @Override
    public void onServicesDiscovered() {
        Log.d(TAG, "onServicesDiscovered");
        mUartService = mBleManager.getGattService(UUID_SERVICE);
        mBleManager.enableNotification(mUartService, UUID_RX, true);
        Log.d(TAG, "Pausing scan; Doing UART stuff...");

        // Initialize sync.
        uartSendData("S", false);
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        Log.d(TAG, "onDataAvailable:characteristic");
        Log.d(TAG, characteristic.toString());
        if (characteristic.getService().getUuid().toString().equalsIgnoreCase(UUID_SERVICE)) {
            if (characteristic.getUuid().toString().equalsIgnoreCase(UUID_RX)) {
                final byte[] bytes = characteristic.getValue();
                final String data = new String(bytes, Charset.forName("UTF-8"));

                mReceivedBytes += bytes.length;

                final UartDataChunk dataChunk = new UartDataChunk(System.currentTimeMillis(), UartDataChunk.TRANSFERMODE_RX, data);
                mDataBuffer.add(dataChunk);

                // Debug: print the data received.
                //        Maybe dump the data into something else after receiving it?
                //        Banjo can answer this question better.
                for (UartDataChunk udc : mDataBuffer) {
                    Log.d(TAG, udc.getData());
                }

                // Ack the data received.
                uartSendData("A", false);
            }
        }
    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {
        Log.d(TAG, "onDataAvailable:descriptor");
        Log.d(TAG, descriptor.toString());
    }

    @Override
    public void onReadRemoteRssi(int rssi) {

    }

    @Override
    public void resetBluetoothCompleted() {
        Log.d(TAG, "Reset completed -> Resume scanning");
        resumeScanning();
    }

    private boolean connect(BluetoothDevice device) {
        mBleManager.connect(this, device.getAddress());
        Log.d(TAG, "Connected device: " + mBleManager.getConnectedDevice());
        return mBleManager.getConnectedDevice() != null;
    }

    private void resumeScanning() {
        if (mIsScanPaused) {
            startScan(null, null);
            mIsScanPaused = mScanner == null;
        }
    }

    private void startScan(final UUID[] servicesToScan, final String deviceNameToScanFor) {
        Log.d(TAG, "startScan");

        // Stop current scanning (if needed)
        stopScanning();

        // Configure scanning
        BluetoothAdapter bluetoothAdapter = BleUtils.getBluetoothAdapter(getApplicationContext());
        if (BleUtils.getBleStatus(this) != BleUtils.STATUS_BLE_ENABLED) {
            Log.w(TAG, "startScan: BluetoothAdapter not initialized or unspecified address.");
        } else {
            mScanner = new BleDevicesScanner(bluetoothAdapter, servicesToScan, new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
                    final String deviceName = device.getName();
                    //Log.d(TAG, "Discovered device: " + (deviceName != null ? deviceName : "<unknown>"));

                    BluetoothDeviceData previouslyScannedDeviceData = null;
                    if (deviceNameToScanFor == null || (deviceName != null && deviceName.equalsIgnoreCase(deviceNameToScanFor))) {       // Workaround for bug in service discovery. Discovery filtered by service uuid is not working on Android 4.3, 4.4
                        if (mScannedDevices == null) mScannedDevices = new ArrayList<>();       // Safeguard

                        // Check that the device was not previously found
                        for (BluetoothDeviceData deviceData : mScannedDevices) {
                            if (deviceData.device.getAddress().equals(device.getAddress())) {
                                previouslyScannedDeviceData = deviceData;
                                break;
                            }
                        }

                        BluetoothDeviceData deviceData;
                        if (previouslyScannedDeviceData == null) {
                            // Add it to the mScannedDevice list
                            deviceData = new BluetoothDeviceData();
                            mScannedDevices.add(deviceData);
                        } else {
                            deviceData = previouslyScannedDeviceData;
                        }

                        deviceData.device = device;
                        deviceData.rssi = rssi;
                        deviceData.scanRecord = scanRecord;
                        decodeScanRecords(deviceData);

                        targetDevice = device;
                        connect(device);
                    }
                }
            });

            // Start scanning
            mScanner.start();
        }
    }


    private void stopScanning() {
        // Stop scanning
        Log.d(TAG, "Stopping scan");
        if (mScanner != null) {
            mScanner.stop();
            mScanner = null;
        }
    }

    private void decodeScanRecords(BluetoothDeviceData deviceData) {
        // based on http://stackoverflow.com/questions/24003777/read-advertisement-packet-in-android
        final byte[] scanRecord = deviceData.scanRecord;

        ArrayList<UUID> uuids = new ArrayList<>();
        byte[] advertisedData = Arrays.copyOf(scanRecord, scanRecord.length);
        int offset = 0;
        deviceData.type = BluetoothDeviceData.kType_Unknown;

        // Read standard advertising packet
        while (offset < advertisedData.length - 2) {
            // Length
            int len = advertisedData[offset++];
            if (len == 0) break;

            // Type
            int type = advertisedData[offset++];
            if (type == 0) break;

            // Data
//            Log.d(TAG, "record -> length: " + length + " type:" + type + " data" + data);

            switch (type) {
                case 0x02: // Partial list of 16-bit UUIDs
                case 0x03: {// Complete list of 16-bit UUIDs
                    while (len > 1) {
                        int uuid16 = advertisedData[offset++] & 0xFF;
                        uuid16 |= (advertisedData[offset++] << 8);
                        len -= 2;
                        uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)));
                    }
                    break;
                }

                case 0x06:          // Partial list of 128-bit UUIDs
                case 0x07: {        // Complete list of 128-bit UUIDs
                    while (len >= 16) {
                        try {
                            // Wrap the advertised bits and order them.
                            UUID uuid = BleUtils.getUuidFromByteArraLittleEndian(Arrays.copyOfRange(advertisedData, offset, offset + 16));
                            uuids.add(uuid);

                        } catch (IndexOutOfBoundsException e) {
                            Log.e(TAG, "BlueToothDeviceFilter.parseUUID: " + e.toString());
                        } finally {
                            // Move the offset to read the next uuid.
                            offset += 16;
                            len -= 16;
                        }
                    }
                    break;
                }

                case 0x0A: {   // TX Power
                    final int txPower = advertisedData[offset++];
                    deviceData.txPower = txPower;
                    break;
                }

                default: {
                    offset += (len - 1);
                    break;
                }
            }
        }

        // Check if Uart is contained in the uuids
        boolean isUart = false;
        for (UUID uuid : uuids) {
            if (uuid.toString().equalsIgnoreCase(UartInterfaceActivity.UUID_SERVICE)) {
                isUart = true;
                break;
            }
        }
        if (isUart) {
            deviceData.type = BluetoothDeviceData.kType_Uart;
        }

        deviceData.uuids = uuids;
    }

    // region UART stuff

    private void uartSendData(String data, boolean wasReceivedFromMqtt) {

        // Add eol
        if (mIsEolEnabled) {
            // Add newline character if checked
            data += "\n";
        }

        // Send to uart
        if (!wasReceivedFromMqtt) {
            sendData(data);
            mSentBytes += data.length();
        }
    }



    private class BluetoothDeviceData {
        public BluetoothDevice device;
        public int rssi;
        public byte[] scanRecord;

        // Decoded scan record (update R.array.scan_devicetypes if this list is modified)
        public static final int kType_Unknown = 0;
        public static final int kType_Uart = 1;
        public static final int kType_Beacon = 2;
        public static final int kType_UriBeacon = 3;

        public int type;
        public int txPower;
        public ArrayList<UUID> uuids;
    }


}
