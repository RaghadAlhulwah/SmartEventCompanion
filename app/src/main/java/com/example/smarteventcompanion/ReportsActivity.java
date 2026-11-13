package com.example.smarteventcompanion;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;

public class ReportsActivity extends AppCompatActivity {

    private DatabaseHelper databaseHelper;
    private TextView tvRegisteredCount;
    private TextView tvAttendanceCount;
    private TextView tvAttendanceEmpty;
    private ListView lvReportAttendance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        databaseHelper = new DatabaseHelper(this);
        tvRegisteredCount = findViewById(R.id.tvRegisteredCount);
        tvAttendanceCount = findViewById(R.id.tvAttendanceCount);
        tvAttendanceEmpty = findViewById(R.id.tvAttendanceEmpty);
        lvReportAttendance = findViewById(R.id.lvReportAttendance);

        findViewById(R.id.btnRefreshReport).setOnClickListener(v -> loadReportData());
        setupBottomNavigation();
        loadReportData();
    }

    private void setupBottomNavigation() {
        BottomNavigationView navigation = findViewById(R.id.bottomNavigation);
        navigation.setSelectedItemId(R.id.nav_reports);
        navigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_reports) return true;
            if (id == R.id.nav_home) {
                openScreen(MainActivity.class);
                return true;
            }
            if (id == R.id.nav_registrations) {
                openScreen(MyEventsActivity.class);
                return true;
            }
            if (id == R.id.nav_bluetooth) {
                openScreen(BluetoothActivity.class);
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadReportData();
    }

    private void loadReportData() {
        try {
            ArrayList<Event> registrations = databaseHelper.getRegisteredEvents();
            ArrayList<String> attendance = databaseHelper.getAttendanceRecords();

            tvRegisteredCount.setText(String.valueOf(registrations.size()));
            tvAttendanceCount.setText(String.valueOf(attendance.size()));

            if (attendance.isEmpty()) {
                tvAttendanceEmpty.setVisibility(View.VISIBLE);
                lvReportAttendance.setVisibility(View.GONE);
            } else {
                tvAttendanceEmpty.setVisibility(View.GONE);
                lvReportAttendance.setVisibility(View.VISIBLE);
                lvReportAttendance.setAdapter(new ArrayAdapter<>(
                        this, android.R.layout.simple_list_item_1, attendance));
            }
        } catch (IllegalStateException e) {
            Toast.makeText(this, "Unable to load report data.", Toast.LENGTH_LONG).show();
        }
    }

    private void openScreen(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
    }

    @Override
    protected void onDestroy() {
        if (databaseHelper != null) databaseHelper.close();
        super.onDestroy();
    }
}
