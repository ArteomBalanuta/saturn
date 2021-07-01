package org.saturn.app.model.impl;

public class ChatMessage {
    private String size;
    private String nick;
    private String trip;
    private String hash;
    private String time;
    private String text;

    public ChatMessage() {

    }

    public ChatMessage(String size, String nick, String trip, String hash, String time, String text) {
        this.size = size;
        this.nick = nick;
        this.trip = trip;
        this.time = time;
        this.text = text;
        this.hash = hash;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public String getTrip() {
        return trip;
    }

    public void setTrip(String trip) {
        this.trip = trip;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
