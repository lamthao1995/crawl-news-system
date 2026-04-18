package com.crawlnews.worker.activities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class DemoActivitiesImplTest {

  private final DemoActivities activities = new DemoActivitiesImpl();

  @Test
  void ping_echoesInputWithPrefix() {
    assertEquals("ok:hello", activities.ping("hello"));
  }

  @Test
  void ping_handlesNullAsEmpty() {
    assertEquals("ok:", activities.ping(null));
  }
}
