package com.crawlnews.crawl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Durable dedup by canonical URL SHA-256. Optional: if {@code CRAWL_JDBC_URL} is unset, all methods
 * no-op / return false so local runs can omit Postgres.
 */
public final class PostgresUrlDedupStore implements AutoCloseable {

  private final HikariDataSource dataSource;

  public PostgresUrlDedupStore() {
    String jdbcUrl = env("CRAWL_JDBC_URL", "");
    if (jdbcUrl.isBlank()) {
      this.dataSource = null;
      return;
    }
    HikariConfig cfg = new HikariConfig();
    cfg.setJdbcUrl(jdbcUrl);
    cfg.setUsername(env("CRAWL_JDBC_USER", "crawl"));
    cfg.setPassword(env("CRAWL_JDBC_PASSWORD", "crawl"));
    cfg.setMaximumPoolSize(6);
    cfg.setMinimumIdle(0);
    cfg.setConnectionTimeout(10_000);
    cfg.setPoolName("crawl-dedup");
    this.dataSource = new HikariDataSource(cfg);
  }

  public boolean isEnabled() {
    return dataSource != null;
  }

  /**
   * @return true if this URL hash was already stored (skip fetch).
   */
  public boolean existsByUrlSha256(String urlSha256Hex) {
    if (dataSource == null) {
      return false;
    }
    String sql = "SELECT 1 FROM crawl_url_seen WHERE url_sha256 = ? LIMIT 1";
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, urlSha256Hex);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    } catch (Exception e) {
      System.err.println("[crawl-db] exists check failed (fail-open): " + e.getMessage());
      return false;
    }
  }

  public void recordSuccessfulFetch(
      String urlSha256Hex, String canonicalUrl, String finalUrl, String title, String bodyText) {
    if (dataSource == null) {
      return;
    }
    String contentHash =
        bodyText == null || bodyText.isBlank()
            ? null
            : Hashing.sha256HexNormalizedText(bodyText);
    String sql =
        "INSERT INTO crawl_url_seen (url_sha256, canonical_url, final_url, title, content_sha256) "
            + "VALUES (?,?,?,?,?) ON CONFLICT (url_sha256) DO NOTHING";
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, urlSha256Hex);
      ps.setString(2, canonicalUrl);
      ps.setString(3, finalUrl);
      ps.setString(4, title == null ? "" : title);
      if (contentHash == null) {
        ps.setNull(5, java.sql.Types.CHAR);
      } else {
        ps.setString(5, contentHash);
      }
      ps.executeUpdate();
    } catch (Exception e) {
      System.err.println("[crawl-db] insert failed: " + e.getMessage());
    }
  }

  @Override
  public void close() {
    if (dataSource != null) {
      dataSource.close();
    }
  }

  private static String env(String key, String defaultValue) {
    String v = System.getenv(key);
    return v == null || v.isBlank() ? defaultValue : v;
  }
}
