package org.saturn.app.service.impl;

import org.saturn.app.model.impl.User;
import org.saturn.app.service.ModService;
import org.saturn.app.service.SQLService;
import org.saturn.app.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ModServiceImpl extends OutService implements ModService {
    private static final String MOD_BOT_NAME = "gohackbot";
    
    private final SQLService sqlService;
    
    public ModServiceImpl(SQLService sqlService, BlockingQueue<String> queue) {
        super(queue);
        this.sqlService = sqlService;
    }
    
    @Override
    public void kick(String target) {
        System.out.println("kick request sent  ");
        enqueueMessageForSending("/whisper " + MOD_BOT_NAME + " #!kick " + target);
    }
    
    
    public AtomicInteger numberOfvotes = new AtomicInteger();
    public static String pest;
    public List<String> judges = new ArrayList<>();
    
    public void votekick(String target) {
        pest = target.trim();
    }
    
    public void vote(String author) {
        /* votes are counted only if trusted user started the vote */
        if (pest == null) {
            return;
        }
        
        judges.add(author);
        int i = numberOfvotes.incrementAndGet();
        if (i == 4) {
            StringBuilder s = new StringBuilder();
            judges.forEach(j -> s.append(j).append(" "));
            
            enqueueMessageForSending("@" + pest + " you have been sentenced to be executed by merc, judges: "
                    + s + "  ");
            enqueueMessageForSending("/whisper " + MOD_BOT_NAME + " #!kick " + pest);
            pest = null;
            numberOfvotes.set(0);
        }
    }
    
    @Override
    public void ban(String target) {
        if (target != null || !target.equals("") || !target.equals(" ")) {
            String sql = ":sql INSERT INTO banned(id) VALUES ('?');".replace("?", Util.getAuthor(target));
            
            sqlService.executeSQLCmd(sql);
        }
    }
    
    @Override
    public void unban(String target) {
        if (target != null || !target.equals("") || !target.equals(" ")) {
            String sql = ":sql DELETE FROM banned WHERE id='?';".replace("?", target);
            sqlService.executeSQLCmd(sql);
        }
    }
    
    @Override
    public void listBanned() {
        List<String> bannedIds = sqlService.getBannedIds();
        if (bannedIds.isEmpty()) {
            enqueueMessageForSending("No users has been banned.");
        } else {
            enqueueMessageForSending("Banned hashes, trips, nicks: " + bannedIds);
        }
    }
    
    @Override
    public boolean isBanned(User target) {
        if (target == null) {
            return false;
        }
        
        List<String> bannedIds = sqlService.getBannedIds();
        return bannedIds.contains(target.getTrip())
                || bannedIds.contains(target.getNick())
                || bannedIds.contains(target.getHash());
    }
}
