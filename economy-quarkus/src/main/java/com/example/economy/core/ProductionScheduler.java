package com.example.economy.core;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

@ApplicationScoped
public class ProductionScheduler implements Runnable {
    private static final Logger LOG = Logger.getLogger(ProductionScheduler.class.getName());

    private final Repositories repo;
    private final RedisBus redis;
    private final NatsBus nats;
    private volatile boolean running = true;

    public ProductionScheduler(Repositories repo, RedisBus redis, NatsBus nats) {
        this.repo = repo;
        this.redis = redis;
        this.nats = nats;
    }

    @PostConstruct
    public void init() {
        LOG.info("=== SCHEDULER INIT DEBUG ===");
        LOG.info("Repo: " + (repo != null ? "OK" : "NULL"));
        LOG.info("Redis: " + (redis != null ? "OK" : "NULL"));
        LOG.info("Nats: " + (nats != null ? "OK" : "NULL"));
        start();
    }

    public void start() {
        Thread t = new Thread(this, "prod-scheduler");
        t.setDaemon(true);
        t.start();
        LOG.info("Production scheduler started");
    }

    @Override
    public void run() {
        LOG.info("Production scheduler running...");
        while (running) {
            try {
                long now = System.currentTimeMillis();
                Map.Entry<String, Long> e = redis.tryPopDueAtomic(now); // атомарный вариант

                if (e == null) {
                    Thread.sleep(150);
                    continue;
                }

                String jobId = e.getKey();
                long endAtMillis = e.getValue();

                LOG.info("Found expired job: " + jobId + " -> " + new java.util.Date(endAtMillis));

                boolean updated = repo.markDoneAndReward(UUID.fromString(jobId));
                if (updated) {
                    LOG.info("Job " + jobId + " completed successfully");
                    try {
                        nats.publish("econ.production.done", "{\"jobId\":\"" + jobId + "\"}");
                    } catch (Exception ex) {
                        LOG.warning("Failed to publish NATS notification: " + ex.getMessage());
                    }
                } else {
                    LOG.warning("Failed to complete job: " + jobId);
                }
            } catch (Exception ex) {
                LOG.severe("Error in production scheduler: " + ex.getMessage());
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
        }
    }

    public void stop() { running = false; }
}
