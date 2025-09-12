package com.example.economy.core;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.logging.Logger;

@ApplicationScoped
public class SkillCatalogRefreshScheduler {
    private static final Logger LOG = Logger.getLogger(SkillCatalogRefreshScheduler.class.getName());

    @Inject
    SkillCatalogService skillCatalogService;

    /**
     * Периодическое обновление кэша каталога скиллов.
     * Выполняется каждые 10 минут.
     */
    @Scheduled(cron = "0 */10 * * * ?", concurrentExecution = ConcurrentExecution.SKIP)
    void refreshSkillCatalog() {
        try {
            LOG.info("Refreshing skill catalog cache...");
            skillCatalogService.refresh();
            LOG.info("Skill catalog cache refreshed successfully.");
        } catch (Exception e) {
            LOG.severe("Failed to refresh skill catalog cache: " + e.getMessage());
        }
    }
}