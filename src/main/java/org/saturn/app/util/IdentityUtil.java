package org.saturn.app.util;

import java.util.Locale;

/** Shared normalization for user nick/name targets. */
public final class IdentityUtil {
  private IdentityUtil() {}

  public static String normalizeNickTarget(String raw) {
    if (raw == null) throw new IllegalArgumentException("Nick target cannot be blank");
    String value = raw.trim();
    if (value.startsWith("@")) value = value.substring(1).trim();
    if (value.isBlank()) throw new IllegalArgumentException("Nick target cannot be blank");
    return value;
  }

  public static String canonicalNick(String raw) {
    return normalizeNickTarget(raw).toLowerCase(Locale.ROOT);
  }

  public static boolean sameNick(String left, String right) {
    return left != null && right != null && canonicalNick(left).equals(canonicalNick(right));
  }
}
