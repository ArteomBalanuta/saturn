package org.saturn.app.facade;

import org.apache.commons.configuration2.Configuration;

public class Facade extends ServiceLayer {
    
    public Facade(java.sql.Connection dbConnection, Configuration config) {
        super(dbConnection, config);
    }
    
    public void setChannel(String chanel){
        this.channel = chanel;
    }
    public void setNick(String nick){
        this.nick = nick;
    }
    public void setTrip(String trip){
        this.trip = trip;
    }
}



