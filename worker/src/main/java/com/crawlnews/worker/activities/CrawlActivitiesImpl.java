package com.crawlnews.worker.activities;

import com.crawlnews.crawl.Hashing;
import com.crawlnews.crawl.PostgresUrlDedupStore;
import com.crawlnews.crawl.RedisDomainCircuitBreaker;
import com.crawlnews.crawl.UrlNormalize;
import com.crawlnews.crawl.dto.PageFetchResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.SetParams;

public final class CrawlActivitiesImpl implements CrawlActivities {

  private static final int CLAIM_TTL_SECONDS = 7 * 24 * 3600;
  private static final String CLAIM_PREFIX = "crawl:v1:url:";

  private final JedisPooled jedis;
  private final RedisDomainCircuitBreaker circuitBreaker;
  private final PostgresUrlDedupStore postgresDedup;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(15))
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

  public CrawlActivitiesImpl() {
    this.jedis = buildJedis();
    this.circuitBreaker = RedisDomainCircuitBreaker.fromEnv(jedis);
    this.postgresDedup = new PostgresUrlDedupStore();
  }

  private static JedisPooled buildJedis() {
    String url = env("REDIS_URL", "redis://127.0.0.1:6379/0");
    URI u = URI.create(url);
    String host = u.getHost() != null ? u.getHost() : "127.0.0.1";
    int port = u.getPort() > 0 ? u.getPort() : 6379;
    if ("rediss".equalsIgnoreCase(u.getScheme())) {
      return new JedisPooled(host, port, true);
    }
    return new JedisPooled(host, port);
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
    try {
      Thread.sleep(350);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      jedis.del(key);
      return PageFetchResult.failure("interrupted");
    }
    try {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(30))
              .header(
                  "User-Agent",
                  "Mozilla/5.0 (compatible; CrawlNewsSystem/1.0; respectful crawl; +https://github.com/)")
              .GET()
              .build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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
      Document doc = Jsoup.parse(resp.body(), finalUrl);
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
      return PageFetchResult.success(finalUrl, title, new ArrayList<>(links));
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
}
