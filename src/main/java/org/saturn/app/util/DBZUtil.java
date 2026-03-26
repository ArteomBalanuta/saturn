package org.saturn.app.util;

public final class DBZUtil {
  public static final String INSERT_INTO_CHARACTERS =
      "INSERT INTO dbz_characters(name, level, created_on) VALUES (?, ?, ?);";
  public static final String INSERT_INTO_STATS =
      "INSERT INTO dbz_stats(char_id, free_stats,str,agi,vit,ene, created_on) VALUES (?, ?, ?, ?, ?, ?, ?);";

  public static final String UPDATE_LEVEL_BY_NAME =
      "UPDATE dbz_characters SET level=level+1 WHERE name=?;";

  public static final String UPDATE_ADD_STATS_BY_NAME =
      "UPDATE dbz_stats SET free_stats=free_stats+5 WHERE char_id=( SELECT id FROM dbz_characters WHERE name = ?);";

  public static final String UPDATE_STR_BY_NAME =
      """
        UPDATE dbz_stats
        SET str = str + ?, free_stats = free_stats - ?
        WHERE char_id = (
            SELECT id
            FROM dbz_characters
            WHERE name = ?
        );
        """;

  public static final String UPDATE_AGI_BY_NAME =
      """
            UPDATE dbz_stats
            SET agi = agi + ?
            WHERE char_id = (
                SELECT id
                FROM dbz_characters
                WHERE name = ?
            );
            """;

  public static final String UPDATE_VIT_BY_NAME =
      """
            UPDATE dbz_stats
            SET vit = vit + ?
            WHERE char_id = (
                SELECT id
                FROM dbz_characters
                WHERE name = ?
            );
            """;

  public static final String UPDATE_ENE_BY_NAME =
      """
            UPDATE dbz_stats
            SET ene = ene + ?
            WHERE char_id = (
                SELECT id
                FROM dbz_characters
                WHERE name = ?
            );
            """;

  public static final String SELECT_CHAR_ID_BY_NAME =
      """
                   SELECT id FROM dbz_characters WHERE name = ?;
                   """;

  public static final String SELECT_STATS =
      """
               SELECT name,level,free_stats,str,agi,vit,ene FROM dbz_stats s INNER JOIN dbz_characters c on s.char_id = c.id WHERE c.name = ?;
               """;

  public static final String FREE_STATS =
      """
    SELECT free_stats from dbz_stats WHERE char_id = (SELECT id from dbz_characters WHERE name = ?) ;
""";
}
