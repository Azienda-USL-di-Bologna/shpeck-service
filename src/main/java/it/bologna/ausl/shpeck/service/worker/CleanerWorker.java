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

    private String threadName;

    // alla creazione dovrebbe essere settato a due settimane fa
    private Date endTime;

    @Autowired
    UploadQueueRepository uploadQueueRepository;

    @Autowired
    RawMessageRepository rawMessageRepository;

    @Autowired
    MessageRepository messageRepository;

    @Autowired
    OutboxRepository outboxRepository;

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

    private boolean deleteOutboxLine(Message message) throws Throwable {
        boolean hoCancellato = false;
        log.info("Ho trovato che questo message è in uscita, quindi carico l'outbox");
        Integer idOutbox = message.getIdOutbox();
        if (idOutbox == null) {
            throw new Throwable("Errore, messagio in uscita senza id outbox");
        }
        Outbox lineInOutbox = new Outbox();
        lineInOutbox = outboxRepository.findById(idOutbox).get();
        if (lineInOutbox.getIgnore() == true) {
            log.info("Sto per cancelare la riga di outbox " + lineInOutbox.getId());
            outboxRepository.delete(lineInOutbox);
            log.info("Ho cancelato la riga!!");
            hoCancellato = true;
        } else {
            log.info("Non ho ancora inviato il messagio, quindi vado avanti");
        }
        return hoCancellato;
    }

    public boolean cleanRawMessage(UploadQueue uq) throws CleanerWorkerException, Exception {
        boolean tuttoOK = true;
        try {
            // recuperare il raw message
            log.info("Recupero il raw_message");
            RawMessage rm = new RawMessage();
            rm = rawMessageRepository.findById(uq.getIdRawMessage().getId()).get();

            if (!(rm != null)) {
                throw new CleanerWorkerException("Non ho il raw message di upload_queue " + uq.getId());
            }

            // recuperare il message
            log.info("Recupero il message");
            Message m = new Message();
            m = messageRepository.findById(rm.getIdMessage().getId()).get();
            log.info("Trovato " + m.toString());

            // il receive time è posteriore alla mia data di fine (2 settimane fa)
            if (m.getReceiveTime().toInstant(ZoneOffset.UTC).isAfter(getEndTime().toInstant())) {
                log.info("Ok ho messaggi troppo nuovi, quindi il mestiere finisce.");
                log.info("End Time: " + getEndTime().toString());
                log.info("Receive Time: " + m.getReceiveTime().toString());
                throw new CleanerWorkerException("Sono arrivato al messaggio " + m.getId() + " che è arrivato " + m.getReceiveTime().toString());
            }
            // se il m.getTime > 2 settimane fa (cioè end time)
            //            //      rilancia specifica eccezione per terminare il mestiere
            {
                if (m.getInOut() == Message.InOut.OUT) {
                    tuttoOK = deleteOutboxLine(m);
                }
            }
            if (tuttoOK && uq.getUuid().equals(m.getUuidRepository()) && uq.getPath().equals(m.getPathRepository()) && uq.getName().equals(m.getName())) {
                log.info("Sto per cancelare la riga di raw message " + rm.getId());
                rawMessageRepository.delete(rm);
                log.info("Ho cancelato la riga!!");
            } else {
                log.info("Non posso cancellare il raw message " + rm.getId());
                log.info(" uq.getUuid().equals(m.getUuidRepository()) " + uq.getUuid().equals(m.getUuidRepository()));
                log.info(" uq.getPath().equals(m.getPathRepository()) " + uq.getPath().equals(m.getPathRepository()));
                log.info(" uq.getName().equals(m.getName()) " + uq.getName().equals(m.getName()));
                tuttoOK = false;
            }
        } catch (Throwable e) {
            e.printStackTrace();
            log.error("Catchato errore in cleanRawMessage " + e.toString());
        }
        return tuttoOK;
    }

    @Transactional(rollbackFor = Throwable.class, propagation = Propagation.REQUIRED)
    public void cleanUploadQueue(Integer id) throws CleanerWorkerException, Exception {
        // carico l'uploadQUeue
        log.info("Recupero upQ con id " + id.toString());
        UploadQueue uq = new UploadQueue();
        uq = uploadQueueRepository.findById(id).get();

        if (uq.getIdRawMessage() != null && uq.getIdRawMessage().getId() != null) {
            log.info("Pulisco il raw message di upQ " + id.toString());
            try {
                if (cleanRawMessage(uq) == true) {
                    // cancela uq
                    log.info("Sto per cancellare la riga di upload queue");
                    uploadQueueRepository.delete(uq);
                    log.info("Ho cancellato la riga di upload queue");
                } else {
                    log.info("Non posso cancellare la riga di upload queue " + uq.getId());
                    throw new CleanerWorkerException("Non posso cancellare la riga di upload queue " + uq.getId());
                }
            } catch (CleanerWorkerInterruption i) {
                log.info("Interrompo cleanUploadQueue: UploadQueue.id " + id.toString());
                throw i;
            } catch (Throwable e) {
                log.info("Errore ignoto nella cancellazione dell upload_queue con id " + uq.getId());
                log.info(e.toString());
                e.printStackTrace();
                throw new Exception("Errore ignoto nella cancellazione dell upload_queue con id " + uq.getId() + "\n" + e.toString(), e);
            }
        } else {
            throw new CleanerWorkerException("Non ho il raw message di upload_queue " + uq.getId());
        }
    }

    public void spazzinoUploadQueue() throws Throwable {
        try {
            // caricare upload queue
            log.info("Retrieving uploaded rows from upload_queue");
            List<Integer> messagesToDelete = new ArrayList<>();
            messagesToDelete = uploadQueueRepository.getIdToDelete();
            log.info("Devo ciclare righe " + messagesToDelete.size());
            for (Integer id : messagesToDelete) {
                log.info("UploadQueue.ID = " + id.toString() + " ...");
                try {
                    cleanUploadQueue(id);
                } catch (CleanerWorkerInterruption i) {
                    log.info("Interrompo spazzinoUploadQueue(): sono arrivato ai messaggi più nuovi:");
                    i.printStackTrace();
                    log.info(i.getMessage());
                    log.info("VA TUTTO BENE, il resto l'ho salvato. termino lo spazzino");
                    break;
                } catch (CleanerWorkerException e) {
                    // entrando qua dentro dovrei rollbackare le cancellazioni avvenute usando questo uploadqueue
                    log.error("Catchato CleanerWorkerException in spazzinoUploadQueue " + e.toString());
                } catch (Throwable t) {
                    log.error("QUALCOSA E' ANDATO MALE! Sono arrivato all'upload queue " + id.toString());
                    log.error(t.toString());
                    log.error(t.getMessage());
                    t.printStackTrace();
                }

            }
            log.info("Finito spazzinoUploadQueue: Tutto OK");
        } catch (Throwable e) {
            e.printStackTrace();
            log.error("Catchato ERRORACCIO in spazzinoUploadQueue " + e.toString());
            throw e;
        }

    }

    public void doRiconciliazione() {
        // caricare pec attive
        // creare IMAPChecker di riconciliazione ciclando sulle pec caricate
        // accodarli in un pool di esecuzione che parte mo
    }

    public void doWork() {
        log.info("------------------------------------------------------------------------");
        log.info("START > *CLEANER WORKER* time: " + new Date());
        try {
            // spazziono uploadqueue
            spazzinoUploadQueue();
            // accodare mestieri di riconciliazione
            doRiconciliazione();
        } catch (Throwable e) {
            log.error("ERRORE NEL DOWORK " + e.getMessage());
            e.printStackTrace();
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
            e.printStackTrace();
            log.info(e.toString());
        }
        MDC.remove("logFileName");
    }

}
