package com.example.smarteventcompanion;

// Shared Event model. The original constructor is kept for compatibility
// with the database and the other activities.
public class Event {

    private final int id;
    private final String name;
    private final String date;
    private final String location;
    private final String description;
    private final String category;

    public Event(int id, String name, String date, String location, String description) {
        this(id, name, date, location, description, "Other");
    }

    public Event(int id, String name, String date, String location,
                 String description, String category) {
        this.id = id;
        this.name = name;
        this.date = date;
        this.location = location;
        this.description = description;
        this.category = (category == null || category.trim().isEmpty())
                ? "Other" : category.trim();
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getDate() { return date; }
    public String getLocation() { return location; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
}
