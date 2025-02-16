package org.saturn.app.model.dto;

public class BanDto {
    private final String trip;
    private final String name;
    private final String hash;
    private final String reason;

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

    @Override
    public String toString() {
        return "BanDto{" +
                "trip='" + trip + '\'' +
                ", name='" + name + '\'' +
                ", hash='" + hash + '\'' +
                ", reason='" + reason + '\'' +
                '}';
    }
}
