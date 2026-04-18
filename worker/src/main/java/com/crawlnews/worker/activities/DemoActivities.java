package com.crawlnews.worker.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface DemoActivities {

  @ActivityMethod
  String ping(String input);
}
