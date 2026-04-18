package com.crawlnews.crawl.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

final class InvestingCrawlInputTest {

  @Test
  void defaults_matchDocumentedValues() {
    InvestingCrawlInput in = new InvestingCrawlInput();
    assertEquals("https://www.investing.com/", in.getSeedUrl());
    assertEquals("investing.com", in.getAllowedHostSuffix());
    assertEquals(2, in.getMaxDepth());
    assertEquals(30, in.getMaxPages());
    assertEquals(18, in.getMaxOutboundLinksPerPage());
    assertEquals(14, in.getMaxPathSegments());
    assertEquals(45, in.getCircuitSleepSeconds());
    assertEquals(24, in.getCircuitMaxWaitsPerUrl());
  }

  @Test
  void setters_roundTrip() {
    InvestingCrawlInput in = new InvestingCrawlInput();
    in.setSeedUrl("https://example.com/");
    in.setAllowedHostSuffix("example.com");
    in.setMaxDepth(3);
    in.setMaxPages(100);
    in.setMaxOutboundLinksPerPage(50);
    in.setMaxPathSegments(20);
    in.setCircuitSleepSeconds(10);
    in.setCircuitMaxWaitsPerUrl(5);

    assertEquals("https://example.com/", in.getSeedUrl());
    assertEquals("example.com", in.getAllowedHostSuffix());
    assertEquals(3, in.getMaxDepth());
    assertEquals(100, in.getMaxPages());
    assertEquals(50, in.getMaxOutboundLinksPerPage());
    assertEquals(20, in.getMaxPathSegments());
    assertEquals(10, in.getCircuitSleepSeconds());
    assertEquals(5, in.getCircuitMaxWaitsPerUrl());
  }

  @Test
  void jackson_ignoresUnknownFields() throws Exception {
    // Regression guard: workflow task must not fail when the UI/CLI sends extra fields.
    String json =
        "{\"seedUrl\":\"https://x.y/\",\"allowedHostSuffix\":\"x.y\","
            + "\"maxDepth\":3,\"maxPages\":10,\"mystery\":\"ignore me\","
            + "\"nested\":{\"a\":1}}";
    InvestingCrawlInput in = new ObjectMapper().readValue(json, InvestingCrawlInput.class);
    assertEquals("https://x.y/", in.getSeedUrl());
    assertEquals("x.y", in.getAllowedHostSuffix());
    assertEquals(3, in.getMaxDepth());
    assertEquals(10, in.getMaxPages());
  }
}
