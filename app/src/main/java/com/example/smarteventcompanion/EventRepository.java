package com.example.smarteventcompanion;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

// Reads the events from assets/events.json and converts them to Event objects.
public class EventRepository {

    private static final String FILE_NAME = "events.json";

    public static ArrayList<Event> loadEvents(Context context) {
        return parseEvents(readFile(context));
    }

    private static String readFile(Context context) {
        StringBuilder builder = new StringBuilder();
        try (InputStream inputStream = context.getAssets().open(FILE_NAME);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + FILE_NAME, e);
        }
        return builder.toString();
    }

    private static ArrayList<Event> parseEvents(String json) {
        ArrayList<Event> events = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                int id = obj.getInt("id");
                String name = obj.getString("name");
                String date = obj.getString("date");
                String location = obj.getString("location");
                String description = obj.optString("description", "");
                String category = obj.optString("category", "Other");

                if (id <= 0 || name.trim().isEmpty() || date.trim().isEmpty()
                        || location.trim().isEmpty()) {
                    throw new IllegalStateException("Invalid event data at position " + i);
                }
                events.add(new Event(id, name, date, location, description, category));
            }
        } catch (JSONException e) {
            throw new IllegalStateException("Invalid JSON in " + FILE_NAME, e);
        }
        return events;
    }
}
