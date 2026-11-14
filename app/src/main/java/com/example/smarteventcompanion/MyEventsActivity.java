package com.example.smarteventcompanion;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class MyEventsActivity extends AppCompatActivity {

    private static final String SUMMARY_FILE = "registered_events_summary.txt";

    private DatabaseHelper databaseHelper;
    private ListView lvMyEvents;
    private TextView tvEmptyMessage;
    private View emptyState;
    private Button btnBrowseEvents;
    private Button btnRetry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_events);
        View root = findViewById(R.id.myEventsRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });

        lvMyEvents = findViewById(R.id.lvMyEvents);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        emptyState = findViewById(R.id.emptyState);
        btnBrowseEvents = findViewById(R.id.btnBrowseEvents);
        btnRetry = findViewById(R.id.btnRetry);
        databaseHelper = new DatabaseHelper(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnBrowseEvents.setOnClickListener(v -> openHome());
        btnRetry.setOnClickListener(v -> loadRegisteredEvents());
        findViewById(R.id.btnSaveSummary).setOnClickListener(v -> saveSummary());
        findViewById(R.id.btnViewSummary).setOnClickListener(v -> viewSummary());

        setupBottomNavigation();
    }

    private void setupBottomNavigation() {
        BottomNavigationView navigation = findViewById(R.id.bottomNavigation);
        navigation.setSelectedItemId(R.id.nav_registrations);
        navigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_registrations) return true;
            if (id == R.id.nav_home) {
                openScreen(MainActivity.class);
                return true;
            }
            if (id == R.id.nav_bluetooth) {
                openScreen(BluetoothActivity.class);
                return true;
            }
            if (id == R.id.nav_reports) {
                openScreen(ReportsActivity.class);
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRegisteredEvents();
    }

    private void loadRegisteredEvents() {
        try {
            ArrayList<Event> events = databaseHelper.getRegisteredEvents();
            if (events.isEmpty()) {
                showMessage("No registered events yet.\nExplore campus events and reserve your spot.", false);
                return;
            }

            lvMyEvents.setAdapter(new RegisteredEventAdapter(events));
            lvMyEvents.setOnItemClickListener((parent, view, position, id) ->
                    openDetails(events.get(position)));

            emptyState.setVisibility(View.GONE);
            lvMyEvents.setVisibility(View.VISIBLE);
        } catch (IllegalStateException e) {
            showMessage("Unable to load your events.\nPlease try again.", true);
        }
    }

    private void showMessage(String message, boolean error) {
        lvMyEvents.setVisibility(View.GONE);
        lvMyEvents.setAdapter(null);
        tvEmptyMessage.setText(message);
        btnRetry.setVisibility(error ? View.VISIBLE : View.GONE);
        btnBrowseEvents.setVisibility(error ? View.GONE : View.VISIBLE);
        emptyState.setVisibility(View.VISIBLE);
    }

    private class RegisteredEventAdapter extends ArrayAdapter<Event> {
        RegisteredEventAdapter(ArrayList<Event> events) {
            super(MyEventsActivity.this, 0, events);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(MyEventsActivity.this)
                        .inflate(R.layout.event_registration_card, parent, false);
            }

            Event event = getItem(position);
            if (event == null) return row;

            ((TextView) row.findViewById(R.id.tvRegistrationName)).setText(event.getName());
            ((TextView) row.findViewById(R.id.tvRegistrationDate)).setText(event.getDate());
            ((TextView) row.findViewById(R.id.tvRegistrationLocation)).setText(event.getLocation());

            row.findViewById(R.id.btnViewRegistrationDetails)
                    .setOnClickListener(v -> openDetails(event));
            row.findViewById(R.id.btnCancelRegistration)
                    .setOnClickListener(v -> confirmCancelRegistration(event));
            return row;
        }
    }

    private void confirmCancelRegistration(Event event) {
        new AlertDialog.Builder(this)
                .setTitle("Cancel registration?")
                .setMessage("Are you sure you want to cancel your registration for "
                        + event.getName() + "?")
                .setNegativeButton("Keep registration", null)
                .setPositiveButton("Cancel registration", (dialog, which) -> {
                    boolean removed = databaseHelper.cancelRegistration(event.getId());
                    if (removed) {
                        Toast.makeText(this, "Registration cancelled.", Toast.LENGTH_SHORT).show();
                        loadRegisteredEvents();
                    } else {
                        Toast.makeText(this, "Could not cancel this registration. Please try again.",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void openDetails(Event event) {
        Intent intent = new Intent(this, EventDetailsActivity.class);
        intent.putExtra(MainActivity.EXTRA_EVENT_ID, event.getId());
        intent.putExtra(MainActivity.EXTRA_EVENT_NAME, event.getName());
        intent.putExtra(MainActivity.EXTRA_EVENT_DATE, event.getDate());
        intent.putExtra(MainActivity.EXTRA_EVENT_LOCATION, event.getLocation());
        intent.putExtra(MainActivity.EXTRA_EVENT_DESCRIPTION, event.getDescription());
        startActivity(intent);
    }

    private void openHome() {
        openScreen(MainActivity.class);
    }

    private void openScreen(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
    }

    private void saveSummary() {
        try {
            ArrayList<Event> events = databaseHelper.getRegisteredEvents();
            if (events.isEmpty()) {
                Toast.makeText(this, "No registered events to save.", Toast.LENGTH_SHORT).show();
                return;
            }

            StringBuilder summary = new StringBuilder();
            summary.append("My Registered Events\n====================\n\n");
            for (Event event : events) {
                summary.append("Event: ").append(event.getName()).append("\n")
                        .append("Date: ").append(event.getDate()).append("\n")
                        .append("Location: ").append(event.getLocation()).append("\n\n");
            }

            try (FileOutputStream output = openFileOutput(SUMMARY_FILE, MODE_PRIVATE)) {
                output.write(summary.toString().getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(this, "Summary saved successfully.", Toast.LENGTH_SHORT).show();
        } catch (IOException | IllegalStateException
                 | android.database.sqlite.SQLiteException e) {
            Toast.makeText(this, "Unable to save the summary. Please try again.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void viewSummary() {
        StringBuilder summary = new StringBuilder();
        try (FileInputStream input = openFileInput(SUMMARY_FILE);
             InputStreamReader inputReader = new InputStreamReader(input, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(inputReader)) {
            String line;
            while ((line = reader.readLine()) != null) summary.append(line).append("\n");

            if (summary.length() == 0) {
                Toast.makeText(this, "The saved summary is empty.", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Saved Events Summary")
                    .setMessage(summary.toString())
                    .setPositiveButton("Close", null)
                    .show();
        } catch (FileNotFoundException e) {
            Toast.makeText(this, "No summary found. Please save one first.",
                    Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Unable to read the summary. Please try again.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (databaseHelper != null) databaseHelper.close();
        super.onDestroy();
    }
}
