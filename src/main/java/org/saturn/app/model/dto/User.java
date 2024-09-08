package org.saturn.app.model.dto;

import java.util.Objects;

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
    
    public String getChannel() {
        return channel;
    }
    
    public void setChannel(String channel) {
        this.channel = channel;
    }
    
    public boolean isIsMe() {
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
    
    @Override
    public String toString() {
        return "User{" +
                "channel='" + channel + '\'' +
                ", isme=" + isme +
                ", nick='" + nick + '\'' +
                ", trip='" + trip + '\'' +
                ", hash='" + hash + '\'' +
                '}';
    }
    
    public User(String channel, boolean isme, String nick, String trip, String uType, String hash, int level,
                long userId, boolean isBot) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return isme == user.isme && level == user.level && userId == user.userId && isBot == user.isBot && Objects.equals(nick, user.nick) && Objects.equals(trip, user.trip) && Objects.equals(uType, user.uType) && Objects.equals(hash, user.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isme, nick, trip, uType, hash, level, userId, isBot);
    }
}
