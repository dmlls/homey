package com.mobilesw.homey;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class BluetoothFunctions extends AppCompatActivity {

    private Switch mSwitchLed;
    private Switch mSwitchFan;
    private TextView mTemperature;
    private TextView mHumidity;
    private TextView mAutoLightsInfo;
    private TextView mAutoFanInfo;

    private SharedPreferences sharedPreferences;

    FirebaseFirestore db = FirebaseFirestore.getInstance(); //Initalize Firestore object
    final CollectionReference dbLogs = db.collection("UserLogs"); //Call the collection UserLogs in firestore
    final FirebaseUser currentFirebaseUser = FirebaseAuth.getInstance().getCurrentUser() ; //Get the current user logged in
    private Date date;
    private UserLog userlog;
    private SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    private ConnectedThread mConnectedThread;
    private BluetoothAdapter mBtAdapter = null;
    private BluetoothSocket mBtSocket = null;

    private String mBtAddress = "00:19:07:00:3C:C1"; // MAC address of our Bluetooth module
    private String mBtName = "HC-06"; // name of our Bluetooth module

    private final String TAG = MainActivity.class.getSimpleName();
    private Handler mHandler; // main handler that will receive callback notifications

    private final static int REQUEST_ENABLE_BT = 1; // used to request the user to turn on Bluetooth

    // codes for identifying shared types between calling functions
    private final static int MESSAGE_READ = 1; // used in bluetooth handler to identify message update
    private final static int CONNECTION_STATUS = 2; // used in bluetooth handler to identify message status

    // tag codes for Bluetooth writing
    private final static String CONNECTED = "0";
    private final static String LIGHTS_OFF = "1";
    private final static String LIGHTS_ON = "2";
    private final static String FAN_OFF = "3";
    private final static String FAN_ON = "4";
    private final static String AUTO_LIGHTS = "5"; // auto-activate lights
    private final static String NO_AUTO_LIGHTS = "6"; // disable auto-activate lights
    private final static String AUTO_FAN = "7"; // auto-activate fan
    private final static String NO_AUTO_FAN = "8"; // disable auto-activate fan
    private final static String NO_AUTO_LIGHTS_FAN = "9"; // disable auto-activate lights & fan

    // tag codes for Bluetooth reading
    private final static String TEMP_AND_HUMIDITY = "TH";
    private final static String AUTO_LIGHTS_OFF = "0L";
    private final static String AUTO_LIGHTS_ON = "1L";
    private final static String AUTO_FAN_OFF = "0F";
    private final static String AUTO_FAN_ON = "1F";


    /**
     * "Random" unique identifier
     */
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_functions);

        mSwitchLed = findViewById(R.id.lights_switch);
        mSwitchFan = findViewById(R.id.fan_switch);
        mTemperature = findViewById(R.id.temperature);
        mHumidity = findViewById(R.id.humidity);
        mAutoLightsInfo = findViewById(R.id.auto_lights_info);
        mAutoFanInfo = findViewById(R.id.auto_fan_info);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        getSupportActionBar().setTitle("Arduino functions");

        mHandler = new Handler() {
            public void handleMessage(android.os.Message msg) {
                switch (msg.what) {
                    case MESSAGE_READ:
                        boolean autoActivate = sharedPreferences.getBoolean("auto-activate", true);
                        boolean autoLights = sharedPreferences.getBoolean("auto_activate_lights", true);
                        boolean autoFan = sharedPreferences.getBoolean("auto_activate_fan", true);
                        String readMessage = null;
                        String tag = null;
                        String contentMessage = null; // message without tag code
                        try {
                            readMessage = new String((byte[]) msg.obj, "UTF-8");
                            tag = readMessage.substring(0, 2);
                            contentMessage = readMessage.substring(2); // remove tag code
                            Log.d(TAG, contentMessage);
                        } catch (UnsupportedEncodingException e) {
                            Log.e(TAG, "Encoding error", e);
                        } catch (IndexOutOfBoundsException e) {} // data read incorrectly

                        switch (tag) {
                            case TEMP_AND_HUMIDITY:
                                String[] values = contentMessage.split("\\s+"); // split by space: temp and humidity
                                if (contentMessage.matches("[^A-Za-z]+") && values.length == 2) { // discard incorrect data
                                    mTemperature.setText(getString(R.string.temperature_display, values[0]));
                                    mHumidity.setText(getString(R.string.humidity_display, values[1]));
                                }
                                break;
                            case AUTO_LIGHTS_OFF:
                                mSwitchLed.setChecked(false);
                                mAutoLightsInfo.setVisibility(View.GONE);

                                // TODO: log(lights, OFF, auto)
                                date = new Date();
                                userlog = new UserLog(currentFirebaseUser.getUid(), "The lights have been automatically turned off", formatter.format(date));
                                dbLogs.add(userlog).addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                                    @Override
                                    public void onSuccess(DocumentReference documentReference) {
                                        Toast.makeText(BluetoothFunctions.this, "Lights Off Log has been added", Toast.LENGTH_LONG).show();
                                    }
                                }).addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Toast.makeText(BluetoothFunctions.this, e.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                });
                                break;
                            case AUTO_FAN_OFF:
                                mSwitchFan.setChecked(false);
                                mAutoFanInfo.setVisibility(View.GONE);

                                // TODO: log(fan, OFF, auto)
                                date = new Date();
                                userlog = new UserLog(currentFirebaseUser.getUid(), "The fan has been automatically turned off", formatter.format(date));
                                dbLogs.add(userlog).addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                                    @Override
                                    public void onSuccess(DocumentReference documentReference) {
                                        Toast.makeText(BluetoothFunctions.this, "Fan Off Log has been added", Toast.LENGTH_LONG).show();
                                    }
                                }).addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Toast.makeText(BluetoothFunctions.this, e.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                });
                                break;
                        }
                        if (autoActivate) {
                            if (autoLights && tag.equals(AUTO_LIGHTS_ON)) {
                                mSwitchLed.setChecked(true);
                                mAutoLightsInfo.setVisibility(View.VISIBLE);
                                // TODO: log(lights, ON, auto)
                                date = new Date();
                                userlog = new UserLog(currentFirebaseUser.getUid(), "The lights have been automatically turned on", formatter.format(date));
                                dbLogs.add(userlog).addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                                    @Override
                                    public void onSuccess(DocumentReference documentReference) {
                                        Toast.makeText(BluetoothFunctions.this, "Lights On Log has been added", Toast.LENGTH_LONG).show();
                                    }
                                }).addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Toast.makeText(BluetoothFunctions.this, e.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                });
                            } else if (autoFan && tag.equals(AUTO_FAN_ON)) {
                                mSwitchFan.setChecked(true);
                                mAutoFanInfo.setVisibility(View.VISIBLE);
                                // TODO: log(fan, ON, auto)
                                date = new Date();
                                userlog = new UserLog(currentFirebaseUser.getUid(), "The fan has been automatically turned on", formatter.format(date));
                                dbLogs.add(userlog).addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                                    @Override
                                    public void onSuccess(DocumentReference documentReference) {
                                        Toast.makeText(BluetoothFunctions.this, "Fan On Log has been added", Toast.LENGTH_LONG).show();
                                    }
                                }).addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Toast.makeText(BluetoothFunctions.this, e.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }
                        break;
                    case CONNECTION_STATUS:
                        String status = (msg.arg1 == 1) ? "Connected to " + mBtName : "Unable to connect";
                        Toast.makeText(getApplicationContext(), status, Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        };

        mBtAdapter = BluetoothAdapter.getDefaultAdapter(); // get a handle on the bluetooth radio

        if (mBtAdapter == null) {
            Toast.makeText(getApplicationContext(), "Your device doesn't support Bluetooth", Toast.LENGTH_LONG).show();
        } else {
            if (!mBtAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(mBtAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                connectBluetoothDevice();
            }
        }
    }


    private void connectBluetoothDevice() {
        Toast.makeText(getApplicationContext(), "Connecting...", Toast.LENGTH_SHORT).show();
        new Thread() {
            public void run() {

                BluetoothDevice device = mBtAdapter.getRemoteDevice(mBtAddress);

                try {
                    mBtSocket = createBluetoothSocket(device);
                    Log.e(TAG, "Socket: " + mBtSocket.toString());
                    mBtSocket.connect();
                    mConnectedThread = new ConnectedThread(mBtSocket);
                    mConnectedThread.start();
                    mConnectedThread.write(CONNECTED);
                    mHandler.obtainMessage(CONNECTION_STATUS, 1, -1, mBtName).sendToTarget();
                } catch (IOException e) {
                    try {
                        mBtSocket.close();
                        Log.e(TAG, "Socket creation failed", e);
                        mHandler.obtainMessage(CONNECTION_STATUS, -1, -1).sendToTarget();
                    } catch (IOException ex2) {}
                }
            }
        }.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                connectBluetoothDevice();
            } else {
                finish(); // terminate activity
            }
        }
    }


    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            return device.createInsecureRfcommSocketToServiceRecord(BTMODULEUUID);
        } catch (Exception e) {
            Log.e(TAG, "Could not create Insecure RFComm Connection.", e);
            return device.createRfcommSocketToServiceRecord(BTMODULEUUID);
        }
    }

    public void switchLights(View view) {
        if (mConnectedThread != null) { // make sure the thread has been created
            String action = (mSwitchLed.isChecked()) ? LIGHTS_ON : LIGHTS_OFF; // switch active?
            mConnectedThread.write(action);
            // TODO: log(lights, action, manual) // action tells you whether the lights were turned ON or OFF
            date = new Date();
            userlog = new UserLog(currentFirebaseUser.getUid(), "The lights have been manually turned off", formatter.format(date));
            dbLogs.add(userlog).addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                @Override
                public void onSuccess(DocumentReference documentReference) {
                    Toast.makeText(BluetoothFunctions.this, "Lights On Log has been added", Toast.LENGTH_LONG).show();
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(BluetoothFunctions.this, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
            if (action.equals(LIGHTS_OFF)) {
                mAutoLightsInfo.setVisibility(View.GONE);
            }
        }
    }

    public void switchFan(View view) {
        if (mConnectedThread != null) { // make sure the thread has been created
            String action = (mSwitchFan.isChecked()) ? FAN_ON : FAN_OFF; // switch active?
            mConnectedThread.write(action);
            // TODO: log(fan, action, manual) // action tells you whether the fan was turned ON or OFF
            date = new Date();
            userlog = new UserLog(currentFirebaseUser.getUid(), "The fan have been automatically turned off", formatter.format(date));
            dbLogs.add(userlog).addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                @Override
                public void onSuccess(DocumentReference documentReference) {
                    Toast.makeText(BluetoothFunctions.this, "Fan Off Log has been added", Toast.LENGTH_LONG).show();
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(BluetoothFunctions.this, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
            if (action.equals(FAN_OFF)) {
                mAutoFanInfo.setVisibility(View.GONE);
            }
        }
    }

    public void goToSettings(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        boolean autoActivate = sharedPreferences.getBoolean("auto-activate", true);
        boolean autoLights = sharedPreferences.getBoolean("auto_activate_lights", true);
        boolean autoFan = sharedPreferences.getBoolean("auto_activate_fan", true);
        if (autoActivate) {
            if (autoLights) {
                mConnectedThread.write(AUTO_LIGHTS);
            } else {
                mConnectedThread.write(NO_AUTO_LIGHTS);
            }
            if (autoFan) {
                mConnectedThread.write(AUTO_FAN);
            } else {
                mConnectedThread.write(NO_AUTO_FAN);
            }
        } else {
            mConnectedThread.write(NO_AUTO_LIGHTS_FAN);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mConnectedThread != null) {
            mConnectedThread.shutdown();
        }
        if (mBtSocket != null) {
            try {
                mBtSocket.close();
            } catch (IOException e) {}
        }
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
    }

    private class ConnectedThread extends Thread {

        private final BluetoothSocket mmBtSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmBtSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "Unable to get Input or Output Stream", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Could not get Input or Output Stream", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer;  // buffer store for the stream
            int bytes; // bytes returned from read()

            // keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    if(mmInStream.available() != 0) { // read from the InputStream
                        buffer = new byte[16];
                        SystemClock.sleep(100); // pause and wait for rest of data.
                        bytes = mmInStream.available(); // how many bytes are ready to be read?
                        bytes = mmInStream.read(buffer, 0, bytes);
                        mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                                .sendToTarget(); // send the obtained bytes to the UI activity
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String input) {
            byte[] bytes = input.getBytes(); // converts entered String into bytes
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "Exception when writing", e);
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void shutdown() {
            try {
                mmBtSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Exception when closing socket", e);
            }
        }
    }
}
