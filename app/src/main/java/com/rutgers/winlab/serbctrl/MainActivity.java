package com.rutgers.winlab.serbctrl;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.SmsManager;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.RxBleDeviceServices;
import com.polidea.rxandroidble.utils.ConnectionSharingAdapter;
import static com.trello.rxlifecycle.android.ActivityEvent.PAUSE;
import com.trello.rxlifecycle.components.support.RxAppCompatActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.subjects.PublishSubject;
import rx.functions.Action0;
import rx.functions.Action1;



public class MainActivity extends RxAppCompatActivity{
    Button sendHelpBtn;
    String phoneNo;
    String message;
    private static final String TAG = "MainActivity";
    private BluetoothAdapter mBluetoothAdapter;
    private static final int MY_PERMISSIONS_REQUEST_SEND_SMS = 0;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERM_LOCATION = 2;
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
    private String emergencyNum = "";
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

    private FusedLocationProviderClient mFusedLocationClient;

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
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);


        //sendBtn = (Button) findViewById(R.id.btnSendSMS);
        //txtphoneNo = (EditText) findViewById(R.id.editText);
        //txtMessage = (EditText) findViewById(R.id.editText2);
        sendHelpBtn = (Button) findViewById(R.id.needhelp);


        sendHelpBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Toast.makeText(MainActivity.this ,
                        "Sending", Toast.LENGTH_LONG).show();
                Log.v(TAG, "Location Sending Message Test 5");
                sendSMSMessage();
                Log.v(TAG, "Location Sending Message Test 6");
            }
        });

        final Button emergencyChange = (Button) findViewById(R.id.emergencyChange);
        final Button button = (Button) findViewById(R.id.buttonlock);
        final Button hazButton = (Button) findViewById(R.id.buttonhazard);
        final Button turnOff = (Button) findViewById(R.id.turnOff);
        final Button turnRight = (Button) findViewById(R.id.turnRight);
        final Button turnLeft = (Button) findViewById(R.id.turnLeft);

        emergencyChange.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                //add new emergency contact number to save in internal storage
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Emergency Contact Number");
                final EditText input = new EditText(MainActivity.this);
                //input box for user to add number
                input.setInputType(InputType.TYPE_CLASS_TEXT);
                builder.setView(input);

                // Set up the buttons
                builder.setPositiveButton("SAVE", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        emergencyNum = input.getText().toString();
                        writeToFile(emergencyNum, MainActivity.this);


                    }
                });
                builder.setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        Log.v(TAG, "Emergency Change Dismissed");
                    }
                });

                builder.show();
            }
        });
        turnOff.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                MainActivity.this.turnOff();
                turnOff.setBackgroundColor(Color.RED);
                turnRight.setBackgroundColor(Color.LTGRAY);
                turnLeft.setBackgroundColor(Color.LTGRAY);

            }
        });
        turnRight.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                MainActivity.this.turnOff();
                MainActivity.this.turnRight();
                turnOff.setBackgroundColor(Color.LTGRAY);
                turnLeft.setBackgroundColor(Color.LTGRAY);
                turnRight.setBackgroundColor(Color.RED);
            }
        });
        turnLeft.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                MainActivity.this.turnOff();
                MainActivity.this.turnLeft();
                turnOff.setBackgroundColor(Color.LTGRAY);
                turnLeft.setBackgroundColor(Color.RED);
                turnRight.setBackgroundColor(Color.LTGRAY);
            }
        });
        button.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (isLocked == false) {
                            lock();
                            button.setText("UNLOCK");
                            isLocked = true;
                        } else {
                            unlock();
                            isLocked = false;
                            button.setText("LOCK");
                        }
                    }
                });
        hazButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                frontAutoMode = false;
                rearAutoMode  = false;
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

    private void sendSMSMessage() {
        Log.v(TAG, "Location Sending Message Test 1");
        String number = readFromFile(MainActivity.this);
        phoneNo = number;
        message = "Please send help! I've gotten into a biking accident! My location is ";
        // should have permissions at this point
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERM_LOCATION);
        }
        Log.v(TAG, "Location Sending Message Test 2");

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS},
                    MY_PERMISSIONS_REQUEST_SEND_SMS);
        }
        Log.v(TAG, "Location Sending Message Test 3");
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                Location loc = result.getLastLocation();
                String url = "https://www.google.com/maps/place/" + loc.getLatitude() + "," + loc.getLongitude();
                Log.v(TAG, "Location: " + url);
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(phoneNo, null, message + url, null, null);
                Toast.makeText(getApplicationContext(), "SMS sent.",
                        Toast.LENGTH_LONG).show();
                mFusedLocationClient.removeLocationUpdates(this);
            }
        }, null);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_SEND_SMS: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    Toast.makeText(this,
                            "SMS permissions are required", Toast.LENGTH_LONG).show();
                    return;
                }
                break;
            }
            case REQUEST_PERM_LOCATION: {
                if (grantResults.length > 1
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    Toast.makeText(this,
                            "Location permissions are required", Toast.LENGTH_LONG).show();

                    return;
                }
                break;
            }
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
        final RadioButton rearflash = (RadioButton)findViewById(R.id.buttonrflash);
        fflash.setChecked(true);
        rearflash.setChecked(true);
    }

    private void allOff() {
        writeRasp(DEV_ALL, STATE_OFF, bytes -> {FRONT_LAST_STATE = STATE_OFF; REAR_LAST_STATE = STATE_OFF;});
        final RadioButton foff = (RadioButton)findViewById(R.id.buttonfoff);

        final RadioButton rearoff = (RadioButton)findViewById(R.id.buttonroff);
        foff.setChecked(true);
        rearoff.setChecked(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERM_LOCATION);
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS},
                    MY_PERMISSIONS_REQUEST_SEND_SMS);
        }
        if(readFromFile(MainActivity.this )== "") {
            //add new emergency contact number to save in internal storage
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Emergency Contact Number");
            final EditText input = new EditText(this);
            //input box for user to add number
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            builder.setView(input);

            // Set up the buttons
            builder.setPositiveButton("SAVE", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    emergencyNum = input.getText().toString();
                    writeToFile(emergencyNum, MainActivity.this);


                }
            });
            builder.show();
        }


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

    private void writeToFile(String data, Context context) {
        try {
            Log.v(TAG, "Writing to file");
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput("config.txt", Context.MODE_PRIVATE));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    private String readFromFile(Context context) {

        String ret = "";

        try {
            InputStream inputStream = context.openFileInput("config.txt");

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                StringBuilder stringBuilder = new StringBuilder();

                while ( (receiveString = bufferedReader.readLine()) != null ) {
                    stringBuilder.append(receiveString);
                }

                inputStream.close();
                ret = stringBuilder.toString();
            }
        }
        catch (FileNotFoundException e) {
            Log.e("login activity", "File not found: " + e.toString());
        } catch (IOException e) {
            Log.e("login activity", "Can not read file: " + e.toString());
        }

        return ret;
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
       // ((TextView) findViewById(R.id.sample_text)).setText(x + "\n" + y + "\n" + z);
        if(x < -3){

            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setCancelable(true);
            alert.setTitle("EMERGENCY");
            alert.setMessage("I detected an crash. Would you like to send for help?");
            alert.setPositiveButton("Send",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            sendSMSMessage();
                           Toast.makeText(MainActivity.this,
                                    "Sending", Toast.LENGTH_LONG).show();
                            Log.v(TAG, "Emergency detected and confirmed, sending alert");
                        }
                    });
            alert.setNegativeButton("I'm OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    Log.v(TAG, "Emergency Dismissed");
                }
            });

            AlertDialog dialog = alert.create();
            dialog.show();
        }
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
        //All gyro data below
        //x + "\n" + y + "\n" + z + "\n"
        //only display RPM
        ((TextView) findViewById(R.id.sample_text)).setText("RPM " + (int) -z/6);
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
