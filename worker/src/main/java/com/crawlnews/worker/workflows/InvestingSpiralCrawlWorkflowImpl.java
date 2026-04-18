package com.crawlnews.worker.workflows;

import com.crawlnews.crawl.FrontierItem;
import com.crawlnews.crawl.UrlNormalize;
import com.crawlnews.crawl.dto.CrawlSummary;
import com.crawlnews.crawl.dto.InvestingCrawlInput;
import com.crawlnews.crawl.dto.PageFetchResult;
import com.crawlnews.worker.activities.CrawlActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class InvestingSpiralCrawlWorkflowImpl implements InvestingSpiralCrawlWorkflow {

  private final CrawlActivities activities =
      Workflow.newActivityStub(
          CrawlActivities.class,
          ActivityOptions.newBuilder()
              .setStartToCloseTimeout(Duration.ofMinutes(2))
              .setRetryOptions(
                  RetryOptions.newBuilder()
                      .setMaximumAttempts(4)
                      .setInitialInterval(Duration.ofSeconds(2))
                      .setBackoffCoefficient(2.0)
                      .build())
              .build());

  @Override
  public CrawlSummary run(InvestingCrawlInput input) {
    InvestingCrawlInput in = input != null ? input : new InvestingCrawlInput();
    String suffix = safeSuffix(in.getAllowedHostSuffix());
    String seed = UrlNormalize.forDedup(in.getSeedUrl());
    int maxDepth = clamp(in.getMaxDepth(), 0, 5);
    int maxPages = clamp(in.getMaxPages(), 1, 150);
    int maxLinks = clamp(in.getMaxOutboundLinksPerPage(), 1, 80);
    int maxPathSeg = clamp(in.getMaxPathSegments(), 4, 40);
    int circuitSleepSec = clamp(in.getCircuitSleepSeconds(), 5, 600);
    int circuitMaxWaits = clamp(in.getCircuitMaxWaitsPerUrl(), 1, 200);

    List<FrontierItem> frontier = new ArrayList<>();
    Set<String> queuedThisRun = new HashSet<>();
    if (!seed.isEmpty() && UrlNormalize.isAllowedHost(seed, suffix)) {
      frontier.add(new FrontierItem(seed, 0));
      queuedThisRun.add(seed);
    }

    CrawlSummary summary = new CrawlSummary();
    if (frontier.isEmpty()) {
      String raw = in.getSeedUrl();
      if (raw == null || raw.isBlank()) {
        summary.setIdleReason("missing_or_blank_seed_url");
      } else if (seed.isEmpty()) {
        summary.setIdleReason(
            "seed_normalized_to_empty;input="
                + sanitizeForReason(raw, 120));
      } else {
        summary.setIdleReason(
            "seed_host_not_allowed_for_suffix="
                + suffix
                + ";parsedHost="
                + hostFromCanonical(seed)
                + ";normalized="
                + sanitizeForReason(seed, 140));
      }
    }
    int idx = 0;
    int fetched = 0;
    int circuitWaitsForCurrent = 0;

    while (idx < frontier.size() && fetched < maxPages) {
      FrontierItem cur = frontier.get(idx);
      if (cur.getDepth() > maxDepth) {
        idx++;
        circuitWaitsForCurrent = 0;
        continue;
      }
      if (UrlNormalize.looksLikePathTrap(cur.getCanonicalUrl(), maxPathSeg)) {
        summary.setSkippedTrap(summary.getSkippedTrap() + 1);
        idx++;
        circuitWaitsForCurrent = 0;
        continue;
      }

      PageFetchResult page =
          activities.fetchWithRedisClaim(
              cur.getCanonicalUrl(), maxLinks, suffix, maxPathSeg);

      if (page.isCircuitOpen()) {
        circuitWaitsForCurrent++;
        summary.setCircuitOpenWaits(summary.getCircuitOpenWaits() + 1);
        if (circuitWaitsForCurrent > circuitMaxWaits) {
          summary.setCircuitGaveUp(summary.getCircuitGaveUp() + 1);
          idx++;
          circuitWaitsForCurrent = 0;
          continue;
        }
        Workflow.sleep(Duration.ofSeconds(circuitSleepSec));
        continue;
      }

      circuitWaitsForCurrent = 0;

      if (page.isSkippedAsDuplicate()) {
        summary.setSkippedDuplicate(summary.getSkippedDuplicate() + 1);
        idx++;
        continue;
      }
      if (!page.isOk()) {
        summary.setSkippedTrap(summary.getSkippedTrap() + 1);
        idx++;
        continue;
      }

      fetched++;
      summary.getFetchedUrls().add(page.getFinalUrl());
      idx++;

      if (cur.getDepth() >= maxDepth) {
        continue;
      }

      for (String link : page.getSameOriginCanonicalUrls()) {
        if (link.equals(page.getFinalUrl()) || link.equals(cur.getCanonicalUrl())) {
          continue;
        }
        if (UrlNormalize.looksLikePathTrap(link, maxPathSeg)) {
          summary.setSkippedTrap(summary.getSkippedTrap() + 1);
          continue;
        }
        if (!queuedThisRun.add(link)) {
          summary.setSkippedAlreadyQueued(summary.getSkippedAlreadyQueued() + 1);
          continue;
        }
        frontier.add(new FrontierItem(link, cur.getDepth() + 1));
      }
    }

    summary.setPagesFetched(fetched);
    return summary;
  }

  private static String safeSuffix(String s) {
    if (s == null || s.isBlank()) {
      return "investing.com";
    }
    String t = s.trim().toLowerCase(Locale.ROOT);
    if (t.indexOf(' ') >= 0 || t.indexOf('/') >= 0 || t.indexOf('\\') >= 0) {
      return "investing.com";
    }
    return t;
  }

  private static int clamp(int v, int lo, int hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  private static String hostFromCanonical(String canonicalUrl) {
    try {
      String h = URI.create(canonicalUrl).getHost();
      return h != null ? h : "";
    } catch (Exception e) {
      return "";
    }
  }

  /** Safe one-line snippet for idleReason (no CR/LF, bounded length). */
  private static String sanitizeForReason(String s, int maxChars) {
    if (s == null || maxChars <= 0) {
      return "";
    }
    StringBuilder b = new StringBuilder(Math.min(s.length(), maxChars));
    for (int i = 0; i < s.length() && b.length() < maxChars; i++) {
      char c = s.charAt(i);
      if (c < 32 || c == 127 || c == '"' || c == '\\') {
        b.append('_');
      } else {
        b.append(c);
      }
    }
    return b.toString();
  }
}
