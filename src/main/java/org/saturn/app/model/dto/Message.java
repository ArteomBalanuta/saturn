package org.saturn.app.model.dto;

public class Message {
    private String author;
    private String trip;
    private String message;
    private String createdOn;

    public Message(String author, String trip, String message, String createdOn) {
        this.author = author;
        this.trip = trip;
        this.message = message;
        this.createdOn = createdOn;
    }

    public String getAuthor() {
        return author;
    }

    public String getTrip() {
        return trip;
    }

    public String getMessage() {
        return message;
    }

    public String getCreatedOn() {
        return createdOn;
    }
}
