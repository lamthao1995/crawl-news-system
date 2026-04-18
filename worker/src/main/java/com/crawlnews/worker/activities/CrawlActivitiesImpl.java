package com.crawlnews.worker.activities;

import com.crawlnews.crawl.Hashing;
import com.crawlnews.crawl.PostgresUrlDedupStore;
import com.crawlnews.crawl.RedisDomainCircuitBreaker;
import com.crawlnews.crawl.UrlNormalize;
import com.crawlnews.crawl.dto.PageFetchResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.SetParams;

public final class CrawlActivitiesImpl implements CrawlActivities, AutoCloseable {

  private static final int CLAIM_TTL_SECONDS = 7 * 24 * 3600;
  private static final String CLAIM_PREFIX = "crawl:v1:url:";
  private static final long DEFAULT_MAX_BODY_BYTES = 4L * 1024 * 1024; // 4 MiB
  private static final String USER_AGENT =
      "Mozilla/5.0 (compatible; CrawlNewsSystem/1.0; respectful crawl; "
          + "+https://github.com/lamthao1995/crawl-news-system)";

  private final JedisPooled jedis;
  private final RedisDomainCircuitBreaker circuitBreaker;
  private final PostgresUrlDedupStore postgresDedup;
  private final long perUrlSleepMs;
  private final long maxBodyBytes;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(15))
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

  public CrawlActivitiesImpl() {
    this.jedis = buildJedis();
    this.circuitBreaker = RedisDomainCircuitBreaker.fromEnv(jedis);
    this.postgresDedup = new PostgresUrlDedupStore();
    this.perUrlSleepMs = Math.max(0L, parseLong(env("CRAWL_PER_URL_SLEEP_MS", "350"), 350L));
    this.maxBodyBytes =
        Math.max(
            64L * 1024,
            parseLong(env("CRAWL_MAX_BODY_BYTES", Long.toString(DEFAULT_MAX_BODY_BYTES)),
                DEFAULT_MAX_BODY_BYTES));
  }

  private static JedisPooled buildJedis() {
    String url = env("REDIS_URL", "redis://127.0.0.1:6379/0");
    URI u = URI.create(url);
    // JedisPooled(URI) honors scheme (redis/rediss), userinfo (user:pass), path-based DB index,
    // and supported query options — everything a hosted Redis URL typically carries.
    return new JedisPooled(u);
  }

  @Override
  public PageFetchResult fetchWithRedisClaim(
      String canonicalUrl, int maxLinks, String allowedHostSuffix, int maxPathSegments) {
    String url = UrlNormalize.forDedup(canonicalUrl);
    if (url.isEmpty() || !UrlNormalize.isAllowedHost(url, allowedHostSuffix)) {
      return PageFetchResult.failure("invalid or disallowed url");
    }
    if (UrlNormalize.looksLikePathTrap(url, maxPathSegments)) {
      return PageFetchResult.failure("path trap");
    }
    String urlSha = Hashing.sha256Hex(url);
    if (postgresDedup.isEnabled() && postgresDedup.existsByUrlSha256(urlSha)) {
      return PageFetchResult.skippedDuplicate();
    }
    String crawlHost = hostOf(url);
    if (circuitBreaker.isOpen(crawlHost)) {
      return PageFetchResult.circuitOpen();
    }
    String key = CLAIM_PREFIX + urlSha;
    SetParams params = SetParams.setParams().nx().ex(CLAIM_TTL_SECONDS);
    String set = jedis.set(key, "1", params);
    if (!"OK".equalsIgnoreCase(set)) {
      return PageFetchResult.skippedDuplicate();
    }
    if (perUrlSleepMs > 0) {
      try {
        Thread.sleep(perUrlSleepMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        jedis.del(key);
        return PageFetchResult.failure("interrupted");
      }
    }
    try {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(30))
              .header("User-Agent", USER_AGENT)
              .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
              .header("Accept-Encoding", "gzip")
              .header("Accept-Language", "en,vi;q=0.9,*;q=0.5")
              .GET()
              .build();
      // Streaming: we never allocate the full response body; the cap is enforced while reading.
      HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
      try (InputStream respBody = resp.body()) {
        if (resp.statusCode() < 200 || resp.statusCode() >= 400) {
          jedis.del(key);
          if (shouldTripCircuit(resp.statusCode())) {
            circuitBreaker.recordFailure(crawlHost);
          }
          return PageFetchResult.failure("http " + resp.statusCode());
        }
        URI finalUri = resp.uri();
        String finalUrl = UrlNormalize.forDedup(finalUri.toString());
        if (!UrlNormalize.isAllowedHost(finalUrl, allowedHostSuffix)) {
          jedis.del(key);
          return PageFetchResult.failure("redirected off-domain");
        }
        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        if (!isHtmlLikeContentType(contentType)) {
          jedis.del(key);
          return PageFetchResult.skippedNonHtml(contentType);
        }
        String contentEncoding = resp.headers().firstValue("Content-Encoding").orElse("");
        byte[] body = decodeStream(respBody, contentEncoding, maxBodyBytes);
        // Jsoup with null charset auto-detects from HTTP/HTML meta; avoids blind UTF-8 decoding.
        Document doc = Jsoup.parse(new ByteArrayInputStream(body), null, finalUrl);
      String title = doc.title();
      String bodyText = doc.body() != null ? doc.body().text() : "";
      Set<String> links = new LinkedHashSet<>();
      Elements anchors = doc.select("a[href]");
      for (Element a : anchors) {
        if (links.size() >= maxLinks) {
          break;
        }
        String href = a.attr("href");
        if (href == null || href.isBlank() || href.startsWith("javascript:") || href.startsWith("#")) {
          continue;
        }
        String abs;
        try {
          abs = a.absUrl("href");
        } catch (Exception e) {
          continue;
        }
        String canon = UrlNormalize.forDedup(abs);
        if (canon.isEmpty()
            || !UrlNormalize.isAllowedHost(canon, allowedHostSuffix)
            || UrlNormalize.looksLikePathTrap(canon, maxPathSegments)) {
          continue;
        }
        links.add(canon);
      }
        circuitBreaker.recordSuccess(hostOf(finalUrl));
        postgresDedup.recordSuccessfulFetch(urlSha, url, finalUrl, title, bodyText);
        // Mirror dedup under the redirected URL's hash so a later fetch of finalUrl is recognized
        // both by Postgres AND by a Redis SET NX claim (important when Postgres is disabled).
        if (!finalUrl.equals(url)) {
          String finalSha = Hashing.sha256Hex(finalUrl);
          postgresDedup.recordSuccessfulFetch(finalSha, finalUrl, finalUrl, title, bodyText);
          SetParams finalParams = SetParams.setParams().nx().ex(CLAIM_TTL_SECONDS);
          jedis.set(CLAIM_PREFIX + finalSha, "1", finalParams);
        }
        return PageFetchResult.success(finalUrl, title, new ArrayList<>(links));
      }
    } catch (Exception e) {
      jedis.del(key);
      circuitBreaker.recordFailure(crawlHost);
      return PageFetchResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  private static boolean shouldTripCircuit(int status) {
    return status >= 500 || status == 429;
  }

  private static String hostOf(String canonicalUrl) {
    try {
      String h = URI.create(canonicalUrl).getHost();
      return h != null ? h.toLowerCase(Locale.ROOT) : "";
    } catch (Exception e) {
      return "";
    }
  }

  private static String env(String key, String defaultValue) {
    String v = System.getenv(key);
    return v == null || v.isBlank() ? defaultValue : v;
  }

  private static long parseLong(String raw, long defaultValue) {
    try {
      return Long.parseLong(raw.trim());
    } catch (Exception e) {
      return defaultValue;
    }
  }

  /**
   * Streams from {@code in}, gunzipping if {@code contentEncoding} is {@code gzip}, and caps at
   * {@code maxBytes}. Returned array size is bounded by {@code maxBytes}; the full payload is never
   * allocated up front.
   */
  static byte[] decodeStream(InputStream in, String contentEncoding, long maxBytes)
      throws IOException {
    InputStream stream = in;
    if ("gzip".equalsIgnoreCase(contentEncoding)) {
      stream = new GZIPInputStream(in);
    }
    return readBounded(stream, maxBytes);
  }

  /** Convenience for tests that already have a byte[] in memory. */
  static byte[] decodeBody(byte[] raw, String contentEncoding, long maxBytes) throws IOException {
    if (raw == null) {
      return new byte[0];
    }
    return decodeStream(new ByteArrayInputStream(raw), contentEncoding, maxBytes);
  }

  private static byte[] readBounded(InputStream in, long maxBytes) throws IOException {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    byte[] buf = new byte[16 * 1024];
    long total = 0;
    int n;
    while ((n = in.read(buf)) != -1) {
      long allowed = Math.max(0, maxBytes - total);
      int toWrite = (int) Math.min(n, allowed);
      if (toWrite > 0) {
        out.write(buf, 0, toWrite);
        total += toWrite;
      }
      if (total >= maxBytes) {
        break;
      }
    }
    return out.toByteArray();
  }

  /** Returns true for HTML-like content types; empty string is permissive (no header). */
  static boolean isHtmlLikeContentType(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return true;
    }
    String first = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    return first.equals("text/html")
        || first.equals("application/xhtml+xml")
        || first.equals("application/xml+xhtml");
  }

  @Override
  public void close() {
    try {
      jedis.close();
    } catch (Exception e) {
      System.err.println("[crawl-worker] Redis pool close failed: " + e.getMessage());
    }
    try {
      postgresDedup.close();
    } catch (Exception e) {
      System.err.println("[crawl-worker] Postgres pool close failed: " + e.getMessage());
    }
  }
}
