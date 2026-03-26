package org.saturn.app.service;

public interface DBZService {
  void register(String name);

  void lvlUp(String name);

  int addStr(String name, int str);

  int addAgi(String name, int agi);

  int addVit(String name, int vit);

  int addEne(String name, int ene);

  String getStats(String name);

  int getFreeStats(String name);

  void fight(String name);

  void spawnEnemy(String name);
}
