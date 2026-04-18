package com.crawlnews.crawl.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

final class CrawlSummaryTest {

  @Test
  void defaults_zerosAndEmpty() {
    CrawlSummary s = new CrawlSummary();
    assertEquals(0, s.getPagesFetched());
    assertEquals(0, s.getSkippedDuplicate());
    assertEquals(0, s.getSkippedAlreadyQueued());
    assertEquals(0, s.getSkippedTrap());
    assertEquals(0, s.getCircuitOpenWaits());
    assertEquals(0, s.getCircuitGaveUp());
    assertEquals(0, s.getFetchErrors());
    assertEquals(0, s.getSkippedNonHtml());
    assertNull(s.getIdleReason());
    assertNotNull(s.getFetchedUrls());
    assertEquals(0, s.getFetchedUrls().size());
  }

  @Test
  void setters_roundTrip() {
    CrawlSummary s = new CrawlSummary();
    s.setPagesFetched(10);
    s.setSkippedDuplicate(1);
    s.setSkippedAlreadyQueued(2);
    s.setSkippedTrap(3);
    s.setCircuitOpenWaits(4);
    s.setCircuitGaveUp(5);
    s.setFetchErrors(6);
    s.setSkippedNonHtml(7);
    s.setIdleReason("because");
    s.setFetchedUrls(List.of("https://x/"));
    assertEquals(10, s.getPagesFetched());
    assertEquals(1, s.getSkippedDuplicate());
    assertEquals(2, s.getSkippedAlreadyQueued());
    assertEquals(3, s.getSkippedTrap());
    assertEquals(4, s.getCircuitOpenWaits());
    assertEquals(5, s.getCircuitGaveUp());
    assertEquals(6, s.getFetchErrors());
    assertEquals(7, s.getSkippedNonHtml());
    assertEquals("because", s.getIdleReason());
    assertEquals(List.of("https://x/"), s.getFetchedUrls());
  }
}
