package it.bologna.ausl.shpeck.service.worker;

//import com.zaxxer.hikari.HikariConfig;
//import com.zaxxer.hikari.HikariDataSource;
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
import java.util.List;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 *
 * @author spritz
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class UploadWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(UploadWorker.class);

    private String threadName;

    private int identifier;

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

    @Value("{mail.upload.number-of-threads}")
    Integer numberOfThreads;

    public UploadWorker() {
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public int getIdentifier() {
        return identifier;
    }

    public void setIdentifier(int identifier) {
        this.identifier = identifier;
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
        } catch (Throwable e) {
            log.error("ERRORE", e);
        }
        MDC.remove("logFileName");
    }

    public void doWork() throws ShpeckServiceException, UnknownHostException {
        log.info("------------------------------------------------------------------------");
        log.info("START -> doWork()," + " time: " + new Date());

        List<Integer> messagesToUpload = new ArrayList<>();
        log.info("ottieni i messaggi da uploadrare...");
        messagesToUpload = uploadQueueRepository.getIdToUpload(numberOfThreads, identifier);
        log.info("query eseguita");

        log.info("messaggi da uploadare: " + messagesToUpload.size());

        for (Integer uq : messagesToUpload) {
            UploadQueue u = uploadQueueRepository.findById(uq).get();
            log.info("oggetto UploadQueue creato");
            RawMessage rm = rawMessageRepository.findById(u.getIdRawMessage().getId()).get();
            log.info("oggetto RawMessage creato");
            Message m = messageRepository.findById(rm.getIdMessage().getId()).get();
            log.info("oggetto Message creato");
            Pec p = pecRepository.findById(m.getIdPec().getId()).get();
            log.info("oggetto Pec creato");
            if (p.getIdAziendaRepository() == null) {
                log.warn("la pec con indirizzo " + p.getIndirizzo() + " non ha un id_repository associato");
                continue;
            }
            Azienda a = aziendaRepository.findById(p.getIdAziendaRepository().getId()).get();
            log.info("oggetto Azienda creato");
            p.setIdAziendaRepository(a);
            m.setIdPec(p);
            rm.setIdMessage(m);
            u.setIdRawMessage(rm);
            uploadManager.manage(u);
        }

        log.info("STOP -> doWork()," + " time: " + new Date());
        log.info("------------------------------------------------------------------------");
    }
}
