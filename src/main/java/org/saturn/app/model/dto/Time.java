package org.saturn.app.model.dto;

public class Time {
    public String timezone;
    public String unixtime;
    public String datetime;
    public String utc_datetime;
    public String utc_offset;
    
    public Time(String timezone, String unixtime, String utc_datetime, String utc_offset, String datetime) {
        this.timezone = timezone;
        this.unixtime = unixtime;
        this.utc_datetime = utc_datetime;
        this.utc_offset = utc_offset;
        this.datetime = datetime;
    }
}
