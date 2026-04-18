package com.crawlnews.crawl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class Hashing {

  private Hashing() {}

  public static String sha256Hex(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** Normalized text fingerprint for near-duplicate bodies (optional). */
  public static String sha256HexNormalizedText(String text) {
    if (text == null) {
      return sha256Hex("");
    }
    String collapsed = text.replaceAll("\\s+", " ").trim();
    return sha256Hex(collapsed);
  }
}
