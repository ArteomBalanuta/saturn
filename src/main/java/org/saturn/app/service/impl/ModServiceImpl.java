package org.saturn.app.service.impl;

import org.saturn.app.model.dto.User;
import org.saturn.app.service.ModService;
import org.saturn.app.service.SQLService;
import org.saturn.app.util.Util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ModServiceImpl extends OutService implements ModService {
    
    private final SQLService sqlService;
    
    public ModServiceImpl(SQLService sqlService, BlockingQueue<String> queue, BlockingQueue<String> rawMessageQueue) {
        super(queue, rawMessageQueue);
        this.sqlService = sqlService;
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
            
            enqueueMessageForSending(author, " @" + pest + " “Long is the night to him who is awake; long is a mile to him who is tired; long is life to the foolish who do not know the true law.” ", false);
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
    public void lock() {
        enqueueRawMessageForSending("{ \"cmd\": \"lockroom\"}");
    }

    @Override
    public void unlock() {
        enqueueRawMessageForSending("{ \"cmd\": \"unlockroom\"}");
    }


    @Override
    public void enableCaptcha() {
        enqueueRawMessageForSending("{ \"cmd\": \"enablecaptcha\"}");
    }

    @Override
    public void disableCaptcha() {
        enqueueRawMessageForSending("{ \"cmd\": \"disablecaptcha\"}");
    }


    @Override
    public void unban(String target) {
        /* TODO: hash it so special chars are safe */
//        String hash = Base64.getEncoder().encodeToString(target.getBytes(StandardCharsets.UTF_8));
        String sql = ":sql DELETE FROM banned WHERE id='" + target + "';";
        sqlService.executeSql(sql, false);
    }
    
    @Override
    public void listBanned(String author) {
        List<String> bannedIds = sqlService.getBannedIds();
        if (bannedIds.isEmpty()) {
            enqueueMessageForSending(author,"No users has been banned.", false);
        } else {
            enqueueMessageForSending(author,"Banned hashes, trips, nicks: " + bannedIds, false);
        }
    }

    @Override
    public void unbanAll() {
        List<String> bannedIds = sqlService.getBannedIds();
        if (bannedIds.isEmpty()) {
            enqueueMessageForSending("", "No users has been banned.", false);
        } else {
            bannedIds.forEach(this::unban);
            enqueueMessageForSending("", "Unbanned hashes, trips, nicks: " + bannedIds, false);
        }
    }

    @Override
    public void kick(String target) {
        enqueueRawMessageForSending(String.format("{ \"cmd\": \"kick\", \"nick\": \"%s\"}", target));
    }

    @Override
    public void overflow(String target) {
        enqueueRawMessageForSending(String.format("{ \"cmd\": \"overflow\", \"nick\": \"%s\"}", target));
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
