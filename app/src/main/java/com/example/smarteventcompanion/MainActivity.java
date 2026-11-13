package com.example.smarteventcompanion;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "event_id";
    public static final String EXTRA_EVENT_NAME = "event_name";
    public static final String EXTRA_EVENT_DATE = "event_date";
    public static final String EXTRA_EVENT_LOCATION = "event_location";
    public static final String EXTRA_EVENT_DESCRIPTION = "event_description";

    private ListView lvEvents;
    private TextView tvError;
    private EditText etSearch;
    private ArrayList<Event> allEvents = new ArrayList<>();
    private ArrayList<Event> displayedEvents = new ArrayList<>();
    private EventAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });

        lvEvents = findViewById(R.id.lvEvents);
        tvError = findViewById(R.id.tvError);
        etSearch = findViewById(R.id.etSearch);
        Button btnMyEvents = findViewById(R.id.btnMyEvents);
        Button btnBluetooth = findViewById(R.id.btnBluetooth);
        Button btnToggleTheme = findViewById(R.id.btnToggleTheme);

        loadAndShowEvents();

        // تفعيل ميزة البحث (Additional Feature)
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterEvents(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        lvEvents.setOnItemClickListener((parent, view, position, id) -> {
            Event event = displayedEvents.get(position);
            Intent intent = createIntent("EventDetailsActivity");
            intent.putExtra(EXTRA_EVENT_ID, event.getId());
            intent.putExtra(EXTRA_EVENT_NAME, event.getName());
            intent.putExtra(EXTRA_EVENT_DATE, event.getDate());
            intent.putExtra(EXTRA_EVENT_LOCATION, event.getLocation());
            intent.putExtra(EXTRA_EVENT_DESCRIPTION, event.getDescription());
            openScreen(intent);
        });

        // كود الثيم الخاص بك
        btnToggleTheme.setOnClickListener(v -> {
            int currentMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (currentMode == Configuration.UI_MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            }
        });

        btnMyEvents.setOnClickListener(v -> openScreen(createIntent("MyEventsActivity")));
        btnBluetooth.setOnClickListener(v -> openScreen(createIntent("BluetoothActivity")));
    }

    private void loadAndShowEvents() {
        try {
            allEvents = EventRepository.loadEvents(this);
            displayedEvents.clear();
            displayedEvents.addAll(allEvents);
        } catch (IllegalStateException e) {
            showError(getString(R.string.msg_unable_to_load_events));
            return;
        }

        if (allEvents.isEmpty()) {
            showError(getString(R.string.msg_no_events));
            return;
        }

        tvError.setVisibility(View.GONE);
        lvEvents.setVisibility(View.VISIBLE);
        adapter = new EventAdapter(displayedEvents);
        lvEvents.setAdapter(adapter);
    }

    // دالة الفلترة للبحث
    private void filterEvents(String query) {
        displayedEvents.clear();
        if (query.isEmpty()) {
            displayedEvents.addAll(allEvents);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (Event event : allEvents) {
                if (event.getName().toLowerCase().contains(lowerCaseQuery) ||
                        event.getLocation().toLowerCase().contains(lowerCaseQuery)) {
                    displayedEvents.add(event);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void showError(String message) {
        lvEvents.setVisibility(View.GONE);
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private Intent createIntent(String activityName) {
        Intent intent = new Intent();
        intent.setClassName(this, getPackageName() + "." + activityName);
        return intent;
    }

    private void openScreen(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.msg_screen_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private class EventAdapter extends ArrayAdapter<Event> {
        EventAdapter(ArrayList<Event> list) {
            super(MainActivity.this, 0, list);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(getContext()).inflate(R.layout.item_event, parent, false);
            }
            Event event = getItem(position);
            TextView tvItemName = row.findViewById(R.id.tvItemName);
            TextView tvItemDate = row.findViewById(R.id.tvItemDate);
            tvItemName.setText(event.getName());
            tvItemDate.setText(event.getDate());
            return row;
        }
    }
}