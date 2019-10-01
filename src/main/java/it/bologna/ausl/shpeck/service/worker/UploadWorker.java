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

//    @Value("${spring.datasource.driver-class-name}")
//    String driverClass;
//
//    @Value("${spring.datasource.url}")
//    String url;
//
//    @Value("${spring.datasource.username}")
//    String username;
//
//    @Value("${spring.datasource.password}")
//    String password;
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

//    HikariConfig hikariConfig;
//    HikariDataSource hikariDataSource;
    //Sql2o sql2o;
    @Override
    public void run() {
        MDC.put("logFileName", threadName);
        try {
//            hikariConfig = new HikariConfig();
//            hikariConfig.setDriverClassName(driverClass);
//            hikariConfig.setJdbcUrl(url);
//            hikariConfig.setUsername(username);
//            hikariConfig.setPassword(password);
//            hikariDataSource = new HikariDataSource(hikariConfig);
//            sql2o = new Sql2o(hikariDataSource);
            /**
             * esegue un primo doWork() perchè se il sistema riparte, si
             * potrebbe avere dei record in upload_queue ancora da uploadare
             */
            doWork();
//            while (true) {
//                try {
//                    // aspetta dal semaforo di avere elementi disponibili sulla tabella upload_queue
//                    log.info("attesa di acquisizione del semaforo per gestire nuovi messaggi...");
//                    //messageSemaphore.acquire();
//                    log.info("semaforo preso");
//                    doWork();
//                    //messageSemaphore.drainPermits();
//                    log.info("semaforo rilasciato");
//                    TimeUnit.SECONDS.sleep(5);
//                } catch (ShpeckServiceException | UnknownHostException ex) {
//                    log.warn("InterruptedException: continue. " + ex);
//                    //continue;
//                }
//            }
        } catch (Throwable e) {
            e.printStackTrace();
            log.info(e.toString());
        }
        MDC.remove("logFileName");
    }

    public void doWork() throws ShpeckServiceException, UnknownHostException {
        log.info("------------------------------------------------------------------------");
        log.info("START -> doWork()," + " time: " + new Date());

        List<Integer> messagesToUpload = new ArrayList<>();
        log.info("obtain message to upload...");
//        try (Connection conn = (Connection) sql2o.open()) {
//            log.info("eseguo query");
//            messagesToUpload = conn.createQuery("select id from shpeck.upload_queue where uploaded = false").executeAndFetch(Integer.class);
//        } catch (Throwable e) {
//            e.printStackTrace();
//            log.info(e.getMessage());
//        }
        //do {
        // prendi i messaggi da caricare presenti in upload_queue
        //messagesToUpload = uploadQueueRepository.getFromUploadQueue(Boolean.FALSE);
        //messagesToUpload = uploadQueueRepository.findByUploaded(Boolean.FALSE);
        messagesToUpload = uploadQueueRepository.getIdToUpload();
        log.info("query executed");

        log.info("messages to upload: " + messagesToUpload.size());

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

//            for (Integer messageToStore : messagesToUpload) {
//                UploadQueue u = uploadQueueRepository.findById(messageToStore).get();
//                uploadManager.manage(u);
//            }
        //} while (!messagesToUpload.isEmpty());
        log.info("STOP -> doWork()," + " time: " + new Date());
        log.info("------------------------------------------------------------------------");
    }
}
