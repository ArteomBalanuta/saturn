package org.saturn.app.service;

public interface SCPService {
  void executeRandomSCP(String author);

  String getSCPDescription(int scpId);
}
