package org.saturn.app.agent;

import java.util.HashSet;
import java.util.Set;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.User;

/** Central protection policy for creators, admins, the host bot, and its replicas. */
final class ProtectedPrincipalPolicy {
  private final EngineImpl engine;
  private final Set<String> protectedTrips;

  ProtectedPrincipalPolicy(EngineImpl engine, String creatorTrip) {
    this.engine = engine;
    Set<String> trips = new HashSet<>();
    if (creatorTrip != null && !creatorTrip.isBlank()) trips.add(creatorTrip);
    if (engine.adminTrips != null) {
      for (String configuredTrip : engine.adminTrips.split(",")) {
        if (!configuredTrip.isBlank()) trips.add(configuredTrip.strip());
      }
    }
    this.protectedTrips = Set.copyOf(trips);
  }

  boolean isProtected(String trip, String nick) {
    return (trip != null && protectedTrips.contains(trip)) || isProtectedNick(nick);
  }

  boolean isProtected(User user) {
    return user.isIsMe() || user.isBot() || isProtected(user.getTrip(), user.getNick());
  }

  private boolean isProtectedNick(String nick) {
    if (nick == null || nick.isBlank() || engine.nick.equalsIgnoreCase(nick)) return true;
    EngineImpl root = engine.getHostRef() == null ? engine : engine.getHostRef();
    if (root.nick != null && root.nick.equalsIgnoreCase(nick)) return true;
    return root.replicasMappedByChannel.values().stream()
        .anyMatch(replica -> replica.nick != null && replica.nick.equalsIgnoreCase(nick));
  }
}
