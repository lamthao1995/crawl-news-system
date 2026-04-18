package com.crawlnews.worker.workflows;

import com.crawlnews.worker.activities.DemoActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public final class NewsDemoWorkflowImpl implements NewsDemoWorkflow {

  private final DemoActivities activities =
      Workflow.newActivityStub(
          DemoActivities.class,
          ActivityOptions.newBuilder()
              .setStartToCloseTimeout(Duration.ofSeconds(10))
              .build());

  @Override
  public String runDemo(String name) {
    return activities.ping(name);
  }
}
