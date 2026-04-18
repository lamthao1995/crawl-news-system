package com.crawlnews.worker.activities;

import com.crawlnews.crawl.dto.PageFetchResult;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scripted test double for {@link CrawlActivities}. For each URL, you can queue a sequence of
 * {@link PageFetchResult} responses (consumed in order; last one is reused once exhausted).
 *
 * <p>Usage:
 *
 * <pre>{@code
 * FakeCrawlActivities fake = new FakeCrawlActivities();
 * fake.scriptSuccess("https://x/", "X", List.of("https://x/a"));
 * fake.scriptDefault(PageFetchResult.failure("not scripted"));
 * }</pre>
 */
public final class FakeCrawlActivities implements CrawlActivities {

  private final Map<String, Deque<PageFetchResult>> script = new ConcurrentHashMap<>();
  private final Map<String, Integer> callCount = new ConcurrentHashMap<>();
  private volatile PageFetchResult defaultResponse =
      PageFetchResult.failure("not scripted in test");

  public void scriptResponses(String url, PageFetchResult... responses) {
    script
        .computeIfAbsent(url, k -> new ArrayDeque<>())
        .addAll(List.of(responses));
  }

  public void scriptSuccess(String url, String title, List<String> links) {
    scriptResponses(url, PageFetchResult.success(url, title, links));
  }

  /** For redirect scenarios: request URL returns a PageFetchResult whose finalUrl differs. */
  public void scriptSuccessWithFinalUrl(
      String requestedUrl, String finalUrl, String title, List<String> links) {
    scriptResponses(requestedUrl, PageFetchResult.success(finalUrl, title, links));
  }

  public void scriptNonHtml(String url, String contentType) {
    scriptResponses(url, PageFetchResult.skippedNonHtml(contentType));
  }

  public void scriptCircuitThenSuccess(String url, int circuitHits, List<String> links) {
    PageFetchResult[] seq = new PageFetchResult[circuitHits + 1];
    for (int i = 0; i < circuitHits; i++) {
      seq[i] = PageFetchResult.circuitOpen();
    }
    seq[circuitHits] = PageFetchResult.success(url, "t", links);
    scriptResponses(url, seq);
  }

  public void scriptAlwaysCircuitOpen(String url) {
    scriptResponses(url, PageFetchResult.circuitOpen());
  }

  public void scriptDuplicate(String url) {
    scriptResponses(url, PageFetchResult.skippedDuplicate());
  }

  public void scriptFailure(String url, String message) {
    scriptResponses(url, PageFetchResult.failure(message));
  }

  public void scriptDefault(PageFetchResult response) {
    this.defaultResponse = response;
  }

  public int callsFor(String url) {
    return callCount.getOrDefault(url, 0);
  }

  @Override
  public PageFetchResult fetchWithRedisClaim(
      String canonicalUrl, int maxLinks, String allowedHostSuffix, int maxPathSegments) {
    callCount.merge(canonicalUrl, 1, (a, b) -> a + b);
    Deque<PageFetchResult> queue = script.get(canonicalUrl);
    if (queue == null || queue.isEmpty()) {
      return defaultResponse;
    }
    // Keep the last response around so repeated polling returns it (useful for "always X").
    if (queue.size() == 1) {
      return queue.peek();
    }
    return queue.poll();
  }

  public Map<String, Integer> snapshotCallCounts() {
    return Collections.unmodifiableMap(new java.util.HashMap<>(callCount));
  }
}
