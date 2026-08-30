package org.saturn.app.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IdentityUtilTest {
  @Test
  void removesExactlyOneLeadingMarkerAndTrims() {
    assertEquals("alice", IdentityUtil.normalizeNickTarget("  @alice  "));
    assertEquals("@alice", IdentityUtil.normalizeNickTarget("@@alice"));
  }

  @Test
  void rejectsBlankAndBareMarker() {
    assertThrows(IllegalArgumentException.class, () -> IdentityUtil.normalizeNickTarget(" @ "));
    assertThrows(IllegalArgumentException.class, () -> IdentityUtil.normalizeNickTarget("  "));
  }

  @Test
  void comparesNickTargetsCaseInsensitively() {
    assertTrue(IdentityUtil.sameNick("@Alice", "alice"));
    assertFalse(IdentityUtil.sameNick("alice", "bob"));
  }
}
