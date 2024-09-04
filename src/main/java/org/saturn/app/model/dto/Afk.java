package org.saturn.app.model.dto;

import java.time.ZonedDateTime;
import java.util.List;

public class Afk {
    public String trip;
    public List<User>  users;
    public String reason;
    public ZonedDateTime afkOn;

    public Afk(List<User> users, String reason, ZonedDateTime afkOn, String trip) {
        this.users = users;
        this.reason = reason;
        this.afkOn = afkOn;
        this.trip = trip;
    }

    public List<User> getUsers() {
        return users;
    }

    public String getReason() {
        return reason;
    }

    public ZonedDateTime getAfkOn() {
        return afkOn;
    }

    public String getTrip() {
        return trip;
    }
}
