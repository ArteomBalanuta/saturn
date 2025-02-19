package org.saturn.app.service;

import java.util.Optional;
import org.saturn.app.model.dto.BanRecord;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

public interface ModService {
  void kick(String target);
  void kickTo(String target, String channel);
  void overflow(String target);
  void shadowBan(BanRecord banDto);
  void unshadowBan(String target);
  void unShadowbanAll(String author);
  void ban(String target);
  void unban(String target);
  void unbanAll();
  void lock();
  void unlock();
  void enableCaptcha();
  void auth(String trip);
  void deauth(String trip);
  void disableCaptcha();
  void listBanned(ChatMessage chatMessage);


  Optional<BanRecord> isShadowBanned(User user);
}
