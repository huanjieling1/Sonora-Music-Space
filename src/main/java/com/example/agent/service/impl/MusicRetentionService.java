package com.example.agent.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MusicRetentionService {
    private static final Logger log = LoggerFactory.getLogger(MusicRetentionService.class);
    private final MusicPersonalizationRepository repository;

    public MusicRetentionService(MusicPersonalizationRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "${music.personalization.retention-cron:0 30 4 * * *}")
    public void purgeExpiredOperationalData() {
        var result = repository.purgeExpiredOperationalData();
        if (result.events() > 0 || result.exposures() > 0) {
            log.info("Purged {} expired music events and {} expired exposures",
                    result.events(), result.exposures());
        }
    }
}
