package org.saturn.app.model.dto;

public class Proxy {
  boolean isUsed = false;
  String ip;
  String port;

  public Proxy(boolean isUsed, String ip, String port) {
    this.isUsed = isUsed;
    this.ip = ip;
    this.port = port;
  }

  public boolean isUsed() {
    return isUsed;
  }

  public void setUsed(boolean used) {
    isUsed = used;
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public String getPort() {
    return port;
  }

  public void setPort(String port) {
    this.port = port;
  }

  @Override
  public String toString() {
    return ip + ":" + port;
  }
}
