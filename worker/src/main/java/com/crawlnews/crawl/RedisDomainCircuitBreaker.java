package com.crawlnews.crawl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import redis.clients.jedis.JedisPooled;

/**
 * Counts consecutive HTTP failures per host in Redis. After a threshold, opens the circuit until a
 * wall-clock deadline so workers do not hammer a dead domain. Success clears state for that host.
 */
public final class RedisDomainCircuitBreaker {

  private static final String KEY_PREFIX = "crawl:cb:v1:host:";

  private final JedisPooled jedis;
  private final int failureThreshold;
  private final long openBackoffMillis;

  public RedisDomainCircuitBreaker(
      JedisPooled jedis, int failureThreshold, long openBackoffMillis) {
    this.jedis = Objects.requireNonNull(jedis);
    this.failureThreshold = Math.max(1, failureThreshold);
    this.openBackoffMillis = Math.max(5_000L, openBackoffMillis);
  }

  public static RedisDomainCircuitBreaker fromEnv(JedisPooled jedis) {
    int threshold = parseInt(env("CRAWL_CB_FAILURE_THRESHOLD", "5"), 5);
    int openSeconds = parseInt(env("CRAWL_CB_OPEN_SECONDS", "90"), 90);
    return new RedisDomainCircuitBreaker(jedis, threshold, openSeconds * 1000L);
  }

  /** @return true if requests for this host should fail fast (circuit open). */
  public boolean isOpen(String host) {
    String h = normalizeHost(host);
    if (h.isEmpty()) {
      return false;
    }
    String key = keyForHost(h);
    String until = jedis.hget(key, "openUntilMs");
    if (until == null || until.isBlank()) {
      return false;
    }
    try {
      return Long.parseLong(until.trim()) > System.currentTimeMillis();
    } catch (NumberFormatException e) {
      return false;
    }
  }

  public void recordSuccess(String host) {
    String h = normalizeHost(host);
    if (h.isEmpty()) {
      return;
    }
    jedis.del(keyForHost(h));
  }

  /** Count toward opening the circuit (HTTP-level failure after a real attempt). */
  public void recordFailure(String host) {
    String h = normalizeHost(host);
    if (h.isEmpty()) {
      return;
    }
    String key = keyForHost(h);
    long failures = jedis.hincrBy(key, "failures", 1);
    if (failures >= failureThreshold) {
      long now = System.currentTimeMillis();
      jedis.hset(key, "openUntilMs", Long.toString(now + openBackoffMillis));
      jedis.hset(key, "failures", "0");
    }
    long ttl = openBackoffMillis / 1000 + 86_400L;
    jedis.expire(key, (int) Math.min(ttl, Integer.MAX_VALUE));
  }

  private static String keyForHost(String hostLower) {
    return KEY_PREFIX + sha256Hex(hostLower);
  }

  private static String normalizeHost(String host) {
    if (host == null) {
      return "";
    }
    return host.trim().toLowerCase(Locale.ROOT);
  }

  private static String sha256Hex(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static int parseInt(String raw, int defaultValue) {
    try {
      return Integer.parseInt(raw.trim());
    } catch (Exception e) {
      return defaultValue;
    }
  }

  private static String env(String key, String defaultValue) {
    String v = System.getenv(key);
    return v == null || v.isBlank() ? defaultValue : v;
  }
}
