package com.crawlnews.crawl.dto;

import java.io.Serializable;

public final class InvestingCrawlInput implements Serializable {

  private static final long serialVersionUID = 1L;

  private String seedUrl = "https://www.investing.com/";
  private String allowedHostSuffix = "investing.com";
  private int maxDepth = 2;
  private int maxPages = 30;
  private int maxOutboundLinksPerPage = 18;
  private int maxPathSegments = 14;
  private int circuitSleepSeconds = 45;
  private int circuitMaxWaitsPerUrl = 24;

  public String getSeedUrl() {
    return seedUrl;
  }

  public void setSeedUrl(String seedUrl) {
    this.seedUrl = seedUrl;
  }

  public String getAllowedHostSuffix() {
    return allowedHostSuffix;
  }

  public void setAllowedHostSuffix(String allowedHostSuffix) {
    this.allowedHostSuffix = allowedHostSuffix;
  }

  public int getMaxDepth() {
    return maxDepth;
  }

  public void setMaxDepth(int maxDepth) {
    this.maxDepth = maxDepth;
  }

  public int getMaxPages() {
    return maxPages;
  }

  public void setMaxPages(int maxPages) {
    this.maxPages = maxPages;
  }

  public int getMaxOutboundLinksPerPage() {
    return maxOutboundLinksPerPage;
  }

  public void setMaxOutboundLinksPerPage(int maxOutboundLinksPerPage) {
    this.maxOutboundLinksPerPage = maxOutboundLinksPerPage;
  }

  public int getMaxPathSegments() {
    return maxPathSegments;
  }

  public void setMaxPathSegments(int maxPathSegments) {
    this.maxPathSegments = maxPathSegments;
  }

  public int getCircuitSleepSeconds() {
    return circuitSleepSeconds;
  }

  public void setCircuitSleepSeconds(int circuitSleepSeconds) {
    this.circuitSleepSeconds = circuitSleepSeconds;
  }

  public int getCircuitMaxWaitsPerUrl() {
    return circuitMaxWaitsPerUrl;
  }

  public void setCircuitMaxWaitsPerUrl(int circuitMaxWaitsPerUrl) {
    this.circuitMaxWaitsPerUrl = circuitMaxWaitsPerUrl;
  }
}
