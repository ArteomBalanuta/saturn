package org.saturn.app.model.dto;

public class ChatMessage {
    private boolean isWhisper;
    private String cmd;
    private String from;
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

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public boolean isWhisper() {
        return isWhisper;
    }

    public void setWhisper(boolean whisper) {
        isWhisper = whisper;
    }

    public String getCmd() {
        return cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
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

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}
