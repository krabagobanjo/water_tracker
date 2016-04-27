package edu.gatech.watertracker;

import android.app.IntentService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.Pair;

import com.adafruit.bluefruit.le.connect.app.UartDataChunk;
import com.adafruit.bluefruit.le.connect.ble.BleDevicesScanner;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.ble.BleUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

/**
 * Created by ngraves3 on 4/25/16.
 */
public class SyncService extends IntentService implements BleManager.BleManagerListener {

    private final static String TAG  = SyncService.class.getSimpleName();

    public static final String UUID_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    public static final String UUID_RX = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";
    public static final String UUID_TX = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
    public static final String UUID_DFU = "00001530-1212-EFDE-1523-785FEABCD123";
    public static final int kTxMaxCharacters = 20;

    private static final String DEFAULT_UNITS = "fl oz";
    private static final double DEFAULT_VOLUME = 0.0;
    private static final int N_POST_THRESHOLD = 10;

    private boolean mIsScanPaused = true;

    private boolean mIsScanning = true;

    private BleDevicesScanner mScanner;

    private ArrayList<BluetoothDeviceData> mScannedDevices;

    private volatile ArrayList<Pair<Double, String>> mDataBuffer;

    private boolean mIsEolEnabled;

    private int mReceivedBytes;

    private String mTargetDeviceName = "Adafruit Bluefruit LE";
    private BluetoothDevice mTargetDevice;

    // Data
    protected BleManager mBleManager;
    protected BluetoothGattService mUartService;

    private Date mLastPost;

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     */
    public SyncService() {
        super("edu.gatech.watertracker.SyncService");
        Log.d(TAG, "SyncService starting");

        mIsScanning = false;
        mLastPost = new Date(0L); // make this our initial date so we always post the first time.
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        mBleManager = BleManager.getInstance(this);
        mBleManager.setBleListener(this);
        mReceivedBytes = 0;
        mIsEolEnabled = false;
        mDataBuffer = new ArrayList<>();
        while (true) {
            // mIsScanning will be reset by onDisconnected
            if (!mIsScanning) {
                mIsScanning = true;
                startScan(null, mTargetDeviceName);
            }
        }
    }

    @Override
    public void onConnected() {
        Log.d(TAG, "onConnected");

        if (mUartService != null) { // Indicates we have discovered services.
            //initSyncDevice();
        }
    }

    @Override
    public void onConnecting() {
        Log.d(TAG, "onConnecting");
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "onDisconnected");
        mBleManager.disconnect();
        mBleManager.close();
        stopScanning();
        mIsScanning = false;
    }

    @Override
    public void onServicesDiscovered() {
        Log.d(TAG, "onServicesDiscovered");
        mUartService = mBleManager.getGattService(UUID_SERVICE);
        mBleManager.enableNotification(mUartService, UUID_RX, true);
        Log.d(TAG, "Doing UART stuff...");

        initSyncDevice();
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

                UartDataChunk dataChunk = new UartDataChunk(System.currentTimeMillis(), UartDataChunk.TRANSFERMODE_RX, data);
                mDataBuffer.add(parseRawDeviceResponse(dataChunk.getData()));

                ackDevice();

                if (isReadyToPost()) {
                    double totalVolume = getTotalVolume(DEFAULT_UNITS);
                    JSONObject params = convertToJSON(totalVolume, DEFAULT_UNITS);
                    // Insert RestHandler here.
                    // ...
                    // if (post.isSuccessful())
                    mDataBuffer.clear();
                }

                // We can either post the data to fitbit immediately, or we can wait for a certain
                // number of ounces/chunks.
            }
        }
    }

    /**
     * We want to post a new request if one of the two conditions are met:
     *   1. We have more than N data points in our buffer.
     *   2. It has been over 30 seconds since our last post.
     * @return whether or not to send a new post request.
     */
    private boolean isReadyToPost() {
        long now = System.currentTimeMillis();
        if (mLastPost.getTime() + (1000 * 30) < now || mDataBuffer.size() > N_POST_THRESHOLD) {
            mLastPost = new Date(now);
            return true;
        }

        return false;
    }

    private double getTotalVolume(String units) {
        double retval = 0.0;
        for (Pair<Double, String> pair : mDataBuffer) {
            if (pair.second.equals(units)) {
                retval += pair.first;
            }
        }

        return retval;
    }

    private double getTotalVolume() {
        return getTotalVolume(DEFAULT_UNITS);
    }

    private Pair<Double, String> parseRawDeviceResponse(String rawData) {

        if (rawData == null || "".equals(rawData)) {
            return new Pair<>(DEFAULT_VOLUME, DEFAULT_UNITS);
        }

        char delimiter = rawData.charAt(0);
        String[] parsed = rawData.substring(1).split(delimiter + "");

        if (parsed.length == 0) {
            return new Pair<>(DEFAULT_VOLUME, DEFAULT_UNITS);
        }

        String units = (parsed.length >= 2) ? parsed[1] : DEFAULT_UNITS;
        double volume = Double.parseDouble(parsed[0]);

        return new Pair<>(volume, units);
    }

    private JSONObject convertToJSON(double amount, String unit) {
        JSONObject retval = new JSONObject();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-mm-dd");
            String dateString = sdf.format(new Date());

            retval.put("date", dateString);
            retval.put("amount", amount);
            retval.put("unit", unit);

        } catch (JSONException jsonOops) {
            Log.d(TAG, "JSON Parsing failed. Oops.");
        }

        return retval;
    }


    private void initSyncDevice() {
        uartSendData("S");
        Log.d(TAG, "Sent: S");
    }

    private void ackDevice() {
        uartSendData("A");
        Log.d(TAG, "Sent: A");
        try {
            Thread.sleep(500);
        } catch (Exception e) {
            // Do nothing.
        }

        mBleManager.disconnect();
    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {
        Log.d(TAG, "onDataAvailable:descriptor");
        Log.d(TAG, descriptor.toString());
    }

    @Override
    public void onReadRemoteRssi(int rssi) {

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
        //stopScanning();

        // Configure scanning
        BluetoothAdapter bluetoothAdapter = BleUtils.getBluetoothAdapter(getApplicationContext());
        if (BleUtils.getBleStatus(this) != BleUtils.STATUS_BLE_ENABLED) {
            Log.w(TAG, "startScan: BluetoothAdapter not initialized or unspecified address.");
        } else {
            mScanner = new BleDevicesScanner(bluetoothAdapter, servicesToScan, new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
                    final String deviceName = device.getName();

                    if (deviceName != null && deviceName.equalsIgnoreCase(deviceNameToScanFor)) {       // Workaround for bug in service discovery. Discovery filtered by service uuid is not working on Android 4.3, 4.4
                        BluetoothDeviceData deviceData = new BluetoothDeviceData();

                        deviceData.device = device;
                        deviceData.rssi = rssi;
                        deviceData.scanRecord = scanRecord;
                        decodeScanRecords(deviceData);

                        mTargetDevice = device;
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

    // region Send Data to UART
    protected void sendData(String text) {
        final byte[] value = text.getBytes(Charset.forName("UTF-8"));
        sendData(value);
    }


    protected void sendData(byte[] data) {
        if (mUartService != null) {
            // Split the value into chunks (UART service has a maximum number of characters that can be written )
            for (int i = 0; i < data.length; i += kTxMaxCharacters) {
                final byte[] chunk = Arrays.copyOfRange(data, i, Math.min(i + kTxMaxCharacters, data.length));
                mBleManager.writeService(mUartService, UUID_TX, chunk);
            }
        } else {
            Log.w(TAG, "Uart Service not discovered. Unable to send data");
        }
    }

    // Nick: I'm not entirely sure why we need this method, but I think it does something.
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
            if (uuid.toString().equalsIgnoreCase(UUID_SERVICE)) {
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

    private void uartSendData(String data) {
        // Add eol
        if (mIsEolEnabled) {
            // Add newline character if checked
            data += "\n";
        }
        // Send to uart
        sendData(data);
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
