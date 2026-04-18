package com.crawlnews.worker.workflows;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface NewsDemoWorkflow {

  @WorkflowMethod
  String runDemo(String name);
}
