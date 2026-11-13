package com.example.smarteventcompanion;

// Shared Event class used by all four parts of the app.
// Do not change the constructor order or the getter names:
// the other team members' code depends on them.
public class Event {

    private final int id;
    private final String name;
    private final String date;          // format: YYYY-MM-DD
    private final String location;
    private final String description;

    public Event(int id, String name, String date,
                 String location, String description) {
        this.id = id;
        this.name = name;
        this.date = date;
        this.location = location;
        this.description = description;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDate() {
        return date;
    }

    public String getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }
}
