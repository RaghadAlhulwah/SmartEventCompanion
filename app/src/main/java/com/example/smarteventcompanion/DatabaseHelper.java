package com.example.smarteventcompanion;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "smart_events.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_EVENTS = "registered_events";
    private static final String TABLE_ATTENDANCE = "attendance_records";

    // Constructor
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Create database tables
    @Override
    public void onCreate(SQLiteDatabase db) {

        String createEventsTable =
                "CREATE TABLE registered_events (" +
                        "id INTEGER PRIMARY KEY, " +
                        "name TEXT, " +
                        "date TEXT, " +
                        "location TEXT, " +
                        "description TEXT)";

        db.execSQL(createEventsTable);

        String createAttendanceTable =
                "CREATE TABLE attendance_records (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "participant_id TEXT NOT NULL, " +
                        "event_id INTEGER NOT NULL, " +
                        "attendance_time TEXT NOT NULL, " +
                        "UNIQUE(participant_id, event_id))";

        db.execSQL(createAttendanceTable);
    }

    // Database upgrades
    @Override
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion) {

        // No upgrades required for version 1.
    }

    // Register an event
    public boolean registerEvent(Event event) {

        if (event == null) {
            return false;
        }

        try {
            SQLiteDatabase db = getWritableDatabase();

            ContentValues values = new ContentValues();

            values.put("id", event.getId());
            values.put("name", event.getName());
            values.put("date", event.getDate());
            values.put("location", event.getLocation());
            values.put("description", event.getDescription());

            long result = db.insertWithOnConflict(
                    TABLE_EVENTS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
            );

            return result != -1;

        } catch (SQLiteException e) {
            return false;
        }
    }

    // Cancel a participant's event registration.
    public boolean cancelRegistration(int eventId) {
        if (eventId <= 0) return false;
        try {
            int deleted = getWritableDatabase().delete(
                    TABLE_EVENTS, "id = ?", new String[]{String.valueOf(eventId)});
            return deleted > 0;
        } catch (SQLiteException e) {
            return false;
        }
    }

    // Check if event is already registered
    public boolean isEventRegistered(int eventId) {

        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.query(
                TABLE_EVENTS,
                new String[]{"id"},
                "id = ?",
                new String[]{String.valueOf(eventId)},
                null,
                null,
                null
        )) {

            return cursor.moveToFirst();
        }
    }

    // Retrieve registered events
    public ArrayList<Event> getRegisteredEvents() {

        ArrayList<Event> events = new ArrayList<>();

        try {
            SQLiteDatabase db = getReadableDatabase();

            try (Cursor cursor = db.query(
                    TABLE_EVENTS,
                    new String[]{
                            "id",
                            "name",
                            "date",
                            "location",
                            "description"
                    },
                    null,
                    null,
                    null,
                    null,
                    "id ASC"
            )) {

                while (cursor.moveToNext()) {

                    int id = cursor.getInt(
                            cursor.getColumnIndexOrThrow("id")
                    );

                    String name = cursor.getString(
                            cursor.getColumnIndexOrThrow("name")
                    );

                    String date = cursor.getString(
                            cursor.getColumnIndexOrThrow("date")
                    );

                    String location = cursor.getString(
                            cursor.getColumnIndexOrThrow("location")
                    );

                    String description = cursor.getString(
                            cursor.getColumnIndexOrThrow("description")
                    );

                    Event event = new Event(
                            id,
                            name,
                            date,
                            location,
                            description
                    );

                    events.add(event);
                }
            }

            return events;

        } catch (SQLiteException e) {

            throw new IllegalStateException(
                    "Unable to load registered events", e
            );
        }
    }

    // Record attendance with provided date and time
    public boolean recordAttendance(
            String participantId,
            int eventId,
            String attendanceTime) {

        if (participantId == null ||
                participantId.trim().isEmpty() ||
                eventId <= 0 ||
                attendanceTime == null ||
                attendanceTime.trim().isEmpty()) {

            return false;
        }

        try {
            SQLiteDatabase db = getWritableDatabase();

            ContentValues values = new ContentValues();

            values.put("participant_id", participantId.trim());
            values.put("event_id", eventId);
            values.put("attendance_time", attendanceTime);

            long result = db.insertWithOnConflict(
                    TABLE_ATTENDANCE,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
            );

            return result != -1;

        } catch (SQLiteException e) {
            return false;
        }
    }

    // Record attendance with automatic date and time
    public boolean recordAttendance(
            String participantId,
            int eventId) {

        String currentTime = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
        ).format(new Date());

        return recordAttendance(
                participantId,
                eventId,
                currentTime
        );
    }

    // Check if participant attendance already exists
    public boolean isAttendanceRecorded(
            String participantId,
            int eventId) {

        if (participantId == null ||
                participantId.trim().isEmpty() ||
                eventId <= 0) {
            return false;
        }

        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.query(
                TABLE_ATTENDANCE,
                new String[]{"id"},
                "participant_id = ? AND event_id = ?",
                new String[]{
                        participantId.trim(),
                        String.valueOf(eventId)
                },
                null,
                null,
                null
        )) {

            return cursor.moveToFirst();
        }
    }

    // Retrieve all attendance records
    public ArrayList<String> getAttendanceRecords() {

        ArrayList<String> records = new ArrayList<>();

        try {
            SQLiteDatabase db = getReadableDatabase();

            try (Cursor cursor = db.query(
                    TABLE_ATTENDANCE,
                    new String[]{
                            "participant_id",
                            "event_id",
                            "attendance_time"
                    },
                    null,
                    null,
                    null,
                    null,
                    "id ASC"
            )) {

                while (cursor.moveToNext()) {

                    String participantId = cursor.getString(
                            cursor.getColumnIndexOrThrow("participant_id")
                    );

                    int eventId = cursor.getInt(
                            cursor.getColumnIndexOrThrow("event_id")
                    );

                    String attendanceTime = cursor.getString(
                            cursor.getColumnIndexOrThrow("attendance_time")
                    );

                    String record =
                            "Participant: " + participantId +
                                    "\nEvent ID: " + eventId +
                                    "\nTime: " + attendanceTime;

                    records.add(record);
                }
            }

            return records;

        } catch (SQLiteException e) {

            throw new IllegalStateException(
                    "Unable to load attendance records", e
            );
        }
    }

    // Retrieve attendance records for a specific event
    public ArrayList<String> getAttendanceByEvent(int eventId) {

        ArrayList<String> records = new ArrayList<>();

        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.query(
                TABLE_ATTENDANCE,
                new String[]{
                        "participant_id",
                        "attendance_time"
                },
                "event_id = ?",
                new String[]{String.valueOf(eventId)},
                null,
                null,
                "id ASC"
        )) {

            while (cursor.moveToNext()) {

                String participantId = cursor.getString(
                        cursor.getColumnIndexOrThrow("participant_id")
                );

                String attendanceTime = cursor.getString(
                        cursor.getColumnIndexOrThrow("attendance_time")
                );

                records.add(
                        "Participant: " + participantId +
                                "\nTime: " + attendanceTime
                );
            }
        }

        return records;
    }
}