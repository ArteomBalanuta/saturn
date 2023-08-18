package org.saturn.app.service.impl;

import org.saturn.app.model.dto.User;
import org.saturn.app.service.ModService;
import org.saturn.app.service.SQLService;
import org.saturn.app.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ModServiceImpl extends OutService implements ModService {
    
    private final SQLService sqlService;
    
    public ModServiceImpl(SQLService sqlService, BlockingQueue<String> queue, BlockingQueue<String> rawMessageQueue) {
        super(queue, rawMessageQueue);
        this.sqlService = sqlService;
    }
    
    @Override
    public void kick(String target) {
        enqueueRawMessageForSending(String.format("{ \"cmd\": \"kick\", \"nick\": \"%s\"}", target));
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
            
            enqueueMessageForSending("@" + pest + " “Long is the night to him who is awake; long is a mile to him who is tired; long is life to the foolish who do not know the true law.” ");
            kick(pest);
            resetVoteKick();
        }
    }

    private void resetVoteKick() {
        pest = null;
        numberOfvotes.set(0);
    }

    @Override
    public void ban(String target) {
        String sql = ":sql INSERT INTO banned(id) VALUES ('?');".replace("?", Util.getAuthor(target));
        sqlService.executeSql(sql, false);
    }
    
    @Override
    public void unban(String target) {
        String sql = ":sql DELETE FROM banned WHERE id='?';".replace("?", target);
        sqlService.executeSql(sql, false);
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

        boolean isTripPresent = target.getTrip().length() > 3;
        return (isTripPresent && bannedIds.contains(target.getTrip()))
                || bannedIds.contains(target.getNick())
                || bannedIds.contains(target.getHash());
    }
}
