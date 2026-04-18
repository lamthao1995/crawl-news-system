package com.crawlnews.worker;

import com.crawlnews.worker.activities.CrawlActivitiesImpl;
import com.crawlnews.worker.activities.DemoActivitiesImpl;
import com.crawlnews.worker.workflows.InvestingSpiralCrawlWorkflowImpl;
import com.crawlnews.worker.workflows.NewsDemoWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;

public final class WorkerMain {

  static {
    System.setProperty("java.net.preferIPv4Stack", "true");
    System.setProperty("java.net.preferIPv6Addresses", "false");
  }

  public static void main(String[] args) throws InterruptedException {
    String target = env("TEMPORAL_TARGET", "127.0.0.1:7233");
    String namespace = env("TEMPORAL_NAMESPACE", "default");
    String taskQueue = env("TEMPORAL_TASK_QUEUE", "news-task-queue");
    int maxActivities = envInt("WORKER_MAX_ACTIVITIES", 16);
    int maxWorkflowTasks = envInt("WORKER_MAX_WORKFLOW_TASKS", 8);
    int maxLocalActivities = envInt("WORKER_MAX_LOCAL_ACTIVITIES", 16);

    for (int attempt = 1; attempt <= 90; attempt++) {
      WorkflowServiceStubs service = null;
      WorkerFactory factory = null;
      try {
        WorkflowServiceStubsOptions stubOptions =
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(target)
                // Dev / Compose frontend is plaintext gRPC; without this some environments get
                // StatusRuntimeException UNKNOWN on GetSystemInfo when connecting by IP.
                .setChannelInitializer(
                    cb -> {
                      if (cb instanceof NettyChannelBuilder netty) {
                        netty.usePlaintext();
                      }
                    })
                .build();
        service = WorkflowServiceStubs.newServiceStubs(stubOptions);
        WorkflowClient client =
            WorkflowClient.newInstance(
                service,
                WorkflowClientOptions.newBuilder().setNamespace(namespace).build());
        factory = WorkerFactory.newInstance(client);

        WorkerOptions workerOptions =
            WorkerOptions.newBuilder()
                .setMaxConcurrentActivityExecutionSize(maxActivities)
                .setMaxConcurrentWorkflowTaskExecutionSize(maxWorkflowTasks)
                .setMaxConcurrentLocalActivityExecutionSize(maxLocalActivities)
                .build();
        Worker worker = factory.newWorker(taskQueue, workerOptions);
        worker.registerWorkflowImplementationTypes(
            NewsDemoWorkflowImpl.class, InvestingSpiralCrawlWorkflowImpl.class);
        CrawlActivitiesImpl crawlActivities = new CrawlActivitiesImpl();
        worker.registerActivitiesImplementations(new DemoActivitiesImpl(), crawlActivities);

        System.err.printf(
            "[crawl-worker] Starting WorkerFactory"
                + " (target=%s namespace=%s taskQueue=%s"
                + " maxActivities=%d maxWorkflowTasks=%d maxLocalActivities=%d)%n",
            target,
            namespace,
            taskQueue,
            maxActivities,
            maxWorkflowTasks,
            maxLocalActivities);
        factory.start();
        System.err.printf(
            "[crawl-worker] Polling Temporal target=%s namespace=%s taskQueue=%s%n",
            target, namespace, taskQueue);
        final WorkerFactory runningFactory = factory;
        final WorkflowServiceStubs runningService = service;
        final CrawlActivitiesImpl runningActivities = crawlActivities;
        Runtime.getRuntime()
            .addShutdownHook(
                new Thread(
                    () -> {
                      runningFactory.shutdown();
                      runningService.shutdown();
                      runningActivities.close();
                    }));

        try {
          Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        factory.shutdown();
        service.shutdown();
        crawlActivities.close();
        return;
      } catch (Throwable t) {
        System.err.printf("Waiting for Temporal (%d/90): %s%n", attempt, t.getMessage());
        shutdownQuietly(factory, service);
        Thread.sleep(2000L);
      }
    }
    System.err.println("Could not start worker: Temporal was not reachable in time (90 attempts).");
    System.exit(1);
  }

  private static String env(String key, String defaultValue) {
    String v = System.getenv(key);
    return v == null || v.isBlank() ? defaultValue : v;
  }

  private static int envInt(String key, int defaultValue) {
    String v = System.getenv(key);
    if (v == null || v.isBlank()) {
      return defaultValue;
    }
    try {
      int parsed = Integer.parseInt(v.trim());
      if (parsed <= 0) {
        System.err.printf(
            "[crawl-worker] %s=%s is not positive, using default %d%n", key, v, defaultValue);
        return defaultValue;
      }
      return parsed;
    } catch (NumberFormatException e) {
      System.err.printf(
          "[crawl-worker] %s=%s not a valid int, using default %d%n", key, v, defaultValue);
      return defaultValue;
    }
  }

  private static void shutdownQuietly(WorkerFactory factory, WorkflowServiceStubs service) {
    if (factory != null) {
      factory.shutdown();
    }
    if (service != null) {
      service.shutdown();
    }
  }
}
