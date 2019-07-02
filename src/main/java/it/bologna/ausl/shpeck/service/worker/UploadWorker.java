package it.bologna.ausl.shpeck.service.worker;

import it.bologna.ausl.model.entities.baborg.Azienda;
import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.RawMessage;
import it.bologna.ausl.model.entities.shpeck.UploadQueue;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.manager.UploadManager;
import it.bologna.ausl.shpeck.service.repository.AziendaRepository;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import it.bologna.ausl.shpeck.service.repository.RawMessageRepository;
import it.bologna.ausl.shpeck.service.repository.UploadQueueRepository;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Semaphore;
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
public class UploadWorker implements Runnable {
    
    private static final Logger log = LoggerFactory.getLogger(UploadWorker.class);
    
    private String threadName;
    
    @Autowired
    UploadQueueRepository uploadQueueRepository;
    
    @Autowired
    RawMessageRepository rawMessageRepository;

    @Autowired
    MessageRepository messageRepository;

    @Autowired
    PecRepository pecRepository;

    @Autowired
    AziendaRepository aziendaRepository;

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
            doWork();
            while (true) {
                try {
                    // aspetta dal semaforo di avere elementi disponibili sulla tabella upload_queue
                    log.info("attesa di acquisizione del semaforo per gestire nuovi messaggi...");
                    messageSemaphore.acquire();
                    log.info("semaforo preso");
                    messageSemaphore.drainPermits();
                    doWork();
                    
                } catch (ShpeckServiceException | InterruptedException | UnknownHostException ex) {
                    log.warn("InterruptedException: continue. " + ex);
                    //continue;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        MDC.remove("logFileName");
    }
    
    public void doWork() throws ShpeckServiceException, UnknownHostException {
        log.info("------------------------------------------------------------------------");
        log.info("START -> doWork()," + " time: " + new Date());
        ArrayList<UploadQueue> messagesToUpload;
        
        do {
            // prendi i messaggi da caricare presenti in upload_queue
            //messagesToUpload = uploadQueueRepository.getFromUploadQueue(Boolean.FALSE);
            messagesToUpload = uploadQueueRepository.findByUploaded(Boolean.FALSE);
            
            for (UploadQueue uq : messagesToUpload) {
                UploadQueue u = uploadQueueRepository.findById(uq.getId()).get();
                RawMessage rm = rawMessageRepository.findById(u.getIdRawMessage().getId()).get();
                Message m = messageRepository.findById(rm.getIdMessage().getId()).get();
                Pec p = pecRepository.findById(m.getIdPec().getId()).get();
                Azienda a = aziendaRepository.findById(p.getIdAziendaRepository().getId()).get();
                p.setIdAziendaRepository(a);
                m.setIdPec(p);
                rm.setIdMessage(m);
                u.setIdRawMessage(rm);
                uploadManager.manage(u);
            }

//            for (Integer messageToStore : messagesToUpload) {
//                UploadQueue u = uploadQueueRepository.findById(messageToStore).get();
//                uploadManager.manage(u);
//            }
        } while (!messagesToUpload.isEmpty());
        log.info("STOP -> doWork()," + " time: " + new Date());
        log.info("------------------------------------------------------------------------");
    }
}
