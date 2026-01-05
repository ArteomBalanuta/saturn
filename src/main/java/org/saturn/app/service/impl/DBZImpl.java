package org.saturn.app.service.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.service.DBZService;
import org.saturn.app.util.DBZUtil;
import org.saturn.app.util.DateUtil;

@Slf4j
public class DBZImpl extends OutService implements DBZService {
  private final Connection connection;

  private final List<String> enemies = new ArrayList<>();

  public DBZImpl(Connection connection, BlockingQueue<String> queue) {
    super(queue);
    this.connection = connection;
  }

  @Override
  public void register(String name) {
    try {

      // TODO: check if exists.
      // char table
      PreparedStatement statement = connection.prepareStatement(DBZUtil.INSERT_INTO_CHARACTERS);
      statement.setString(1, name);
      statement.setInt(2, 1);
      statement.setLong(3, DateUtil.getTimestampNow());
      statement.executeUpdate();

      statement.close();

      // get gen id

      PreparedStatement charid = connection.prepareStatement(DBZUtil.SELECT_CHAR_ID_BY_NAME);
      charid.setString(1, name);
      ResultSet resultSet = charid.executeQuery();

      int charId = 0;
      if (resultSet.next()) {
        charId = resultSet.getInt("id");
        System.out.println("char id registered: " + charId);
      } else {
        throw new SQLException("Character not found: " + name);
      }

      charid.close();
      resultSet.close();

      // stats
      PreparedStatement stats = connection.prepareStatement(DBZUtil.INSERT_INTO_STATS);
      stats.setInt(1, charId);
      stats.setInt(2, 0);
      stats.setInt(3, 1);
      stats.setInt(4, 1);
      stats.setInt(5, 1);
      stats.setInt(6, 1);
      stats.setLong(7, DateUtil.getTimestampNow());
      stats.executeUpdate();

      stats.close();

      log.info("[DBZ] Registered character successfully");

    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Exception: ", e);
    }
  }

  @Override
  public void lvlUp(String name) {
    try {
      PreparedStatement statement = connection.prepareStatement(DBZUtil.UPDATE_LEVEL_BY_NAME);
      statement.setString(1, name);
      statement.executeUpdate();

      statement.close();
      log.info("[DBZ] Character: {} level up successfully", name);

      PreparedStatement stats = connection.prepareStatement(DBZUtil.UPDATE_ADD_STATS_BY_NAME);
      stats.setString(1, name);
      stats.executeUpdate();

      stats.close();
      log.info("[DBZ] Character: {} added 5 free stats successfully", name);

    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Exception: ", e);
    }
  }

  @Override
  public int addStr(String name, int str) {
    try {
      PreparedStatement statement = connection.prepareStatement(DBZUtil.UPDATE_STR_BY_NAME);
      statement.setInt(1, str);
      statement.setInt(2, str);
      statement.setString(3, name);
      statement.executeUpdate();

      statement.close();
      log.info("[DBZ] Character: {} str increased by {} successfully", name, str);
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Exception: ", e);
    }

    return -1;
  }

  @Override
  public int addAgi(String name, int agi) {
    try {
      PreparedStatement statement = connection.prepareStatement(DBZUtil.UPDATE_AGI_BY_NAME);
      statement.setInt(1, agi);
      statement.setString(2, name);
      statement.executeUpdate();

      statement.close();
      log.info("[DBZ] Character: {} agi increased by 1 successfully", name);
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Exception: ", e);
    }

    return -1;
  }

  @Override
  public int addVit(String name, int vit) {
    try {
      PreparedStatement statement = connection.prepareStatement(DBZUtil.UPDATE_VIT_BY_NAME);
      statement.setInt(1, vit);
      statement.setString(2, name);
      statement.executeUpdate();

      statement.close();
      log.info("[DBZ] Character: {} vit increased by 1 successfully", name);
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Exception: ", e);
    }

    return -1;
  }

  @Override
  public int addEne(String name, int ene) {
    try {
      PreparedStatement statement = connection.prepareStatement(DBZUtil.UPDATE_ENE_BY_NAME);
      statement.setInt(1, ene);
      statement.setString(2, name);
      statement.executeUpdate();

      statement.close();
      log.info("[DBZ] Character: {} ene increased by 1 successfully", name);
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Exception: ", e);
    }

    return -1;
  }

  @Override
  public String getStats(String name) {
    StringBuilder stats = new StringBuilder();
    try {
      PreparedStatement statement = connection.prepareStatement(DBZUtil.SELECT_STATS);
      statement.setString(1, name);
      statement.execute();

      ResultSet resultSet = statement.getResultSet();

      if (resultSet.next()) {
        stats.append("character: ").append(name).append("\n");
        stats.append("level: ").append(resultSet.getString("level")).append("\n");

        stats.append("free stats: ").append(resultSet.getString("free_stats")).append("\n");
        stats.append("str: ").append(resultSet.getString("str")).append("\n");
        stats.append("agi: ").append(resultSet.getString("agi")).append("\n");
        stats.append("vit: ").append(resultSet.getString("vit")).append("\n");
        stats.append("ene: ").append(resultSet.getString("ene")).append("\n");
      } else {
        stats.append("No stats found for character: ").append(name);
      }

      resultSet.close();
      statement.close();
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);
    }

    return stats.toString();
  }

  @Override
  public int getFreeStats(String name) {
    try {
      PreparedStatement statement = connection.prepareStatement(DBZUtil.FREE_STATS);
      statement.setString(1, name);
      statement.execute();

      ResultSet resultSet = statement.getResultSet();

      if (resultSet.next()) {
        return resultSet.getInt("free_stats");
      }

      resultSet.close();
      statement.close();
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);
    }

    return -1;
  }

  @Override
  public void fight(String name) {
    this.enemies.remove(name);
  }

  @Override
  public void spawnEnemy(String name) {
    this.enemies.add(name);
  }
}
