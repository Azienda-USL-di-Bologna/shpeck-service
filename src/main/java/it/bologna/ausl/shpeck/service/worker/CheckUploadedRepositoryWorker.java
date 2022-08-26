package it.bologna.ausl.shpeck.service.worker;

import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author spritz
 */
@Component
public class CheckUploadedRepositoryWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CheckUploadedRepositoryWorker.class);

    @Autowired
    MessageRepository messageRepository;

    private String threadName;

    public CheckUploadedRepositoryWorker() {
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    @Override
    public void run() {
        MDC.put("logFileName", threadName);
        try {
            doWork();
        } catch (Throwable e) {
            log.error("errore", e);
        }
        MDC.remove("logFileName");
    }

    public void doWork() {
        log.info("------------------------------------------------------------------------");
        log.info("START -> doWork()," + " time: " + new Date());

        Integer count = messageRepository.getNumberOfMessageUploadedWithNoRepositoryInMessage();

        if (count > 0) {
            log.info("count: " + count);
            messageRepository.fixNumberOfMessageUploadedWithNoRepositoryInMessage();
            log.info("allineamento eseguito");
        }

        log.info("STOP -> doWork()," + " time: " + new Date());
        log.info("------------------------------------------------------------------------");
    }
}
