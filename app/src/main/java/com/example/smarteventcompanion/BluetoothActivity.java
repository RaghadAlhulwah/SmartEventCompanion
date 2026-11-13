package com.example.smarteventcompanion;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class BluetoothActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private TextView tvBtStatus;
    private ListView lvDevices;
    private ArrayList<String> deviceList;
    private ArrayAdapter<String> adapter;
    private ArrayList<BluetoothDevice> bluetoothDevices;

    // UUID ثابت وموحد للاتصال بين أجهزة المشروع
    private static final UUID MY_UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    private static final String APP_NAME = "SmartEventCompanion";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        tvBtStatus = findViewById(R.id.tvBtStatus);
        lvDevices = findViewById(R.id.lvDevices);
        Button btnEnableBt = findViewById(R.id.btnEnableBt);
        Button btnVisible = findViewById(R.id.btnVisible);
        Button btnPairedDevices = findViewById(R.id.btnPairedDevices);
        Button btnBack = findViewById(R.id.btnBack);
        Button btnHome = findViewById(R.id.btnHome);
        Button btnMyEvents = findViewById(R.id.btnMyEvents);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        deviceList = new ArrayList<>();
        bluetoothDevices = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        lvDevices.setAdapter(adapter);

        checkBluetoothStatus();

        // زر تشغيل البلوتوث
        btnEnableBt.setOnClickListener(v -> {
            if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                startActivity(enableBtIntent);
            } else {
                Toast.makeText(this, "Bluetooth is already on", Toast.LENGTH_SHORT).show();
            }
        });

        // زر جعل الجهاز مرئيًا
        btnVisible.setOnClickListener(v -> {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        });

        // زر عرض الأجهزة المقترنة وبدء الاستماع كـ Server في الخلفية
        btnPairedDevices.setOnClickListener(v -> {
            loadPairedDevices();
            startServerListening(); // يبدأ الجهاز يستقبل الاتصالات من المشاركين الآخرين
        });

        // عند النقر على جهاز من القائمة، يتم إرسال بيانات الحضور إليه (Client)
        lvDevices.setOnItemClickListener((parent, view, position, id) -> {
            if (position < bluetoothDevices.size()) {
                BluetoothDevice selectedDevice = bluetoothDevices.get(position);
                // إرسال بيانات تجريبية للحضور: (اسم المشارك، رقم الفعالية، الوقت)
                sendAttendanceData(selectedDevice, "Raghad Alhulwah", "Event_101");
            }
        });

        // أزرار التنقل السفلية والعلوية
        btnBack.setOnClickListener(v -> finish());
        btnHome.setOnClickListener(v -> openScreen("MainActivity"));
        btnMyEvents.setOnClickListener(v -> openScreen("MyEventsActivity"));
    }

    private void checkBluetoothStatus() {
        if (bluetoothAdapter == null) {
            tvBtStatus.setText("Status: Bluetooth not supported");
        } else if (bluetoothAdapter.isEnabled()) {
            tvBtStatus.setText("Status: Bluetooth is ON (Ready)");
        } else {
            tvBtStatus.setText("Status: Bluetooth is OFF");
        }
    }

    private void loadPairedDevices() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetooth Connect permission required", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        deviceList.clear();
        bluetoothDevices.clear();

        if (pairedDevices != null && !pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                deviceList.add(device.getName() + "\n" + device.getAddress());
                bluetoothDevices.add(device);
            }
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Paired devices loaded", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "No paired devices found", Toast.LENGTH_SHORT).show();
        }
    }

    // 1. دور المنظم (Server): يستقبل الحضور من الأجهزة القريبة
    private void startServerListening() {
        new Thread(() -> {
            BluetoothServerSocket serverSocket = null;
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID);
                    BluetoothSocket socket = serverSocket.accept(); // ينتظر اتصال المشارك

                    if (socket != null) {
                        InputStream inputStream = socket.getInputStream();
                        byte[] buffer = new byte[1024];
                        int bytes = inputStream.read(buffer);
                        String receivedData = new String(buffer, 0, bytes);

                        // عرض رسالة نجاح واستلام بيانات الحضور
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Attendance Received: " + receivedData, Toast.LENGTH_LONG).show();
                            // ملاحظة: هنا سيتم لاحقاً استدعاء دالة DatabaseHelper لحفظ الحضور في SQLite
                        });

                        socket.close();
                        serverSocket.close();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    // 2. دور المشارك (Client): يرسل بيانات حضوره لجهاز المنظم
    private void sendAttendanceData(BluetoothDevice device, String participantName, String eventId) {
        new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    BluetoothSocket socket = device.createRfcommSocketToServiceRecord(MY_UUID);
                    bluetoothAdapter.cancelDiscovery();
                    socket.connect(); // الاتصال بالمنظم

                    OutputStream outputStream = socket.getOutputStream();
                    String attendanceString = participantName + "," + eventId + "," + System.currentTimeMillis();
                    outputStream.write(attendanceString.getBytes());

                    runOnUiThread(() -> Toast.makeText(this, "Attendance Sent Successfully!", Toast.LENGTH_SHORT).show());

                    socket.close();
                }
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "Connection Failed. Make sure receiver is listening.", Toast.LENGTH_LONG).show());
                e.printStackTrace();
            }
        }).start();
    }

    private void openScreen(String activityName) {
        try {
            Intent intent = new Intent();
            intent.setClassName(this, getPackageName() + "." + activityName);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Screen not available", Toast.LENGTH_SHORT).show();
        }
    }
}