package org.saturn.app.model.impl;

public class User {
//    channel,isme bool, nick, trip, uType, hash, level int , userId long, isBot bool, color bool

    String channel;
    boolean isme;
    String nick;
    String trip;
    String uType;
    String hash;
    int level;
    long userId;
    boolean isBot;

    @Override
    public String toString() {
        return nick;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public boolean isIsme() {
        return isme;
    }

    public void setIsme(boolean isme) {
        this.isme = isme;
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

    public String getuType() {
        return uType;
    }

    public void setuType(String uType) {
        this.uType = uType;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public boolean isBot() {
        return isBot;
    }

    public void setBot(boolean bot) {
        isBot = bot;
    }
    public User(String channel, boolean isme, String nick, String trip, String uType, String hash, int level, long userId, boolean isBot) {
        this.channel = channel;
        this.isme = isme;
        this.nick = nick;
        this.trip = trip;
        this.uType = uType;
        this.hash = hash;
        this.level = level;
        this.userId = userId;
        this.isBot = isBot;
    }
}
