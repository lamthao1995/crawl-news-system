package com.crawlnews.crawl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class FrontierItemTest {

  @Test
  void getters_roundTrip() {
    FrontierItem item = new FrontierItem("https://example.com/a", 3);
    assertEquals("https://example.com/a", item.getCanonicalUrl());
    assertEquals(3, item.getDepth());
  }
}
