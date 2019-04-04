package it.bologna.ausl.shpeck.service.worker;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 *
 * @author spritz
 */

@Component
public class TestThread extends Thread{

    private static final Logger log = LoggerFactory.getLogger(TestThread.class);
      
    @Override
    public void run() {
        log.info(getName() + " start TestWorker");

        try {
                TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException e) {
                e.printStackTrace();
        }

        log.info(getName() + " stop TestWorker");
    }
    
}
