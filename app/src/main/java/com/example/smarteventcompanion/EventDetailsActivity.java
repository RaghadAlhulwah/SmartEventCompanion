package com.example.smarteventcompanion;

import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class EventDetailsActivity extends AppCompatActivity {

    private Event event;
    private DatabaseHelper databaseHelper;
    private Button btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_details);

        View root = findViewById(R.id.detailsRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
            );
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        databaseHelper = new DatabaseHelper(this);
        btnRegister = findViewById(R.id.btnRegister);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnHome).setOnClickListener(v -> openHome());
        findViewById(R.id.btnMyEvents).setOnClickListener(v -> {
            Intent intent = new Intent(this, MyEventsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        TextView tvEventName = findViewById(R.id.tvEventName);
        TextView tvEventDate = findViewById(R.id.tvEventDate);
        TextView tvEventLocation = findViewById(R.id.tvEventLocation);
        TextView tvEventDescription =
                findViewById(R.id.tvEventDescription);

        Intent intent = getIntent();
        int id = intent.getIntExtra(MainActivity.EXTRA_EVENT_ID, -1);
        String name =
                intent.getStringExtra(MainActivity.EXTRA_EVENT_NAME);
        String date =
                intent.getStringExtra(MainActivity.EXTRA_EVENT_DATE);
        String location =
                intent.getStringExtra(MainActivity.EXTRA_EVENT_LOCATION);
        String description =
                intent.getStringExtra(MainActivity.EXTRA_EVENT_DESCRIPTION);

        if (id <= 0 || isMissing(name) || isMissing(date)
                || isMissing(location) || description == null) {
            tvEventName.setText("Event details unavailable");
            btnRegister.setEnabled(false);
            return;
        }

        event = new Event(id, name, date, location, description);

        tvEventName.setText(event.getName());
        tvEventDate.setText(event.getDate());
        tvEventLocation.setText(event.getLocation());
        tvEventDescription.setText(event.getDescription());

        btnRegister.setOnClickListener(v -> registerEvent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRegistrationButton();
    }

    private void updateRegistrationButton() {
        if (event == null) {
            btnRegister.setEnabled(false);
            return;
        }

        try {
            boolean registered =
                    databaseHelper.isEventRegistered(event.getId());

            btnRegister.setText(
                    registered ? "Registered" : "Register for Event"
            );
            btnRegister.setEnabled(!registered);

        } catch (SQLiteException e) {
            btnRegister.setText("Retry Registration");
            btnRegister.setEnabled(true);
            showToast("Unable to check registration. Please try again.");
        }
    }

    private void registerEvent() {
        if (event == null) {
            return;
        }

        try {
            if (databaseHelper.isEventRegistered(event.getId())) {
                updateRegistrationButton();
                showToast("You are already registered");
                return;
            }

            if (databaseHelper.registerEvent(event)) {
                updateRegistrationButton();
                showToast("Registration successful");
            } else {
                showToast("Registration failed. Please try again.");
            }

        } catch (SQLiteException e) {
            showToast("Unable to access the database. Please try again.");
        }
    }

    private void openHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private boolean isMissing(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (databaseHelper != null) {
            databaseHelper.close();
        }
        super.onDestroy();
    }
}