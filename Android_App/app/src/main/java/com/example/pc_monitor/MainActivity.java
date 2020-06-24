package com.example.pc_monitor;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import java.io.IOException;
import java.util.Calendar;
import java.util.Objects;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    // Global variables for bluetooth
    private static final int REQUEST_ENABLE_BT = 1;
    public static final String MacAddress = "00:14:03:05:F2:3D";
    public Button BtsleepWakeBtn;
    BluetoothAdapter bluetoothAdapter;
    public static BluetoothDevice btDevice;  // holds the Mac address of the desired connection
    public static BluetoothSocket btSocket;  // Used to manage the BT connection once connected to device

    public TcpClient mTcpClient;
    private static final String wakeKey = "ZOyMzWG9IJWa2xu6";
    private static final String updateKey = "H83ENi9gzq8lEXxt";
    public Button sleepWakeBtn;
    public TextView statusTv;
    public Button statusBtn;
    public TextView tempTv;
    public TextView connectionTv;
    public Button setTimeBtn;
    public static Switch timeSwitch;
    public static TextView timeStatusTv;
    public PendingIntent wakeIntent;
    ConnectTask task;
    SharedPreferences sharedPref;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Initialize UI Elements
        sleepWakeBtn = findViewById(R.id.SleepWakeBtn);
        statusTv = findViewById(R.id.statusTv);
        tempTv = findViewById(R.id.tempTv);
        connectionTv = findViewById(R.id.connectionTv);
        statusBtn = findViewById(R.id.statusBtn);
        setTimeBtn = findViewById(R.id.setTimeBtn);
        timeSwitch = findViewById(R.id.timeSwitch);
        timeStatusTv = findViewById(R.id.timeStatusTV);

        // Restoring previous state from saved preferences
        sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        String status = sharedPref.getString("Status", "Status: Sleeping");
        String temp = sharedPref.getString("Temp", "Temperature: 33°C");
        String timeText = sharedPref.getString("timeText", "Please set a wakeup time");
        Boolean isChecked = sharedPref.getBoolean("isChecked",  false);
        timeSwitch.setChecked(isChecked);
        timeStatusTv.setText(timeText);
        statusTv.setText(status);
        tempTv.setText(temp);
        if(isChecked) {
            timeStatusTv.setVisibility(View.VISIBLE);
        }
        else {
            timeStatusTv.setVisibility(View.INVISIBLE);
        }

        AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);

        // Execute WiFi Thread
        task = new ConnectTask();
        task.execute("");

        // Allow time for app to connect to arduino
        new CountDownTimer(4000, 1000) {
            public void onTick(long millisUntilFinished) {
                sleepWakeBtn.setClickable(false);
                statusBtn.setClickable(false);
            }
            public void onFinish() {
                connectionTv.setVisibility(View.INVISIBLE);
                sleepWakeBtn.setClickable(true);
                statusBtn.setClickable(true);
                Toast.makeText(getApplicationContext(), "Connected to Arduino!", Toast.LENGTH_SHORT).show();
            }
        }.start();




        // Listener for sleep/Wake Button
        // ONClick, it will send a message through the TCP Client, or set up a connection if disconnected
        sleepWakeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //sends the message to the server
                if (task.isCancelled()) {
                    Toast.makeText(getApplicationContext(), "Connection Timed Out, Reconnecting...", Toast.LENGTH_SHORT).show();
                    connectionTv.setVisibility(View.VISIBLE);
                    task = new ConnectTask();
                    task.execute("");
                    new CountDownTimer(4000, 1000) {
                        public void onTick(long millisUntilFinished) {
                            sleepWakeBtn.setClickable(false);
                            statusBtn.setClickable(false);
                        }
                        public void onFinish() {
                            connectionTv.setVisibility(View.INVISIBLE);
                            sleepWakeBtn.setClickable(true);
                            statusBtn.setClickable(true);
                            mTcpClient.sendMessage(wakeKey);
                            Toast.makeText(getApplicationContext(), "Command Sent!", Toast.LENGTH_SHORT).show();
                        }
                    }.start();
                }
                else {
                    mTcpClient.sendMessage(wakeKey);
                    Toast.makeText(getApplicationContext(), "Command Sent!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // OnClickListener for update status button
        // On Click, will update the status with the data sent by the arduino
        // If connection is disconnected, connect to the arduino and request updated data
        statusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //sends the message to the server
                if (task.isCancelled()) {
                    Toast.makeText(getApplicationContext(), "Connection Timed Out, Reconnecting...", Toast.LENGTH_SHORT).show();
                    connectionTv.setVisibility(View.VISIBLE);
                    task = new ConnectTask();
                    task.execute("");
                    new CountDownTimer(4000, 1000) {
                        public void onTick(long millisUntilFinished) {
                            sleepWakeBtn.setClickable(false);
                            statusBtn.setClickable(false);
                        }
                        public void onFinish() {
                            connectionTv.setVisibility(View.INVISIBLE);
                            sleepWakeBtn.setClickable(true);
                            statusBtn.setClickable(true);
                            mTcpClient.sendMessage(updateKey);
                            Toast.makeText(getApplicationContext(), "Updated!", Toast.LENGTH_SHORT).show();
                        }
                    }.start();
                }
                else {
                    mTcpClient.sendMessage(updateKey);
                    Toast.makeText(getApplicationContext(), "Updated!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // On click listener for setting a routine wakeup time
        // Shows a dialog allowing the user to select a time
        setTimeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimePickerDialog(v);
            }
        });

        // Enables the alarm and textview describing the alarm when checked
        timeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor editor = sharedPref.edit();
                if(isChecked) {
                    timeStatusTv.setVisibility(View.VISIBLE);
                    editor.putBoolean("isChecked", true);
                }
                else {
                    timeStatusTv.setVisibility(View.INVISIBLE);
                    editor.putBoolean("isChecked", false);
                }
                editor.commit();
            }
        });

//        BtsleepWakeBtn = findViewById(R.id.BtBtn);
//        if (bluetoothAdapter == null) {
//            // Device doesn't support Bluetooth
//        }
//        else {  // Device supports bluetooth
//            if (!bluetoothAdapter.isEnabled()) {  // request bluetooth on if currently off
//                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
//                if (REQUEST_ENABLE_BT == RESULT_CANCELED) {
//                    // if user denied bluetooth
//                }
//            }
//            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
//
//            if (pairedDevices.size() > 0) {
//                // There are paired devices. Get the name and address of each paired device.
//                for (BluetoothDevice device : pairedDevices) {
//                    if(device.getName().equals("DSD TECH HC-05")) {
//                       btDevice = device;
//                    }
//                }
//            }
//            ConnectThread connectThread = new ConnectThread(btDevice);
//            connectThread.start();
//        }

//        BtsleepWakeBtn.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if(btSocket != null) {
//                    try {
//                        OutputStream out = btSocket.getOutputStream();
//                        out.write('1');
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//                else {
//                    Toast.makeText(getApplicationContext(), "Bluetooth Not Set Up Yet", Toast.LENGTH_SHORT).show();
//                }
//            }
//        });
    }

    void cancelTask() {
        task.cancel(true);
    }

    static void checkSwitch() {
        if(timeSwitch.isChecked()) {
            timeSwitch.setChecked(false);
        }
        else {
            timeSwitch.setChecked(true);
        }
    }

    public void showTimePickerDialog(View v) {
        DialogFragment newFragment = new TimePickerFragment();
        newFragment.show(getSupportFragmentManager(), "timePicker");
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice HC05;  // Desired bluetooth device to connect to
        private static final String TAG = "PC_Monitor";
        private final UUID MY_UUID;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            HC05 = device;
            String UU_ID = "00001101-0000-1000-8000-00805f9b34fb";
            MY_UUID = UUID.fromString(UU_ID);
            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = HC05.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter.cancelDiscovery();
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
                btSocket = mmSocket;
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
            }
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class ConnectTask extends AsyncTask<String, String, TcpClient> {

        @Override
        protected TcpClient doInBackground(String... message) {
            //we create a TCPClient object
            mTcpClient = new TcpClient(new TcpClient.OnMessageReceived() {
                @Override
                //here the messageReceived method is implemented
                public void messageReceived(String message) {
                    //this method calls the onProgressUpdate
                    publishProgress(message);
                }
            });
            mTcpClient.run();
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            //response received from server
            // 33 for sleeping
            // 30 for awake
            SharedPreferences.Editor editor = sharedPref.edit();
            int length = values[0].length();
            if(length == 33) {
                statusTv.setText(values[0].substring(0,16));
                tempTv.setText(values[0].substring(16,33));
                editor.putString("Status", values[0].substring(0,16));
                editor.putString("Temp", values[0].substring(16,33));
                editor.commit();
            }
            else if(length == 30) {
                statusTv.setText(values[0].substring(0,13));
                tempTv.setText(values[0].substring(13,30));
                editor.putString("Status", values[0].substring(0,13));
                editor.putString("Temp", values[0].substring(13,30));
                editor.commit();
            }
            else {
                statusTv.setText(values[0]);
            }
            Log.d("test", "response " + values[0]);
            //process server response here...
            mTcpClient.stopClient();
            cancelTask();
        }
    }

    public static class TimePickerFragment extends DialogFragment
            implements TimePickerDialog.OnTimeSetListener {
        SharedPreferences sharedPref;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the current time as the default values for the picker
            final Calendar c = Calendar.getInstance();
            int hour;
            int minute;
            sharedPref = Objects.requireNonNull(getActivity()).getPreferences(Context.MODE_PRIVATE);
            if (sharedPref.contains("hour")) {
                hour = sharedPref.getInt("hour",12);
                minute = sharedPref.getInt("minute",0);
            }
            else {
                hour = c.get(Calendar.HOUR_OF_DAY);
                minute = c.get(Calendar.MINUTE);
            }
            // Create a new instance of TimePickerDialog and return it
            return new TimePickerDialog(getActivity(), this, hour, minute,
                    DateFormat.is24HourFormat(getActivity()));
        }

        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            // Do something with the time chosen by the user
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putInt("hour", hourOfDay);
            editor.putInt("minute", minute);
            String timeText;
            if(!timeSwitch.isChecked()) {
                checkSwitch();
            }
            int hour = hourOfDay;
            if (hourOfDay > 12) {
                hour = hourOfDay - 12;
                timeText =  "Waking up PC at " + hour + ":" + minute + " PM every day";
            }
            else {
                timeText =  "Waking up PC at " + hour + ":" + minute + " AM every day";
            }
            timeStatusTv.setText(timeText);
            editor.putString("timeText", timeText);
            editor.apply();
        }
    }
}

