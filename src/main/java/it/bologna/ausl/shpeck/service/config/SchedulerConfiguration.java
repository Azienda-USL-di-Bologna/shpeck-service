package it.bologna.ausl.shpeck.service.config;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author spritz
 */
@Configuration
public class SchedulerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SchedulerConfiguration.class);

    @Value("${shpeck.threads.pool-size}")
    String poolSize;

    @Bean
    public ScheduledThreadPoolExecutor scheduledThreadPoolExecutor() {
        log.info("poolSize: " + poolSize);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(Integer.parseInt(poolSize));
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return executor;
    }

    @Bean
    public Semaphore messageSemaphore() {
        return new Semaphore(0);
    }

}
