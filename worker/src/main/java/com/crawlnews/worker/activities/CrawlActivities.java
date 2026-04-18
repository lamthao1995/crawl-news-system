package com.crawlnews.worker.activities;

import com.crawlnews.crawl.dto.PageFetchResult;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface CrawlActivities {

  /**
   * Redis {@code SET NX} then HTTP fetch + link extraction. If {@code SET NX} fails, returns {@link
   * PageFetchResult#skippedDuplicate()}. If fetch fails after a successful claim, the claim key is
   * deleted so Temporal retries (or later runs) are not blocked.
   */
  @ActivityMethod
  PageFetchResult fetchWithRedisClaim(
      String canonicalUrl, int maxLinks, String allowedHostSuffix, int maxPathSegments);
}
