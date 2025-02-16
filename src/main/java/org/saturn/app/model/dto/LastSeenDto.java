package org.saturn.app.model.dto;

public class LastSeenDto {
  String tripOrNick;
  String lastMessage = " - ";
  String lastSeenRfc1123 = " - ";
  String timeSinceSeen = " - ";
  String timeSinceJoined = " - ";
  String joinedAtRfc1123 = " - ";

  String sessionDuration = " - ";

  public String getTripOrNick() {
    return tripOrNick;
  }

  public void setTripOrNick(String tripOrNick) {
    this.tripOrNick = tripOrNick;
  }

  public String getLastMessage() {
    return lastMessage;
  }

  public void setLastMessage(String lastMessage) {
    this.lastMessage = lastMessage;
  }

  public String getLastSeenRfc1123() {
    return lastSeenRfc1123;
  }

  public void setLastSeenRfc1123(String lastSeenRfc1123) {
    this.lastSeenRfc1123 = lastSeenRfc1123;
  }

  public String getTimeSinceSeen() {
    return timeSinceSeen;
  }

  public void setTimeSinceSeen(String timeSinceSeen) {
    this.timeSinceSeen = timeSinceSeen;
  }

  public String getTimeSinceJoined() {
    return timeSinceJoined;
  }

  public void setTimeSinceJoined(String timeSinceJoined) {
    this.timeSinceJoined = timeSinceJoined;
  }

  public String getJoinedAtRfc1123() {
    return joinedAtRfc1123;
  }

  public void setJoinedAtRfc1123(String joinedAtRfc1123) {
    this.joinedAtRfc1123 = joinedAtRfc1123;
  }

  public String getSessionDuration() {
    return sessionDuration;
  }

  public void setSessionDuration(String sessionDuration) {
    this.sessionDuration = sessionDuration;
  }

  public LastSeenDto() {}
}
