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
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
    private static final int ACTION_TOGGLE_BLUETOOTH = 1;
    private static final int ACTION_START_SERVER = 2;
    private static final int ACTION_LOAD_DEVICES = 3;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothServerSocket serverSocket;
    private volatile boolean serverRunning = false;
    private int pendingAction = ACTION_NONE;

    private View hubPanel;
    private View participantPanel;
    private View organizerPanel;
    private View attendancePanel;
    private TextView tvBtStatus;
    private TextView tvOrganizerHint;
    private android.widget.Button btnEnableBt;
    private EditText etParticipantName;
    private Spinner spRegisteredEvents;
    private ListView lvDevices;
    private ListView lvAttendance;

    private ArrayList<BluetoothDevice> bluetoothDevices = new ArrayList<>();
    private ArrayList<Event> registeredEvents = new ArrayList<>();
    private ArrayAdapter<String> deviceAdapter;
    private ArrayAdapter<String> attendanceAdapter;
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);
        View root = findViewById(R.id.bluetoothRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });

        hubPanel = findViewById(R.id.hubPanel);
        participantPanel = findViewById(R.id.participantPanel);
        organizerPanel = findViewById(R.id.organizerPanel);
        attendancePanel = findViewById(R.id.attendancePanel);
        tvBtStatus = findViewById(R.id.tvBtStatus);
        tvOrganizerHint = findViewById(R.id.tvOrganizerHint);
        btnEnableBt = findViewById(R.id.btnEnableBt);
        etParticipantName = findViewById(R.id.etParticipantName);
        spRegisteredEvents = findViewById(R.id.spRegisteredEvents);
        lvDevices = findViewById(R.id.lvDevices);
        lvAttendance = findViewById(R.id.lvAttendance);

        databaseHelper = new DatabaseHelper(this);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        deviceAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, new ArrayList<>());
        attendanceAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, new ArrayList<>());
        lvDevices.setAdapter(deviceAdapter);
        lvAttendance.setAdapter(attendanceAdapter);

        findViewById(R.id.btnParticipantMode)
                .setOnClickListener(v -> showPanel(participantPanel));
        findViewById(R.id.btnOrganizerMode)
                .setOnClickListener(v -> showPanel(organizerPanel));
        findViewById(R.id.btnAttendanceDashboard).setOnClickListener(v -> {
            showPanel(attendancePanel);
            loadAttendanceRecords();
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> {
            if (hubPanel.getVisibility() == View.VISIBLE) {
                finish();
            } else {
                showHub();
            }
        });
        setupBottomNavigation();

        btnEnableBt.setOnClickListener(v -> runBluetoothAction(ACTION_TOGGLE_BLUETOOTH));
        findViewById(R.id.btnVisible).setOnClickListener(v -> makeDeviceDiscoverable());
        findViewById(R.id.btnPairedDevices)
                .setOnClickListener(v -> runBluetoothAction(ACTION_LOAD_DEVICES));
        findViewById(R.id.btnStartServer)
                .setOnClickListener(v -> runBluetoothAction(ACTION_START_SERVER));
        findViewById(R.id.btnStopServer).setOnClickListener(v -> stopServer());
        findViewById(R.id.btnRefreshAttendance).setOnClickListener(v -> loadAttendanceRecords());

        lvDevices.setOnItemClickListener((parent, view, position, id) -> {
            if (position < bluetoothDevices.size()) {
                sendAttendanceData(bluetoothDevices.get(position));
            }
        });

        loadRegisteredEvents();
        showHub();
        refreshBluetoothStatus();
    }


    private void setupBottomNavigation() {
        BottomNavigationView navigation = findViewById(R.id.bottomNavigation);
        navigation.setSelectedItemId(R.id.nav_bluetooth);
        navigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_bluetooth) return true;
            if (id == R.id.nav_home) {
                openScreen(MainActivity.class);
                return true;
            }
            if (id == R.id.nav_registrations) {
                openScreen(MyEventsActivity.class);
                return true;
            }
            if (id == R.id.nav_reports) {
                openScreen(ReportsActivity.class);
                return true;
            }
            return false;
        });
    }

    private void showHub() {
        hubPanel.setVisibility(View.VISIBLE);
        participantPanel.setVisibility(View.GONE);
        organizerPanel.setVisibility(View.GONE);
        attendancePanel.setVisibility(View.GONE);
        refreshBluetoothStatus();
    }

    private void showPanel(View panel) {
        hubPanel.setVisibility(View.GONE);
        participantPanel.setVisibility(panel == participantPanel ? View.VISIBLE : View.GONE);
        organizerPanel.setVisibility(panel == organizerPanel ? View.VISIBLE : View.GONE);
        attendancePanel.setVisibility(panel == attendancePanel ? View.VISIBLE : View.GONE);
        refreshBluetoothStatus();
        if (panel == participantPanel) loadRegisteredEvents();
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
            } else {
                etParticipantName.setEnabled(true);
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
                    this, new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    REQUEST_CONNECT_PERMISSION);
            return;
        }
        performAction(action);
    }

    private void performAction(int action) {
        switch (action) {
            case ACTION_TOGGLE_BLUETOOTH:
                toggleBluetooth();
                break;
            case ACTION_START_SERVER:
                startServerListening();
                break;
            case ACTION_LOAD_DEVICES:
                loadPairedDevices();
                break;
            default:
                break;
        }
    }

    private void toggleBluetooth() {
        try {
            if (!bluetoothAdapter.isEnabled()) {
                // Android displays its own confirmation prompt before enabling Bluetooth.
                startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            } else {
                // Modern Android does not let ordinary apps switch Bluetooth off silently.
                // Open the system Bluetooth page so the user can turn it off there.
                if (serverRunning) stopServer();
                Toast.makeText(this,
                        "Use the Bluetooth switch in Android Settings to turn it off.",
                        Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            }
        } catch (SecurityException e) {
            showStatus("Bluetooth permission is required for this action.");
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
                    this, new String[]{Manifest.permission.BLUETOOTH_ADVERTISE},
                    REQUEST_ADVERTISE_PERMISSION);
            return;
        }
        requestDiscoverableNow();
    }

    private void requestDiscoverableNow() {
        try {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        } catch (SecurityException e) {
            showStatus("Bluetooth advertising permission is required.");
        }
    }

    private void refreshBluetoothStatus() {
        if (bluetoothAdapter == null) {
            showStatus("Bluetooth is not supported on this device.");
            btnEnableBt.setText("Bluetooth unavailable");
            btnEnableBt.setEnabled(false);
            return;
        }
        btnEnableBt.setEnabled(true);
        if (!hasConnectPermission()) {
            showStatus("Allow Bluetooth permission to continue.");
            btnEnableBt.setText("Check Bluetooth");
            return;
        }
        try {
            boolean enabled = bluetoothAdapter.isEnabled();
            btnEnableBt.setText(enabled ? "Turn Bluetooth Off" : "Turn Bluetooth On");
            showStatus(enabled
                    ? "Bluetooth is ON. Your phone is ready to connect."
                    : "Bluetooth is OFF. Turn it on to use check-in.");
            if (tvOrganizerHint != null) {
                tvOrganizerHint.setText(enabled
                        ? "Keep this screen open while participants send their check-ins."
                        : "Turn Bluetooth on first, then start receiving check-ins.");
            }
        } catch (SecurityException e) {
            showStatus("Bluetooth permission is required.");
        }
    }

    private void showStatus(String message) {
        tvBtStatus.setText(message);
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
            showStatus("Choose the paired organizer phone from the list.");
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
            showStatus("Organizer mode is already active and waiting for participants.");
            return;
        }

        serverRunning = true;
        showStatus("Starting organizer receiver… keep this page open.");
        new Thread(() -> {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, APP_UUID);
                runOnUiThread(() -> showStatus(
                        "Organizer is active. Waiting for participant check-ins…"));
                while (serverRunning) {
                    BluetoothSocket clientSocket = serverSocket.accept();
                    if (clientSocket != null) receiveAttendance(clientSocket);
                }
            } catch (IOException | SecurityException e) {
                if (serverRunning) {
                    runOnUiThread(() -> showStatus(
                            "Could not receive connections. Check pairing, visibility and permissions."));
                }
            } finally {
                serverRunning = false;
                closeServerSocket();
            }
        }, "attendance-server").start();
    }

    private void stopServer() {
        serverRunning = false;
        closeServerSocket();
        showStatus("Organizer receiver stopped.");
    }

    private void receiveAttendance(BluetoothSocket socket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            String message = reader.readLine();
            if (message == null || message.trim().isEmpty()) {
                showReceiveResult("Received an empty attendance message.");
                return;
            }

            JSONObject payload = new JSONObject(message);
            String participantName = payload.optString("participantName", "").trim();
            int eventId = payload.optInt("eventId", -1);
            String response;

            if (participantName.isEmpty() || eventId <= 0) {
                response = "ERROR";
                showReceiveResult("Invalid attendance data received.");
            } else {
                String attendanceTime = new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
                boolean saved = databaseHelper.recordAttendance(
                        participantName, eventId, attendanceTime);

                if (saved) {
                    response = "SAVED";
                    showReceiveResult("Attendance saved for " + participantName
                            + " (Event " + eventId + ").");
                } else if (databaseHelper.isAttendanceRecorded(participantName, eventId)) {
                    response = "DUPLICATE";
                    showReceiveResult(participantName
                            + " is already checked in for this event.");
                } else {
                    response = "ERROR";
                    showReceiveResult("Attendance could not be saved. Please try again.");
                }
            }

            OutputStream responseStream = socket.getOutputStream();
            responseStream.write((response + "\n").getBytes(StandardCharsets.UTF_8));
            responseStream.flush();
        } catch (IOException | JSONException | IllegalStateException e) {
            showReceiveResult("Could not read or save the attendance message.");
        } finally {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    private void showReceiveResult(String message) {
        runOnUiThread(() -> {
            showStatus(message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            if (attendancePanel.getVisibility() == View.VISIBLE) loadAttendanceRecords();
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

                BufferedReader responseReader = new BufferedReader(new InputStreamReader(
                        socket.getInputStream(), StandardCharsets.UTF_8));
                String response = responseReader.readLine();

                if ("SAVED".equals(response)) {
                    runOnUiThread(() -> {
                        showStatus("The organizer confirmed your attendance.");
                        Toast.makeText(this, "Attendance confirmed!", Toast.LENGTH_LONG).show();
                    });
                } else if ("DUPLICATE".equals(response)) {
                    runOnUiThread(() -> Toast.makeText(
                            this, "You are already checked in for this event.",
                            Toast.LENGTH_LONG).show());
                } else {
                    runOnUiThread(() -> Toast.makeText(
                            this, "The organizer could not save your attendance.",
                            Toast.LENGTH_LONG).show());
                }
            } catch (IOException | JSONException | SecurityException e) {
                runOnUiThread(() -> Toast.makeText(
                        this,
                        "Connection failed. Ensure the organizer is listening and both phones are paired.",
                        Toast.LENGTH_LONG).show());
            } finally {
                if (socket != null) {
                    try { socket.close(); } catch (IOException ignored) { }
                }
            }
        }, "attendance-client").start();
    }

    private void loadAttendanceRecords() {
        try {
            attendanceAdapter.clear();
            ArrayList<String> records = databaseHelper.getAttendanceRecords();
            if (records.isEmpty()) {
                attendanceAdapter.add("No attendance records yet.");
            } else {
                attendanceAdapter.addAll(records);
            }
            attendanceAdapter.notifyDataSetChanged();
        } catch (IllegalStateException e) {
            Toast.makeText(this, "Unable to load attendance records.", Toast.LENGTH_LONG).show();
        }
    }

    private void closeServerSocket() {
        BluetoothServerSocket current = serverSocket;
        serverSocket = null;
        if (current != null) {
            try { current.close(); } catch (IOException ignored) { }
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
