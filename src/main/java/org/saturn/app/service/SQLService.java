package org.saturn.app.service;

public interface SQLService {
  String executeSql(String cmd, boolean withOutput);

  String executeFormatted(String sql);

  String getBasicUserData(String hash, String trip);
}
