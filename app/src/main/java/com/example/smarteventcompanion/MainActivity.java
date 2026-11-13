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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "event_id";
    public static final String EXTRA_EVENT_NAME = "event_name";
    public static final String EXTRA_EVENT_DATE = "event_date";
    public static final String EXTRA_EVENT_LOCATION = "event_location";
    public static final String EXTRA_EVENT_DESCRIPTION = "event_description";

    private ListView lvEvents;
    private TextView tvError;
    private TextView tvEventCount;
    private EditText etSearch;
    private Spinner spCategoryFilter;
    private final ArrayList<Event> allEvents = new ArrayList<>();
    private final ArrayList<Event> displayedEvents = new ArrayList<>();
    private EventAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left + 18, bars.top + 16, bars.right + 18, bars.bottom + 12);
            return windowInsets;
        });

        lvEvents = findViewById(R.id.lvEvents);
        tvError = findViewById(R.id.tvError);
        tvEventCount = findViewById(R.id.tvEventCount);
        etSearch = findViewById(R.id.etSearch);
        spCategoryFilter = findViewById(R.id.spCategoryFilter);

        Button btnToggleTheme = findViewById(R.id.btnToggleTheme);
        setupBottomNavigation();

        loadAndShowEvents();
        setupCategoryFilter();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterEvents();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        spCategoryFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                filterEvents();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        lvEvents.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= displayedEvents.size()) return;
            Event event = displayedEvents.get(position);
            Intent intent = createIntent("EventDetailsActivity");
            intent.putExtra(EXTRA_EVENT_ID, event.getId());
            intent.putExtra(EXTRA_EVENT_NAME, event.getName());
            intent.putExtra(EXTRA_EVENT_DATE, event.getDate());
            intent.putExtra(EXTRA_EVENT_LOCATION, event.getLocation());
            intent.putExtra(EXTRA_EVENT_DESCRIPTION, event.getDescription());
            openScreen(intent);
        });

        updateThemeButton(btnToggleTheme);
        btnToggleTheme.setOnClickListener(v -> {
            int currentMode = getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            AppCompatDelegate.setDefaultNightMode(
                    currentMode == Configuration.UI_MODE_NIGHT_YES
                            ? AppCompatDelegate.MODE_NIGHT_NO
                            : AppCompatDelegate.MODE_NIGHT_YES);
        });

    }



    private void updateThemeButton(Button button) {
        int currentMode = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        button.setText(currentMode == Configuration.UI_MODE_NIGHT_YES
                ? "Light mode" : "Dark mode");
    }

    private void setupBottomNavigation() {
        BottomNavigationView navigation = findViewById(R.id.bottomNavigation);
        navigation.setSelectedItemId(R.id.nav_home);
        navigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return true;
            if (id == R.id.nav_registrations) {
                openScreen(createIntent("MyEventsActivity"));
                return true;
            }
            if (id == R.id.nav_bluetooth) {
                openScreen(createIntent("BluetoothActivity"));
                return true;
            }
            if (id == R.id.nav_reports) {
                openScreen(createIntent("ReportsActivity"));
                return true;
            }
            return false;
        });
    }

    private void loadAndShowEvents() {
        try {
            allEvents.clear();
            allEvents.addAll(EventRepository.loadEvents(this));
        } catch (IllegalStateException e) {
            showError(getString(R.string.msg_unable_to_load_events));
            return;
        }

        if (allEvents.isEmpty()) {
            showError(getString(R.string.msg_no_events));
            return;
        }

        adapter = new EventAdapter(displayedEvents);
        lvEvents.setAdapter(adapter);
        filterEvents();
    }

    private void setupCategoryFilter() {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        categories.add("All types");
        for (Event event : allEvents) {
            categories.add(event.getCategory());
        }

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, new ArrayList<>(categories));
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategoryFilter.setAdapter(categoryAdapter);
    }

    private void filterEvents() {
        if (adapter == null) return;

        String query = etSearch.getText().toString().trim().toLowerCase();
        String selectedCategory = spCategoryFilter.getSelectedItem() == null
                ? "All types" : spCategoryFilter.getSelectedItem().toString();

        displayedEvents.clear();
        for (Event event : allEvents) {
            boolean matchesCategory = selectedCategory.equals("All types")
                    || event.getCategory().equalsIgnoreCase(selectedCategory);
            boolean matchesSearch = query.isEmpty()
                    || event.getName().toLowerCase().contains(query)
                    || event.getLocation().toLowerCase().contains(query)
                    || event.getCategory().toLowerCase().contains(query)
                    || event.getDescription().toLowerCase().contains(query);

            if (matchesCategory && matchesSearch) {
                displayedEvents.add(event);
            }
        }

        adapter.notifyDataSetChanged();
        tvEventCount.setText(displayedEvents.size() + " events found");
        boolean hasResults = !displayedEvents.isEmpty();
        lvEvents.setVisibility(hasResults ? View.VISIBLE : View.GONE);
        tvError.setVisibility(hasResults ? View.GONE : View.VISIBLE);
        if (!hasResults) {
            tvError.setText("No events match your search. Try another keyword or type.");
        }
    }

    private void showError(String message) {
        lvEvents.setVisibility(View.GONE);
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
        tvEventCount.setText("Events");
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
                row = LayoutInflater.from(getContext())
                        .inflate(R.layout.event_card, parent, false);
            }

            Event event = getItem(position);
            TextView tvName = row.findViewById(R.id.tvEventName);
            TextView tvDate = row.findViewById(R.id.tvEventDate);
            TextView tvLocation = row.findViewById(R.id.tvEventLocation);
            TextView tvCategory = row.findViewById(R.id.tvEventCategory);

            tvName.setText(event.getName());
            tvDate.setText(event.getDate());
            tvLocation.setText(event.getLocation());
            tvCategory.setText(event.getCategory());
            return row;
        }
    }
}
