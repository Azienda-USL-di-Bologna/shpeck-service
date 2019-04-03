package it.bologna.ausl.shpeck.worker;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author spritz
 */
@Component
public class ShutdownThread extends Thread{
    
    private static final Logger log = LoggerFactory.getLogger(ShutdownThread.class);
    
    @Autowired
    ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
    
    @Override
    public void run() {
        log.info("Shutdown initiated");
        boolean terminated = false;
        scheduledThreadPoolExecutor.shutdown();
        //DataSource dataSource = DbConnectionFactory.getDataSource();
        //if (dataSource != null) {}
        while (true) {
            try {
                terminated = scheduledThreadPoolExecutor.awaitTermination(5, TimeUnit.MINUTES);
                break;
            } catch (InterruptedException ex) {
                log.warn("Shutdown thread interrupted while waiting for threads termination");
            }
        }
        if (terminated == true) {
            log.info("All worker threads are gone");
        } else {
            log.error("timeout waiting for thread exit!");
        }

    }
    
}
