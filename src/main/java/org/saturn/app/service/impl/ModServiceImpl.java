package org.saturn.app.service.impl;

import org.saturn.app.model.impl.User;
import org.saturn.app.service.ModService;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ModServiceImpl extends OutService implements ModService {
    private Connection connection;
    List<String> bannedTripsNnicksNhashes = new ArrayList<>();
    
    public ModServiceImpl(Connection connection, BlockingQueue<String> queue) {
        super(queue);
        this.connection = connection;
    }
    
    private String modBotName = "gohackbot";
    
    @Override
    public void kick(String target) {
        System.out.println("kick request sent  ");
        enqueueMessageForSending("/whisper " + modBotName + " #!kick " + target);
    }
    
    
    public AtomicInteger numberOfvotes = new AtomicInteger();
    public static String candidate;
    public List<String> judge = new ArrayList<>();
    
    public void votekick(String target) {
        candidate = target.trim();
    }
    
    public void vote(String author) {
        judge.add(author);
        int i = numberOfvotes.incrementAndGet();
        if (i == 4) {
            StringBuilder s = new StringBuilder();
            judge.forEach(j -> s.append(j + " "));
            
            enqueueMessageForSending("@" + candidate + " you have been sentenced to be executed by merc, judges: " + s + "  ");
//            enqueueMessageForSending("/whisper " + merc + " #!kick " + candidate);
            candidate = null;
            numberOfvotes.set(0);
        }
    }
    
    @Override
    public void ban(String target) {
        if (target != null || !target.equals("") || !target.equals(" ")) {
            this.bannedTripsNnicksNhashes.add(target);
        }
    }
    
    @Override
    public void unban(String target) {
    }
    
    @Override
    public boolean isBanned(User target) {
        if (target == null) {
            return false;
        }
        System.out.println("##### All Banned trips, nicks, hashes: ");
        bannedTripsNnicksNhashes.forEach(System.out::println);
        System.out.println("#####");
        
        if (bannedTripsNnicksNhashes.contains(target.getTrip())
                || bannedTripsNnicksNhashes.contains(target.getNick())
                || bannedTripsNnicksNhashes.contains(target.getHash())) {
            System.out.println("Banned: " + target.toString());
            return true;
        }
        
        return false;
    }
}
