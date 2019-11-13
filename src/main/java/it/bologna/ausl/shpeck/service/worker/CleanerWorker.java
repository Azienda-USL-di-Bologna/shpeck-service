/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.bologna.ausl.shpeck.service.worker;

import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.Outbox;
import it.bologna.ausl.model.entities.shpeck.RawMessage;
import it.bologna.ausl.model.entities.shpeck.UploadQueue;
import it.bologna.ausl.shpeck.service.exceptions.CleanerWorkerException;
import it.bologna.ausl.shpeck.service.exceptions.CleanerWorkerInterruption;
import it.bologna.ausl.shpeck.service.manager.CleanerManager;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.OutboxRepository;
import it.bologna.ausl.shpeck.service.repository.RawMessageRepository;
import it.bologna.ausl.shpeck.service.repository.UploadQueueRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author Salo
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
public class CleanerWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CleanerWorker.class);

    @Autowired
    UploadQueueRepository uploadQueueRepository;

    @Autowired
    CleanerManager cleanerManager;

    private String threadName;

    // E' la data termine dello spazzino: i dati posteriori a questa data non vanno toccati
    private Date endTime;

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    /*DA CANCELLARE: FUNZIONE DI TEST*/
    public ArrayList<Integer> getTestOutboxIds() {
        ArrayList<Integer> arrayList = new ArrayList<Integer>();
        arrayList.add(10494);
        arrayList.add(10504);
        return arrayList;
    }

    /**
     * Cicla gli UploadQueue già uploadati e si occupa della cancellazione loro
     * e degli oggetti a loro attaccati (RawMessages) ormai inutili e, se i
     * Messages attaccati a questi ultimi sono in uscita, cancella anche gli
     * Outbox, nell'ottica di liberare spazio. Si interrompe quando arriva a
     * toccare dati relativi a un Message più recente del numero di giorni
     * impostati sul file di configurazione.
     */
    public void spazzinoUploadQueue() throws Throwable {
        try {
            // caricare upload queue
            log.info("Retrieving uploaded rows from upload_queue");
            List<Integer> messagesToDelete = new ArrayList<>();
            messagesToDelete = uploadQueueRepository.getIdToDelete();
            log.info("Devo ciclare righe " + messagesToDelete.size());
            cleanerManager.setEndTime(endTime);
            for (Integer id : messagesToDelete) {
                log.info("UploadQueue.ID = " + id.toString() + " ...");
                try {
                    cleanerManager.cleanUploadQueue(id);
                } catch (CleanerWorkerInterruption i) {
                    //i.printStackTrace();
                    log.info("Interruzione del CleanerWorker: " + i.getMessage());
                    log.info("Interrompo spazzinoUploadQueue(): sono arrivato ai messaggi più nuovi");
                    log.info("VA TUTTO BENE, il resto l'ho salvato.");
                    break;
                } catch (CleanerWorkerException e) {
                    // entrando qua dentro dovrei rollbackare le cancellazioni avvenute usando questo uploadqueue
                    log.error("Catchato CleanerWorkerException in spazzinoUploadQueue ", e);
                } catch (Throwable t) {
                    log.error("QUALCOSA E' ANDATO MALE! Sono arrivato all'upload queue " + id.toString());
                    //log.error(t.toString());
                    log.error("Errore: ", t);
                }
                if (messagesToDelete.indexOf(id) < messagesToDelete.size() - 1) {
                    log.info("Fatto " + id.toString() + ": passo al prossimo...");
                    log.info("*\t*\t*\t*\t*\t*");
                }
            }
            log.info("Finito spazzinoUploadQueue: Tutto OK");
        } catch (Throwable e) {
            log.error("Catchato ERRORACCIO in spazzinoUploadQueue " + e.toString());
            log.error(e.getMessage());
            throw e;
        }

    }

    public void doWork() {
        log.info("------------------------------------------------------------------------");
        log.info("START > *CLEANER WORKER* time: " + new Date());
        try {

            // spazziono uploadqueue
            spazzinoUploadQueue();

        } catch (Throwable e) {
            log.error("ERRORE NEL DOWORK ", e);
        }
        log.info("------------------------------------------------------------------------");
        log.info("FINE > *CLEANER WORKER* time: " + new Date());
    }

    @Override
    public void run() {
        MDC.put("logFileName", threadName);
        try {
            log.info("Entrato nel run di " + threadName);
            doWork();
        } catch (Throwable e) {
            log.info("ERRORE nel main run di CelanerWorker: " + e.toString());
            log.error(e.getMessage());
            e.printStackTrace();
        }
        MDC.remove("logFileName");
    }

}
