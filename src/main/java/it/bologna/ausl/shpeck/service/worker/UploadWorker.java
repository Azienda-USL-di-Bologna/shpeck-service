package it.bologna.ausl.shpeck.service.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.bologna.ausl.model.entities.baborg.AziendaParametriJson;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.UploadQueue;
import it.bologna.ausl.mongowrapper.exceptions.MongoWrapperException;
import it.bologna.ausl.shpeck.service.Test;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.manager.UploadManager;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.UploadQueueRepository;
import it.bologna.ausl.shpeck.service.storage.MongoStorage;
import it.bologna.ausl.shpeck.service.storage.StorageContext;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 *
 * @author spritz
 */
@Component
public class UploadWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(UploadWorker.class);

    private String threadName;

    @Autowired
    UploadQueueRepository uploadQueueRepository;

    @Autowired
    Semaphore messageSemaphore;

    @Autowired
    UploadManager uploadManager;

//    @Autowired
//    Test test;
    public UploadWorker() {
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
            /**
             * esegue un primo doWork() perchè se il sistema riparte, si
             * potrebbe avere dei record in upload_queue ancora da uploadare
             */
            Test test = new Test();
            test.prova();
//            doWork();
//            while (true) {
//                try {
//                    // aspetta dal semaforo di avere elementi disponibili sulla tabella upload_queue
//                    log.info("attesa di acquisizione del semaforo per gestire nuovi messaggi...");
//                    messageSemaphore.acquire();
//                    log.info("semaforo preso");
//                    messageSemaphore.drainPermits();
//                    doWork();
//
//                } catch (ShpeckServiceException | InterruptedException | UnknownHostException ex) {
//                    log.warn("InterruptedException: continue. " + ex);
//                    //continue;
//                }
//            }
        } catch (Throwable e) {
        }
        MDC.remove("logFileName");
    }

    public void doWork() throws ShpeckServiceException, UnknownHostException {
        log.info("START -> doWork()," + " time: " + new Date());
        ArrayList<UploadQueue> messagesToUpload;

        do {
            // prendi i messaggi da caricare presenti in upload_queue
            messagesToUpload = uploadQueueRepository.getFromUploadQueue(Boolean.FALSE);

            for (UploadQueue messageToStore : messagesToUpload) {
                uploadManager.manage(messageToStore);
            }
        } while (!messagesToUpload.isEmpty());
        log.info("STOP -> doWork()," + " time: " + new Date());
    }
}
