package com.example.smarteventcompanion;
import android.content.Intent;
import android.os.Bundle;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
            );
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        ViewCompat.requestApplyInsets(root);

        lvMyEvents = findViewById(R.id.lvMyEvents);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        emptyState = findViewById(R.id.emptyState);
        btnBrowseEvents = findViewById(R.id.btnBrowseEvents);
        btnRetry = findViewById(R.id.btnRetry);
        databaseHelper = new DatabaseHelper(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnHome).setOnClickListener(v -> openHome());

        // Refresh My Events without opening another copy of the page.
        findViewById(R.id.btnMyEvents)
                .setOnClickListener(v -> loadRegisteredEvents());

        btnBrowseEvents.setOnClickListener(v -> openHome());
        btnRetry.setOnClickListener(v -> loadRegisteredEvents());

        // Phase 2: save and read the registered events summary.
        findViewById(R.id.btnSaveSummary)
                .setOnClickListener(v -> saveSummary());

        findViewById(R.id.btnViewSummary)
                .setOnClickListener(v -> viewSummary());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRegisteredEvents();
    }

    private void loadRegisteredEvents() {
        try {
            ArrayList<Event> events =
                    databaseHelper.getRegisteredEvents();

            if (events.isEmpty()) {
                showMessage(
                        "No registered events yet.\n"
                                + "Explore campus events and reserve your spot.",
                        false
                );
                return;
            }

            ArrayList<String> rows = new ArrayList<>();

            for (Event event : events) {
                rows.add(
                        event.getName()
                                + "\n\nDate: " + event.getDate()
                                + "\nLocation: " + event.getLocation()
                                + "\n\nView details ›"
                );
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                    this,
                    android.R.layout.simple_list_item_1,
                    rows
            ) {
                @NonNull
                @Override
                public View getView(
                        int position,
                        @Nullable View convertView,
                        @NonNull ViewGroup parent
                ) {
                    TextView row = (TextView) super.getView(
                            position,
                            convertView,
                            parent
                    );

                    row.setTextColor(
                            getResources().getColor(
                                    R.color.text_primary,
                                    getTheme()
                            )
                    );
                    row.setTextSize(16);
                    row.setSingleLine(false);
                    row.setPadding(dp(16), dp(20), dp(16), dp(20));

                    return row;
                }
            };

            lvMyEvents.setAdapter(adapter);

            lvMyEvents.setOnItemClickListener(
                    (parent, view, position, id) ->
                            openDetails(events.get(position))
            );

            emptyState.setVisibility(View.GONE);
            lvMyEvents.setVisibility(View.VISIBLE);

        } catch (IllegalStateException e) {
            showMessage(
                    "Unable to load your events.\nPlease try again.",
                    true
            );
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

    private void openDetails(Event event) {
        Intent intent = new Intent(this, EventDetailsActivity.class);

        intent.putExtra(MainActivity.EXTRA_EVENT_ID, event.getId());
        intent.putExtra(MainActivity.EXTRA_EVENT_NAME, event.getName());
        intent.putExtra(MainActivity.EXTRA_EVENT_DATE, event.getDate());
        intent.putExtra(
                MainActivity.EXTRA_EVENT_LOCATION,
                event.getLocation()
        );
        intent.putExtra(
                MainActivity.EXTRA_EVENT_DESCRIPTION,
                event.getDescription()
        );

        startActivity(intent);
    }

    private void openHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );
        startActivity(intent);
        finish();
    }

    // Save the current registered events to an internal text file.
    private void saveSummary() {
        try {
            ArrayList<Event> events = databaseHelper.getRegisteredEvents();

            if (events.isEmpty()) {
                Toast.makeText(
                        this,
                        "No registered events to save.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            StringBuilder summary = new StringBuilder();
            summary.append("My Registered Events\n");
            summary.append("====================\n\n");

            for (Event event : events) {
                summary.append("Event: ")
                        .append(event.getName())
                        .append("\n");

                summary.append("Date: ")
                        .append(event.getDate())
                        .append("\n");

                summary.append("Location: ")
                        .append(event.getLocation())
                        .append("\n\n");
            }

            try (FileOutputStream output = openFileOutput(
                    SUMMARY_FILE,
                    MODE_PRIVATE
            )) {
                output.write(
                        summary.toString().getBytes(StandardCharsets.UTF_8)
                );
            }

            Toast.makeText(
                    this,
                    "Summary saved successfully.",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (IOException | IllegalStateException
                 | android.database.sqlite.SQLiteException e) {
            Toast.makeText(
                    this,
                    "Unable to save the summary. Please try again.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // Read the saved text file and display its contents.
    private void viewSummary() {
        StringBuilder summary = new StringBuilder();

        try (
                FileInputStream input = openFileInput(SUMMARY_FILE);
                InputStreamReader inputReader = new InputStreamReader(
                        input,
                        StandardCharsets.UTF_8
                );
                BufferedReader reader = new BufferedReader(inputReader)
        ) {
            String line;

            while ((line = reader.readLine()) != null) {
                summary.append(line).append("\n");
            }

            if (summary.length() == 0) {
                Toast.makeText(
                        this,
                        "The saved summary is empty.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Saved Events Summary")
                    .setMessage(summary.toString())
                    .setPositiveButton("Close", null)
                    .show();

        } catch (FileNotFoundException e) {
            Toast.makeText(
                    this,
                    "No summary found. Please save one first.",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (IOException e) {
            Toast.makeText(
                    this,
                    "Unable to read the summary. Please try again.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }

    @Override
    protected void onDestroy() {
        if (databaseHelper != null) {
            databaseHelper.close();
        }
        super.onDestroy();
    }
}