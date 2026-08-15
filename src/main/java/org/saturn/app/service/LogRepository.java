package org.saturn.app.service;

import org.saturn.app.model.MessageAuditEvent;

public interface LogRepository {
  void logCommand(
      String trip, String cmd, String arguments, String status, String channel, long created_on);

  void logMessage(MessageAuditEvent event);
}
