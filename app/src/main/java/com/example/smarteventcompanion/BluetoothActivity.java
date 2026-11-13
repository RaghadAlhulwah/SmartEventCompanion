package com.example.smarteventcompanion;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class BluetoothActivity extends AppCompatActivity {

    private static final UUID APP_UUID =
            UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    private static final String APP_NAME = "SmartEventCompanion";
    private static final int REQUEST_CONNECT_PERMISSION = 200;
    private static final int REQUEST_ADVERTISE_PERMISSION = 201;
    private static final int ACTION_NONE = 0;
    private static final int ACTION_ENABLE = 1;
    private static final int ACTION_START_SERVER = 2;
    private static final int ACTION_LOAD_DEVICES = 3;
    private static final int ACTION_DISCOVERABLE = 4;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothServerSocket serverSocket;
    private volatile boolean serverRunning = false;
    private int pendingAction = ACTION_NONE;

    private TextView tvBtStatus;
    private EditText etParticipantName;
    private Spinner spRegisteredEvents;
    private ListView lvDevices;
    private ArrayList<BluetoothDevice> bluetoothDevices = new ArrayList<>();
    private ArrayList<Event> registeredEvents = new ArrayList<>();
    private ArrayAdapter<String> deviceAdapter;
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        tvBtStatus = findViewById(R.id.tvBtStatus);
        etParticipantName = findViewById(R.id.etParticipantName);
        spRegisteredEvents = findViewById(R.id.spRegisteredEvents);
        lvDevices = findViewById(R.id.lvDevices);

        Button btnEnableBt = findViewById(R.id.btnEnableBt);
        Button btnVisible = findViewById(R.id.btnVisible);
        Button btnPairedDevices = findViewById(R.id.btnPairedDevices);
        Button btnStartServer = findViewById(R.id.btnStartServer);
        Button btnViewAttendance = findViewById(R.id.btnViewAttendance);

        databaseHelper = new DatabaseHelper(this);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        deviceAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, new ArrayList<>());
        lvDevices.setAdapter(deviceAdapter);

        loadRegisteredEvents();
        refreshBluetoothStatus();

        btnEnableBt.setOnClickListener(v -> enableBluetooth());
        btnVisible.setOnClickListener(v -> makeDeviceDiscoverable());
        btnPairedDevices.setOnClickListener(v -> runBluetoothAction(ACTION_LOAD_DEVICES));
        btnStartServer.setOnClickListener(v -> runBluetoothAction(ACTION_START_SERVER));
        btnViewAttendance.setOnClickListener(v -> showAttendanceRecords());

        lvDevices.setOnItemClickListener((parent, view, position, id) -> {
            if (position < bluetoothDevices.size()) {
                sendAttendanceData(bluetoothDevices.get(position));
            }
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnHome).setOnClickListener(v -> openScreen(MainActivity.class));
        findViewById(R.id.btnMyEvents).setOnClickListener(v -> openScreen(MyEventsActivity.class));
    }

    private void loadRegisteredEvents() {
        try {
            registeredEvents = databaseHelper.getRegisteredEvents();
            ArrayList<String> eventNames = new ArrayList<>();
            for (Event event : registeredEvents) {
                eventNames.add(event.getName() + " (ID: " + event.getId() + ")");
            }

            if (eventNames.isEmpty()) {
                eventNames.add("Register for an event first");
                etParticipantName.setEnabled(false);
            }

            ArrayAdapter<String> eventAdapter = new ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_item, eventNames);
            eventAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spRegisteredEvents.setAdapter(eventAdapter);
        } catch (IllegalStateException e) {
            Toast.makeText(this, "Could not load registered events.", Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasConnectPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void runBluetoothAction(int action) {
        if (bluetoothAdapter == null) {
            showStatus("This device does not support Bluetooth.");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasConnectPermission()) {
            pendingAction = action;
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    REQUEST_CONNECT_PERMISSION);
            return;
        }
        performAction(action);
    }

    private void performAction(int action) {
        switch (action) {
            case ACTION_ENABLE:
                enableBluetoothNow();
                break;
            case ACTION_START_SERVER:
                startServerListening();
                break;
            case ACTION_LOAD_DEVICES:
                loadPairedDevices();
                break;
            case ACTION_DISCOVERABLE:
                requestDiscoverableNow();
                break;
            default:
                break;
        }
    }

    private void enableBluetooth() {
        runBluetoothAction(ACTION_ENABLE);
    }

    private void enableBluetoothNow() {
        if (bluetoothAdapter == null) {
            showStatus("This device does not support Bluetooth.");
            return;
        }
        try {
            if (!bluetoothAdapter.isEnabled()) {
                startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            } else {
                Toast.makeText(this, "Bluetooth is already on.", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            showStatus("Bluetooth permission is required to turn it on.");
        }
    }

    private void makeDeviceDiscoverable() {
        if (bluetoothAdapter == null) {
            showStatus("This device does not support Bluetooth.");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.BLUETOOTH_ADVERTISE},
                    REQUEST_ADVERTISE_PERMISSION);
            return;
        }
        requestDiscoverableNow();
    }

    private void requestDiscoverableNow() {
        try {
            Intent discoverableIntent =
                    new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(
                    BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        } catch (SecurityException e) {
            showStatus("Bluetooth advertising permission is required.");
        }
    }

    private void refreshBluetoothStatus() {
        if (bluetoothAdapter == null) {
            showStatus("Bluetooth is not supported on this device.");
            return;
        }
        if (!hasConnectPermission()) {
            showStatus("Allow Bluetooth permission to continue.");
            return;
        }
        try {
            showStatus(bluetoothAdapter.isEnabled()
                    ? "Bluetooth is ON — ready to connect."
                    : "Bluetooth is OFF — tap Turn On.");
        } catch (SecurityException e) {
            showStatus("Bluetooth permission is required.");
        }
    }

    private void showStatus(String message) {
        tvBtStatus.setText("Status: " + message);
    }

    private void loadPairedDevices() {
        if (bluetoothAdapter == null) return;
        if (!hasConnectPermission()) {
            runBluetoothAction(ACTION_LOAD_DEVICES);
            return;
        }
        try {
            if (!bluetoothAdapter.isEnabled()) {
                showStatus("Turn Bluetooth on before loading paired devices.");
                return;
            }

            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            bluetoothDevices.clear();
            deviceAdapter.clear();

            if (pairedDevices == null || pairedDevices.isEmpty()) {
                showStatus("No paired devices found. Pair both phones in Android Settings first.");
                return;
            }

            for (BluetoothDevice device : pairedDevices) {
                bluetoothDevices.add(device);
                String name = device.getName();
                deviceAdapter.add((name == null ? "Bluetooth device" : name)
                        + "\n" + device.getAddress());
            }
            deviceAdapter.notifyDataSetChanged();
            showStatus("Select a paired organizer device below to send attendance.");
        } catch (SecurityException e) {
            showStatus("Bluetooth Connect permission was not granted.");
        }
    }

    private void startServerListening() {
        if (bluetoothAdapter == null) return;
        if (!hasConnectPermission()) {
            runBluetoothAction(ACTION_START_SERVER);
            return;
        }
        try {
            if (!bluetoothAdapter.isEnabled()) {
                showStatus("Turn Bluetooth on before starting organizer mode.");
                return;
            }
        } catch (SecurityException e) {
            showStatus("Bluetooth permission is required.");
            return;
        }

        if (serverRunning) {
            showStatus("Organizer mode is already running. Waiting for participants…");
            return;
        }

        serverRunning = true;
        showStatus("Organizer mode is starting… keep this screen open.");
        new Thread(() -> {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                        APP_NAME, APP_UUID);
                runOnUiThread(() ->
                        showStatus("Organizer mode is active. Waiting for check-ins…"));

                while (serverRunning) {
                    BluetoothSocket clientSocket = serverSocket.accept();
                    if (clientSocket != null) {
                        receiveAttendance(clientSocket);
                    }
                }
            } catch (IOException | SecurityException e) {
                if (serverRunning) {
                    runOnUiThread(() ->
                            showStatus("Could not receive connections. Check pairing and permissions."));
                }
            } finally {
                serverRunning = false;
                closeServerSocket();
            }
        }, "attendance-server").start();
    }

    private void receiveAttendance(BluetoothSocket socket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            String message = reader.readLine();
            if (message == null || message.trim().isEmpty()) {
                showReceiveResult("Received an empty attendance message.", false);
                return;
            }

            JSONObject payload = new JSONObject(message);
            String participantName = payload.optString("participantName", "").trim();
            int eventId = payload.optInt("eventId", -1);

            if (participantName.isEmpty() || eventId <= 0) {
                showReceiveResult("Invalid attendance data received.", false);
                return;
            }

            String attendanceTime = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

            if (databaseHelper.isAttendanceRecorded(participantName, eventId)) {
                showReceiveResult(participantName + " is already checked in for event "
                        + eventId + ".", false);
                return;
            }

            boolean saved = databaseHelper.recordAttendance(
                    participantName, eventId, attendanceTime);
            showReceiveResult(saved
                    ? "Attendance saved for " + participantName + " (Event " + eventId + ")."
                    : "Attendance could not be saved. Please try again.", saved);

        } catch (IOException | JSONException | IllegalStateException e) {
            showReceiveResult("Could not read or save the attendance message.", false);
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void showReceiveResult(String message, boolean success) {
        runOnUiThread(() -> {
            showStatus(message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void sendAttendanceData(BluetoothDevice device) {
        String participantName = etParticipantName.getText().toString().trim();
        int selectedPosition = spRegisteredEvents.getSelectedItemPosition();

        if (participantName.isEmpty()) {
            etParticipantName.setError("Enter your name");
            etParticipantName.requestFocus();
            return;
        }
        if (registeredEvents.isEmpty() || selectedPosition < 0
                || selectedPosition >= registeredEvents.size()) {
            Toast.makeText(this, "Register for an event before checking in.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Event selectedEvent = registeredEvents.get(selectedPosition);
        new Thread(() -> {
            BluetoothSocket socket = null;
            try {
                if (!hasConnectPermission()) {
                    runOnUiThread(() -> showStatus("Bluetooth Connect permission is required."));
                    return;
                }

                socket = device.createRfcommSocketToServiceRecord(APP_UUID);
                socket.connect();

                JSONObject payload = new JSONObject();
                payload.put("participantName", participantName);
                payload.put("eventId", selectedEvent.getId());
                payload.put("eventName", selectedEvent.getName());

                OutputStream output = socket.getOutputStream();
                output.write((payload.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                output.flush();

                runOnUiThread(() -> Toast.makeText(
                        this, "Check-in sent to organizer.", Toast.LENGTH_LONG).show());
            } catch (IOException | JSONException | SecurityException e) {
                runOnUiThread(() -> Toast.makeText(
                        this,
                        "Connection failed. Make sure the organizer is listening and both phones are paired.",
                        Toast.LENGTH_LONG).show());
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }, "attendance-client").start();
    }

    private void showAttendanceRecords() {
        try {
            ArrayList<String> records = databaseHelper.getAttendanceRecords();
            if (records.isEmpty()) {
                new AlertDialog.Builder(this)
                        .setTitle("Attendance Records")
                        .setMessage("No attendance has been recorded on this device yet.")
                        .setPositiveButton("Close", null)
                        .show();
                return;
            }
            String[] items = records.toArray(new String[0]);
            new AlertDialog.Builder(this)
                    .setTitle("Saved Attendance")
                    .setItems(items, (dialog, which) -> { })
                    .setPositiveButton("Close", null)
                    .show();
        } catch (IllegalStateException e) {
            Toast.makeText(this, "Unable to load attendance records.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void closeServerSocket() {
        BluetoothServerSocket current = serverSocket;
        serverSocket = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void openScreen(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CONNECT_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                int action = pendingAction;
                pendingAction = ACTION_NONE;
                performAction(action);
            } else {
                pendingAction = ACTION_NONE;
                Toast.makeText(this, "Bluetooth Connect permission is needed for this action.",
                        Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_ADVERTISE_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestDiscoverableNow();
            } else {
                Toast.makeText(this, "Bluetooth advertising permission was denied.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshBluetoothStatus();
    }

    @Override
    protected void onDestroy() {
        serverRunning = false;
        closeServerSocket();
        if (databaseHelper != null) databaseHelper.close();
        super.onDestroy();
    }
}
