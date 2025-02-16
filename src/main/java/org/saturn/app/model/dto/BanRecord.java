package org.saturn.app.model.dto;

public record BanRecord(String trip, String name, String hash, String reason) {
    @Override
    public String toString() {
        return "BanRecord{" +
                "trip='" + trip + '\'' +
                ", name='" + name + '\'' +
                ", hash='" + hash + '\'' +
                ", reason='" + reason + '\'' +
                '}';
    }
}
