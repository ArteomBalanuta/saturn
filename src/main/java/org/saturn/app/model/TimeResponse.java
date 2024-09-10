package org.saturn.app.model;

public class TimeResponse {

    StringDto results;
    String status;

    public StringDto getResult() {
        return this.results;
    }
    /*
    {"results":
    {"date":"2024-09-10","sunrise":"5:22:06 AM",
    "sunset":"5:56:40 PM",
    "first_light":"3:55:12 AM",
    "last_light":"7:23:34 PM",
    "dawn":"4:56:23 AM",
    "dusk":"6:22:23 PM","solar_noon":"11:39:23 AM",
    "golden_hour":"5:22:56 PM",
    "day_length":"12:34:34",
    "Stringzone":"Asia/Tokyo",
    "utc_offset":540},
    "status":"OK"}
     */

    @Override
    public String toString() {
        return "TimeResponse{" +
                "results=" + results +
                ", status='" + status + '\'' +
                '}';
    }

    public static class StringDto {
        String date;
        String sunrise;
        String sunset;
        String first_light;
        String last_light;
        String dawn;
        String dusk;
        String solar_noon;
        String golden_hour;
        String day_length;
        String timezone;
        String utc_offset;

        @Override
        public String toString() {
            return "date='" + date + '\'' +
                    "\\n sunrise='" + sunrise + '\'' +
                    "\\n sunset='" + sunset + '\'' +
                    "\\n first_light='" + first_light + '\'' +
                    "\\n last_light='" + last_light + '\'' +
                    "\\n dawn='" + dawn + '\'' +
                    "\\n dusk='" + dusk + '\'' +
                    "\\n solar_noon='" + solar_noon + '\'' +
                    "\\n golden_hour='" + golden_hour + '\'' +
                    "\\n day_length='" + day_length + '\'' +
                    "\\n timezone='" + timezone + '\'' +
                    "\\n utc_offset='" + utc_offset + '\'' +
                    '}';
        }

        public StringDto() {
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getSunrise() {
            return sunrise;
        }

        public void setSunrise(String sunrise) {
            this.sunrise = sunrise;
        }

        public String getSunset() {
            return sunset;
        }

        public void setSunset(String sunset) {
            this.sunset = sunset;
        }

        public String getFirst_light() {
            return first_light;
        }

        public void setFirst_light(String first_light) {
            this.first_light = first_light;
        }

        public String getLast_light() {
            return last_light;
        }

        public void setLast_light(String last_light) {
            this.last_light = last_light;
        }

        public String getDawn() {
            return dawn;
        }

        public void setDawn(String dawn) {
            this.dawn = dawn;
        }

        public String getDusk() {
            return dusk;
        }

        public void setDusk(String dusk) {
            this.dusk = dusk;
        }

        public String getSolar_noon() {
            return solar_noon;
        }

        public void setSolar_noon(String solar_noon) {
            this.solar_noon = solar_noon;
        }

        public String getGolden_hour() {
            return golden_hour;
        }

        public void setGolden_hour(String golden_hour) {
            this.golden_hour = golden_hour;
        }

        public String getDay_length() {
            return day_length;
        }

        public void setDay_length(String day_length) {
            this.day_length = day_length;
        }

        public String getTimezone() {
            return timezone;
        }

        public void setTimezone(String timezone) {
            this.timezone = timezone;
        }

        public String getUtc_offset() {
            return utc_offset;
        }

        public void setUtc_offset(String utc_offset) {
            this.utc_offset = utc_offset;
        }
    }

    public StringDto getResults() {
        return results;
    }

    public void setResults(StringDto results) {
        this.results = results;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public TimeResponse() {
    }
}
