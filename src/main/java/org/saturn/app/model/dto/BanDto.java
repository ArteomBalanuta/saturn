package org.saturn.app.model.dto;

public class BanDto {
    private String trip;
    private String name;
    private String hash;
    private String reason;

    public BanDto(String trip, String name, String hash, String reason) {
        this.trip = trip;
        this.name = name;
        this.hash = hash;
        this.reason = reason;
    }

    public String getTrip() {
        return trip;
    }

    public String getName() {
        return name;
    }

    public String getHash() {
        return hash;
    }

    public String getReason() {
        return reason;
    }
}
