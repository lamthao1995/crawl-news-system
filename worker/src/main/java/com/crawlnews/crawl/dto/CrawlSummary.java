package com.crawlnews.crawl.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public final class CrawlSummary implements Serializable {

  private static final long serialVersionUID = 1L;

  private int pagesFetched;
  private int skippedDuplicate;
  private int skippedAlreadyQueued;
  private int skippedTrap;
  private int circuitOpenWaits;
  private int circuitGaveUp;
  private int fetchErrors;
  private int skippedNonHtml;
  private List<String> fetchedUrls = new ArrayList<>();
  /** Set when the frontier was empty at start (no HTTP / no extract ran). */
  private String idleReason;

  public int getPagesFetched() {
    return pagesFetched;
  }

  public void setPagesFetched(int pagesFetched) {
    this.pagesFetched = pagesFetched;
  }

  public int getSkippedDuplicate() {
    return skippedDuplicate;
  }

  public void setSkippedDuplicate(int skippedDuplicate) {
    this.skippedDuplicate = skippedDuplicate;
  }

  public int getSkippedAlreadyQueued() {
    return skippedAlreadyQueued;
  }

  public void setSkippedAlreadyQueued(int skippedAlreadyQueued) {
    this.skippedAlreadyQueued = skippedAlreadyQueued;
  }

  public int getSkippedTrap() {
    return skippedTrap;
  }

  public void setSkippedTrap(int skippedTrap) {
    this.skippedTrap = skippedTrap;
  }

  public int getCircuitOpenWaits() {
    return circuitOpenWaits;
  }

  public void setCircuitOpenWaits(int circuitOpenWaits) {
    this.circuitOpenWaits = circuitOpenWaits;
  }

  public int getCircuitGaveUp() {
    return circuitGaveUp;
  }

  public void setCircuitGaveUp(int circuitGaveUp) {
    this.circuitGaveUp = circuitGaveUp;
  }

  public int getFetchErrors() {
    return fetchErrors;
  }

  public void setFetchErrors(int fetchErrors) {
    this.fetchErrors = fetchErrors;
  }

  public int getSkippedNonHtml() {
    return skippedNonHtml;
  }

  public void setSkippedNonHtml(int skippedNonHtml) {
    this.skippedNonHtml = skippedNonHtml;
  }

  public List<String> getFetchedUrls() {
    return fetchedUrls;
  }

  public void setFetchedUrls(List<String> fetchedUrls) {
    this.fetchedUrls = fetchedUrls;
  }

  public String getIdleReason() {
    return idleReason;
  }

  public void setIdleReason(String idleReason) {
    this.idleReason = idleReason;
  }
}
