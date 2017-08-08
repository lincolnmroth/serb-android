package com.rutgers.winlab.serbctrl;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.RxBleDeviceServices;
import com.polidea.rxandroidble.utils.ConnectionSharingAdapter;
import static com.trello.rxlifecycle.android.ActivityEvent.PAUSE;
import com.trello.rxlifecycle.components.support.RxAppCompatActivity;

import java.util.UUID;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.subjects.PublishSubject;
import rx.functions.Action0;
import rx.functions.Action1;



public class MainActivity extends RxAppCompatActivity {
// TODO: stop scanning when both are found
    private static final String TAG = "MainActivity";
    private BluetoothAdapter mBluetoothAdapter;
    private static final int REQUEST_ENABLE_BT = 1;
    private RxBleClient rxBleClient;
    private PublishSubject<Void> disconnectTriggerP = PublishSubject.create();
    private Observable<RxBleConnection> connectionObservableP;
    private RxBleDevice devPrimary;
    private PublishSubject<Void> disconnectTriggerS = PublishSubject.create();
    private Observable<RxBleConnection> connectionObservableS;
    private RxBleDevice devSecondary;
    private PublishSubject<Void> disconnectTriggerR = PublishSubject.create();
    private Observable<RxBleConnection> connectionObservableR;
    private RxBleDevice devRasp;
    private boolean frontAutoMode = false;
    private boolean rearAutoMode = false;
    private boolean isLocked = false;
    private boolean hazardsOn = false;
    private final byte DEV_ALL = 0;
    private final byte DEV_FRONT = 1;
    private final byte DEV_REAR = 2;
    private final byte DEV_TURN = 3;
    private final byte DEV_LOCK = 4;
    private final byte STATE_OFF = 0;
    private final byte STATE_ON = 1;
    private final byte STATE_LOW = 2;
    private final byte STATE_BLINK = 3;
    private final byte STATE_LEFT = 4;
    private final byte STATE_RIGHT = 5;
    private byte FRONT_LAST_STATE = STATE_OFF;
    private byte REAR_LAST_STATE = STATE_OFF;

    private Observable<RxBleConnection> prepConnObservableP() {
        return devPrimary
                .establishConnection(true)
                .takeUntil(disconnectTriggerP)
                .compose(bindUntilEvent(PAUSE))
                .compose(new ConnectionSharingAdapter());
    }

    private Observable<RxBleConnection> prepConnObservableS() {
        return devSecondary
                .establishConnection(true)
                .takeUntil(disconnectTriggerS)
                .compose(bindUntilEvent(PAUSE))
                .compose(new ConnectionSharingAdapter());
    }

    private Observable<RxBleConnection> prepConnObservableR() {
        return devRasp
                .establishConnection(true)
                .takeUntil(disconnectTriggerR)
                .compose(bindUntilEvent(PAUSE))
                .compose(new ConnectionSharingAdapter());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Button button = (Button) findViewById(R.id.buttonlock);
        final Button hazButton = (Button) findViewById(R.id.buttonhazard);

        button.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (isLocked == false) {
                            this.lock();
                            button.setText("UNLOCK");
                            isLocked = true;
                        } else {
                            this.unlock();
                        }
                    }

                    private void unlock() {
                        writeRasp(DEV_LOCK, STATE_OFF);
                        button.setText("LOCK");
                        isLocked = false;
                    }

                    private void lock() {
                        writeRasp(DEV_LOCK, STATE_ON);
                    }
                });
        hazButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(hazardsOn == false){
                    hazards();
                    hazardsOn = true;
                    hazButton.setText("Hazards OFF");
                }
                else{
                    allOff();
                    hazardsOn = false;
                    hazButton.setText("Hazards ON");

                }
            }
        });
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (null == rxBleClient) rxBleClient = RxBleClient.create(this);

        devPrimary = rxBleClient.getBleDevice(getString(R.string.primary_stag_addr));
        connectionObservableP = prepConnObservableP();

        devSecondary = rxBleClient.getBleDevice(getString(R.string.secondary_stag_addr));
        connectionObservableS = prepConnObservableS();

        devRasp = rxBleClient.getBleDevice(getString(R.string.rasp_addr));
        connectionObservableR = prepConnObservableR();


    }

    public void onFrontLightClick(View view) {
        //Headlight RadioGroup
        boolean checked = ((RadioButton) view).isChecked();
        if(!checked) return;
        // Check which radio button was clicked
        switch(view.getId()) {
            case R.id.buttonfhigh:
                // Headlight high solid
                frontAutoMode = false;
                this.frontHigh();
                break;
            case R.id.buttonflow:
                // Headlight low solid
                frontAutoMode = false;
                this.frontLow();
                break;
            case R.id.buttonfflash:
                // Headlight Flash
                frontAutoMode = false;
                this.frontBlink();
                break;
            case R.id.buttonfoff:
                // Headlight off
                frontAutoMode = false;
                this.frontOff();
                break;
            case R.id.frontAuto:
                frontAutoMode = true;
                break;
        }
    }
    public void onRearLightClick(View view) {
        //Headlight RadioGroup
        boolean checked = ((RadioButton) view).isChecked();
        if(!checked) return;
        // Check which radio button was clicked
        switch(view.getId()) {
            case R.id.buttonron:
                // Rear light solid
                rearAutoMode = false;
                this.rearOn();
                break;
            case R.id.buttonroff:
                // Rear light off
                rearAutoMode = false;
                this.rearOff();
                break;
            case R.id.buttonrflash:
                    // Rear light Flash
                rearAutoMode = false;
                this.rearBlink();
                break;
            case R.id.rearAuto:
                rearAutoMode = true;
                break;
        }
    }
    public void onTurnSignalClick(View view) {
        //Headlight RadioGroup
        boolean checked = ((RadioButton) view).isChecked();
        if (!checked) return;
        // Check which radio button was clicked
        switch(view.getId()) {
            case R.id.buttonrright:
                // Turn right
                this.turnRight();
                break;
            case R.id.buttonrleft:
                // Turn left
                this.turnLeft();
                break;
            case R.id.buttonturnoff:
                // Turn signals off
                this.turnOff();
                break;
        }
    }
    //sent to Pi
    private void frontBlink() {
        writeRasp(DEV_FRONT, STATE_BLINK, bytes -> FRONT_LAST_STATE = STATE_BLINK);
    }

    private void frontLow() {
        writeRasp(DEV_FRONT, STATE_LOW, bytes -> FRONT_LAST_STATE = STATE_LOW);
    }

    private void frontHigh() {
        writeRasp(DEV_FRONT, STATE_ON, bytes -> FRONT_LAST_STATE = STATE_ON);
    }

    private void frontOff() {
        writeRasp(DEV_FRONT, STATE_OFF, bytes -> FRONT_LAST_STATE = STATE_OFF);
    }

    private void rearOn() {
        writeRasp(DEV_REAR, STATE_ON, bytes -> REAR_LAST_STATE = STATE_ON);
    }

    private void rearOff() {
        writeRasp(DEV_REAR, STATE_OFF, bytes -> REAR_LAST_STATE = STATE_OFF);
    }

    private void rearBlink() {
        writeRasp(DEV_REAR, STATE_BLINK, bytes -> REAR_LAST_STATE = STATE_BLINK);
    }

    private void turnRight() {
        writeRasp(DEV_TURN, STATE_RIGHT);
    }

    private void turnLeft() {
        writeRasp(DEV_TURN, STATE_LEFT);
    }

    private void turnOff() {
        writeRasp(DEV_TURN, STATE_OFF);
    }

    private void lock() {
        writeRasp(DEV_LOCK, STATE_ON);
    }

    private void unlock() {
        writeRasp(DEV_LOCK, STATE_OFF);
    }

    private void hazards() {
        writeRasp(DEV_ALL, STATE_ON, bytes -> {FRONT_LAST_STATE = STATE_BLINK; REAR_LAST_STATE = STATE_BLINK;});
        final RadioButton fflash = (RadioButton)findViewById(R.id.buttonfflash);
        final RadioButton turnsright = (RadioButton)findViewById(R.id.buttonrright);
        final RadioButton turnsleft = (RadioButton)findViewById(R.id.buttonrleft);
        final RadioButton rearflash = (RadioButton)findViewById(R.id.buttonrflash);
        fflash.setChecked(true);
        turnsright.setChecked(false);
        turnsleft.setChecked(false);
        rearflash.setChecked(true);
    }

    private void allOff() {
        writeRasp(DEV_ALL, STATE_OFF, bytes -> {FRONT_LAST_STATE = STATE_OFF; REAR_LAST_STATE = STATE_OFF;});
        final RadioButton foff = (RadioButton)findViewById(R.id.buttonfoff);
        final RadioButton turnsoff = (RadioButton)findViewById(R.id.buttonturnoff);
        final RadioButton rearoff = (RadioButton)findViewById(R.id.buttonroff);
        foff.setChecked(true);
        turnsoff.setChecked(true);
        rearoff.setChecked(true);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (null == mBluetoothAdapter || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            if (!isConnectedP()) {
                Observable<RxBleDeviceServices> servObvP = connectionObservableP.flatMap(RxBleConnection::discoverServices);
                servObvP
                        .flatMap(rxBleDeviceServices -> rxBleDeviceServices.getCharacteristic(uuid(R.string.uuid_mov_conf)))
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnSubscribe(() -> Log.v(TAG, "Connecting acc"))
                        .subscribe(
                                characteristic -> {
                                    Log.v(TAG, "Connection established acc " + characteristic.getUuid().toString());
                                    enableAcc(bytes -> setAccPeriod(bytes2 -> setNotifyAcc(null)));
                                },
                                this::onConnectionFailure,
                                this::onConnectionFinished
                        );
                servObvP
                        .flatMap(rxBleDeviceServices -> rxBleDeviceServices.getCharacteristic(uuid(R.string.uuid_opt_conf)))
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnSubscribe(() -> Log.v(TAG, "Connecting lux"))
                        .subscribe(
                                characteristic -> {
                                    Log.v(TAG, "Connection established lux " + characteristic.getUuid().toString());
                                    enableLux(bytes -> setLuxPeriod(bytes2 -> setNotifyLux(null)));
                                },
                                this::onConnectionFailure,
                                this::onConnectionFinished
                        );
            }
            if (!isConnectedS()) {
                connectionObservableS
                        .flatMap(RxBleConnection::discoverServices)
                        .flatMap(rxBleDeviceServices -> rxBleDeviceServices.getCharacteristic(uuid(R.string.uuid_mov_conf)))
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnSubscribe(() -> Log.v(TAG, "Connecting gyro"))
                        .subscribe(
                                characteristic -> {
                                    Log.v(TAG, "Connection established gyro " + characteristic.getUuid().toString());
                                    enableGyro(bytes -> setGyroPeriod(bytes2 -> setNotifyGyro(null)));
                                },
                                this::onConnectionFailure,
                                this::onConnectionFinished
                        );
            }
            if (!isConnectedR()) {
                connectionObservableR
                        .flatMap(RxBleConnection::discoverServices)
                        .flatMap(rxBleDeviceServices -> rxBleDeviceServices.getCharacteristic(uuid(R.string.uuid_light)))
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnSubscribe(() -> Log.v(TAG, "Connecting rasp"))
                        .subscribe(
                                characteristic -> {
                                    Log.v(TAG, "Connection established rasp" + characteristic.getUuid().toString());
                                    allOff();
                                },
                                this::onConnectionFailure,
                                this::onConnectionFinished
                        );
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private boolean isConnectedP() {
        return devPrimary.getConnectionState() == RxBleConnection.RxBleConnectionState.CONNECTED;
    }

    private boolean isConnectedS() {
        return devSecondary.getConnectionState() == RxBleConnection.RxBleConnectionState.CONNECTED;
    }

    private boolean isConnectedR() {
        return devRasp.getConnectionState() == RxBleConnection.RxBleConnectionState.CONNECTED;
    }

    private void write(Observable<RxBleConnection> obs, int id, byte[] value, Action1<byte[]> c) {
        obs
                .flatMap(rxBleConnection -> rxBleConnection.writeCharacteristic(uuid(id), value))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        bytes -> {
                            onWriteSuccess();
                            if (null != c) c.call(bytes);
                        },
                        this::onWriteFailure
                );
    }

    private void writeRasp(byte dev, byte state, Action1<byte[]> c) {
        byte[] value = new byte[]{dev, state};
        if (isConnectedR()) write(connectionObservableR, R.string.uuid_light, value, c);
        else Log.e(TAG, "writeRasp: not connected");
    }

    private void writeRasp(byte dev, byte state) {
        writeRasp(dev, state, null);
    }

    private void enableAcc(Action1<byte[]> c) {
        byte[] value = new byte[]{0x7F, 0x00};
        if (isConnectedP()) write(connectionObservableP, R.string.uuid_mov_conf, value, c);
        else Log.e(TAG, "enableAcc: not connected");
    }

    private void setAccPeriod(Action1<byte[]> c) {
        byte[] value = new byte[]{20};
        if (isConnectedP()) write(connectionObservableP, R.string.uuid_mov_peri, value, c);
        else Log.e(TAG, "setAccPeriod: not connected");
    }

    private void setNotifyAcc(Action0 c) {
        if (isConnectedP()) {
            connectionObservableP
                    .flatMap(rxBleConnection -> rxBleConnection.setupNotification(uuid(R.string.uuid_mov_data)))
                    .doOnNext(notificationObservable -> runOnUiThread(() -> notificationHasBeenSetUp(c)))
                    .flatMap(notificationObservable -> notificationObservable)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            this::onNotificationReceivedAcc,
                            this::onNotificationSetupFailure
                    );
        } else {
            Log.e(TAG, "setNotifyAcc: not connected");
        }
    }

    private void enableLux(Action1<byte[]> c) {
        byte[] value = new byte[]{0x01};
        if (isConnectedP()) write(connectionObservableP, R.string.uuid_opt_conf, value, c);
        else Log.e(TAG, "enableLux: not connected");
    }

    private void setLuxPeriod(Action1<byte[]> c) {
        byte[] value = new byte[]{20};
        if (isConnectedP()) write(connectionObservableP, R.string.uuid_opt_peri, value, c);
        else Log.e(TAG, "setLuxPeriod: not connected");
    }

    private void setNotifyLux(Action0 c) {
        if (isConnectedP()) {
            connectionObservableP
                    .flatMap(rxBleConnection -> rxBleConnection.setupNotification(uuid(R.string.uuid_opt_data)))
                    .doOnNext(notificationObservable -> runOnUiThread(() -> notificationHasBeenSetUp(c)))
                    .flatMap(notificationObservable -> notificationObservable)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            this::onNotificationReceivedLux,
                            this::onNotificationSetupFailure
                    );
        } else {
            Log.e(TAG, "setNotifyLux: not connected");
        }
    }

    private void enableGyro(Action1<byte[]> c) {
        byte[] value = new byte[]{0x7F, 0x00};
        if (isConnectedS()) write(connectionObservableS, R.string.uuid_mov_conf, value, c);
        else Log.e(TAG, "enableGyro: not connected");
    }

    private void setGyroPeriod(Action1<byte[]> c) {
        byte[] value = new byte[]{20};
        if (isConnectedS()) write(connectionObservableS, R.string.uuid_mov_peri, value, c);
        else Log.e(TAG, "setGyroPeriod: not connected");
    }

    private void setNotifyGyro(Action0 c) {
        if (isConnectedS()) {
            connectionObservableS
                    .flatMap(rxBleConnection -> rxBleConnection.setupNotification(uuid(R.string.uuid_mov_data)))
                    .doOnNext(notificationObservable -> runOnUiThread(() -> notificationHasBeenSetUp(c)))
                    .flatMap(notificationObservable -> notificationObservable)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            this::onNotificationReceivedGyro,
                            this::onNotificationSetupFailure
                    );
        } else {
            Log.e(TAG, "setNotifyGyro: not connected");
        }
    }

    private UUID uuid(int id) {
        return UUID.fromString(getString(id));
    }

    private void onConnectionFailure(Throwable throwable) {
        String msg = "Connection error: " + throwable;
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        Log.e(TAG, msg);
    }

    private void onConnectionFinished() {
        Log.v(TAG, "Connection Finished");
    }

    private void onWriteSuccess() {
        Log.v(TAG, "write success");
    }

    private void onWriteFailure(Throwable throwable) {
        String msg = "Write error: " + throwable;
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        Log.e(TAG, msg);
    }

    private void onNotificationReceivedAcc(byte[] bytes) {
        // unit: G
        final float SCALE = 4096.0f;
        double x = (((bytes[7] << 8) + bytes[6]) / SCALE) * -1;
        double y = ((bytes[9] << 8) + bytes[8]) / SCALE;
        double z = (((bytes[11] << 8) + bytes[10]) / SCALE) * -1;
        ((TextView) findViewById(R.id.sample_text)).setText(x + "\n" + y + "\n" + z);
        Log.v(TAG, "Acc Notification:\t" + x + "\t" + y + "\t" + z);
    }

    private void onNotificationReceivedLux(byte[] bytes) {
        // unit: Lux
        int sfloat = (((int) bytes[1] & 0xFF) << 8) + ((int) bytes[0] & 0xFF);
        int mantissa = sfloat & 0x0FFF;
        int exponent = (sfloat >> 12) & 0xFF;
        double magnitude = Math.pow(2.0f, exponent);
        double out = (mantissa * magnitude) / 100.0f;
        if(out < 30.0){
            if(frontAutoMode && FRONT_LAST_STATE != STATE_ON){
                frontHigh();

            }
            if(rearAutoMode && REAR_LAST_STATE != STATE_BLINK){
                rearBlink();

            }
        } else {
            if (frontAutoMode && FRONT_LAST_STATE != STATE_OFF) {
                frontOff();
            }
            if (rearAutoMode && REAR_LAST_STATE != STATE_OFF) {
                rearOff();
            }
        }
        Log.v(TAG, "Lux Notification:\t" + out);
    }

    private void onNotificationReceivedGyro(byte[] bytes) {
        // unit: degrees / sec
        final float SCALE = 128.0f;
        double x = ((bytes[1] << 8) + bytes[0]) / SCALE;
        double y = ((bytes[3] << 8) + bytes[2]) / SCALE;
        double z = ((bytes[5] << 8) + bytes[4]) / SCALE;
        ((TextView) findViewById(R.id.sample_text)).setText(x + "\n" + y + "\n" + z);
        Log.v(TAG, "Gyro Notification:\t" + x + "\t" + y + "\t" + z);
    }

    private void onNotificationSetupFailure(Throwable throwable) {
        String msg = "Notification setup error: " + throwable;
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        Log.e(TAG, msg);
    }

    private void notificationHasBeenSetUp(Action0 c) {
        Log.v(TAG, "Notification setup success");
        if (null != c) c.call();
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
//    public native String stringFromJNI();

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }
}
