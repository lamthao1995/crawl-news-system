package com.crawlnews.crawl.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PageFetchResult implements Serializable {

  private static final long serialVersionUID = 1L;

  private boolean ok;
  private boolean skippedAsDuplicate;
  private boolean circuitOpen;
  private String finalUrl;
  private String title;
  private List<String> sameOriginCanonicalUrls = new ArrayList<>();
  private String errorMessage;

  public static PageFetchResult circuitOpen() {
    PageFetchResult r = new PageFetchResult();
    r.circuitOpen = true;
    r.ok = false;
    r.sameOriginCanonicalUrls = Collections.emptyList();
    return r;
  }

  public static PageFetchResult skippedDuplicate() {
    PageFetchResult r = new PageFetchResult();
    r.skippedAsDuplicate = true;
    r.ok = false;
    r.sameOriginCanonicalUrls = Collections.emptyList();
    return r;
  }

  public static PageFetchResult failure(String message) {
    PageFetchResult r = new PageFetchResult();
    r.ok = false;
    r.errorMessage = message;
    r.sameOriginCanonicalUrls = Collections.emptyList();
    return r;
  }

  public static PageFetchResult success(String finalUrl, String title, List<String> links) {
    PageFetchResult r = new PageFetchResult();
    r.ok = true;
    r.finalUrl = finalUrl;
    r.title = title;
    r.sameOriginCanonicalUrls = new ArrayList<>(links);
    return r;
  }

  public boolean isOk() {
    return ok;
  }

  public boolean isSkippedAsDuplicate() {
    return skippedAsDuplicate;
  }

  public boolean isCircuitOpen() {
    return circuitOpen;
  }

  public String getFinalUrl() {
    return finalUrl;
  }

  public String getTitle() {
    return title;
  }

  public List<String> getSameOriginCanonicalUrls() {
    return sameOriginCanonicalUrls;
  }

  public String getErrorMessage() {
    return errorMessage;
  }
}
