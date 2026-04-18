package com.crawlnews.crawl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

final class HashingTest {

  @Test
  void sha256Hex_knownVector() {
    assertEquals(
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        Hashing.sha256Hex(""));
    assertEquals(
        "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
        Hashing.sha256Hex("hello"));
  }

  @Test
  void sha256HexNormalizedText_collapsesWhitespace() {
    String a = Hashing.sha256HexNormalizedText("hello   world\n\n");
    String b = Hashing.sha256HexNormalizedText("hello world");
    assertEquals(a, b);
  }

  @Test
  void sha256HexNormalizedText_nullEqualsEmpty() {
    assertEquals(Hashing.sha256HexNormalizedText(null), Hashing.sha256HexNormalizedText(""));
    assertNotEquals(Hashing.sha256HexNormalizedText(null), Hashing.sha256HexNormalizedText("x"));
  }
}
