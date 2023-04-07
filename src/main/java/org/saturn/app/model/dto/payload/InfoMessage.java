package org.saturn.app.model.dto.payload;

public class InfoMessage {
    /*
        {"cmd":"info","text":"mercury is now test","channel":"programming","time":1680860867786}
    */
    private String info;
    private String text;
    private String channel;
    private String from;
    private String trip;

    public String getTrip() {
        return trip;
    }

    public void setTrip(String trip) {
        this.trip = trip;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public InfoMessage(String info, String text, String channel, String from) {
        this.info = info;
        this.text = text;
        this.channel = channel;
        this.from = from;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public InfoMessage(String info, String text, String channel) {
        this.info = info;
        this.text = text;
        this.channel = channel;
    }
}
