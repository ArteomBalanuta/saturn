package org.saturn.app.model.dto;

import java.time.ZonedDateTime;
import java.util.List;

public record Afk(List<User> users, String reason, ZonedDateTime afkOn, String trip) {
    public ZonedDateTime getAfkOn() {
        return this.afkOn;
    }

    public String getReason() {
        return this.reason;
    }

    public List<User> getUsers() {
        return this.users;
    }
}
