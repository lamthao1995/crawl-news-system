package com.crawlnews.worker.activities;

public final class DemoActivitiesImpl implements DemoActivities {

  @Override
  public String ping(String input) {
    return "ok:" + (input == null ? "" : input);
  }
}
