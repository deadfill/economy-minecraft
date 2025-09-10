package com.example.economy.core;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

@ApplicationScoped
public class ProductionPoller {
    private static final Logger LOG = Logger.getLogger(ProductionPoller.class.getName());

    @Inject Repositories repo;
    @Inject RedisBus redis;
    @Inject NatsBus nats;

    @PostConstruct
    void init() {
        LOG.info("ProductionPoller initialized");
    }

    @Scheduled(every = "0.15s", concurrentExecution = ConcurrentExecution.SKIP)
    void tick() {
        long now = System.currentTimeMillis();

        for (int i = 0; i < 50; i++) {
            Map.Entry<String, Long> e = redis.tryPopDueAtomic(now);
            if (e == null) break;

            String jobId = e.getKey();
            long endAtMillis = e.getValue();

            LOG.info("Processing expired job: " + jobId + " (ended " + new java.util.Date(endAtMillis) + ")");

            try {
                UUID jobUuid = UUID.fromString(jobId);
                boolean updated = repo.markDoneAndReward(jobUuid);
                if (updated) {
                    try {
                        UUID owner = repo.findOwnerByJobId(jobUuid);
                        String payload = "{\"type\":\"done\",\"jobId\":\""+jobId+"\",\"owner\":\""+owner+"\",\"endMs\":"+endAtMillis+"}";
                        nats.publish("econ.production.done", payload);
                    } catch (Exception ex) {
                        LOG.warning("NATS publish failed: " + ex.getMessage());
                    }
                } else {
                    LOG.warning("markDoneAndReward returned false for job " + jobId);
                }
            } catch (Exception ex) {
                LOG.severe("Error processing job " + jobId + ": " + ex.getMessage());
            }
        }
    }
}
