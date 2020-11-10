/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.bologna.ausl.shpeck.service.worker;

import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.Outbox;
import it.bologna.ausl.model.entities.shpeck.UploadQueue;
import it.bologna.ausl.shpeck.service.exceptions.CleanerWorkerException;
import it.bologna.ausl.shpeck.service.exceptions.CleanerWorkerInterruption;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.manager.CleanerManager;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.OutboxRepository;
import it.bologna.ausl.shpeck.service.repository.UploadQueueRepository;
import it.bologna.ausl.shpeck.service.utils.Diagnostica;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

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
    OutboxRepository outboxRepository;

    @Autowired
    CleanerManager cleanerManager;

    @Autowired
    MessageRepository messageRepository;

    @Autowired
    Diagnostica diagnostica;

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
                log.debug("UploadQueue.ID = " + id.toString());
                try {
                    String uuidRepository = uploadQueueRepository.getUuidRepository(id);
                    log.debug("uuidRepository = " + uuidRepository);
                    if (uuidRepository != null && !uuidRepository.equals("")) {
                        log.debug("record da eliminare");
                        cleanerManager.cleanUploadQueue(id);
                    } else {
                        // salvataggio uuid_repository
                        log.debug("uuidRepository vuoto, salvataggio da UploadQueue");
                        Optional<UploadQueue> u = uploadQueueRepository.findById(id);
                        if (u.isPresent()) {
                            log.debug("record presente in UploadQueue");
                            Integer idMessage = u.get().getIdRawMessage().getIdMessage().getId();
                            log.debug("idMessage: " + idMessage.toString());
                            Optional<Message> message = messageRepository.findById(idMessage);
                            if (message.isPresent()) {
                                log.debug("messaggio presente su DB");
                                Message tmp = message.get();
                                log.debug("uuid = " + u.get().getUuid());
                                tmp.setUuidRepository(u.get().getUuid());
                                log.debug("path = " + u.get().getPath());
                                tmp.setPathRepository(u.get().getPath());
                                log.debug("name = " + u.get().getName());
                                tmp.setName(u.get().getName());
                                messageRepository.save(tmp);
                            } else {
                                log.debug("messaggio NON presente su DB");
                                throw new CleanerWorkerException("messaggio con id: " + idMessage + "NON presente su DB");
                            }
                        }
                    }
                } catch (CleanerWorkerInterruption i) {
                    //i.printStackTrace();
                    log.info("Interruzione del CleanerWorker: " + i.getMessage());
                    log.info("Interrompo spazzinoUploadQueue(): sono arrivato ai messaggi più nuovi");
                    log.info("VA TUTTO BENE, il resto l'ho salvato.");
                    break;
                } catch (CleanerWorkerException e) {
                    // entrando qua dentro dovrei rollbackare le cancellazioni avvenute usando questo uploadqueue
                    log.error("Catchato CleanerWorkerException in spazzinoUploadQueue ", e);
                    writeReportDiagnostica(e, id);
                } catch (Throwable t) {
                    log.error("QUALCOSA E' ANDATO MALE! Sono arrivato all'upload queue " + id.toString());
                    //log.error(t.toString());
                    log.error("Errore: ", t);
                    writeReportDiagnostica(t, id);
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
            writeReportDiagnostica(e, null);
            throw e;
        }
    }

    public void spazzinoOutbox() throws Throwable {
        try {
            // carica outbox
            log.info("ottieni outbox con ignore a true");
            List<Integer> outboxMessageToDelete = new ArrayList<>();
            outboxMessageToDelete = outboxRepository.findAllIdOutboxIgnoreTrue();

            for (Integer idOutbox : outboxMessageToDelete) {
                log.info("id Outbox ---> " + idOutbox.toString());
                
                Message message = messageRepository.getMessageByIdOutbox(idOutbox);
                if (message != null && message.getUuidMessage() != null && !message.getUuidMessage().equals("")
                        && message.getUuidRepository() != null && !message.getUuidRepository().equals("")) {
                    log.info("elimino idOutbox: " + idOutbox + " relativo a idMessage: " + message.getId());
                    Outbox outbox = outboxRepository.findById(idOutbox).get();
                    outboxRepository.delete(outbox);
                }
                if (message==null){
                    writeReportDiagnostica(new ShpeckServiceException("Nessun message trovato con id outbox "+idOutbox.toString()), null);
                }
            }
        } catch (Throwable e) {
            log.error("Catchato ERRORACCIO in spazzinoOutbox " + e.toString());
            log.error(e.getMessage());
            writeReportDiagnostica(e, null);
            throw e;
        }
    }

    public void doWork() {
        log.info("------------------------------------------------------------------------");
        log.info("START > *CLEANER WORKER* time: " + new Date());
        try {

            // spazziono uploadqueue
            spazzinoUploadQueue();
            spazzinoOutbox();

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

    private void writeReportDiagnostica(Throwable e, Integer idUploadQueue) {
        // creazione messaggio di errore
        JSONObject json = new JSONObject();
        if (idUploadQueue != null) {
            json.put("id_upload_queue", idUploadQueue.toString());
        }
        json.put("Exception", e.toString());
        json.put("ExceptionMessage", e.getMessage());

        diagnostica.writeInDiagnoticaReport("SHPECK_ERROR_CLEANER_WORKER", json);
    }

}
