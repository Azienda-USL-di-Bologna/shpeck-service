/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.bologna.ausl.shpeck.service.manager;

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
import java.time.ZoneOffset;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author Salo
 */
@Service
public class CleanerManager {
    
    private static final Logger log = LoggerFactory.getLogger(CleanerManager.class);
    
    @Autowired
    UploadQueueRepository uploadQueueRepository;
    
    @Autowired
    RawMessageRepository rawMessageRepository;
    
    @Autowired
    MessageRepository messageRepository;
    
    @Autowired
    OutboxRepository outboxRepository;

    // E' la data termine dello spazzino: i dati posteriori a questa data non vanno toccati
    private Date endTime;
    
    public Date getEndTime() {
        return endTime;
    }
    
    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    /**
     * Recupera un Outbox dal Message passato come parametro e lo cancella se il
     * campo 'ignore' è settato a true.
     */
    public boolean deleteOutboxRow(Message message) throws Throwable {
        boolean hoCancellato = false;
        log.info("Ho trovato che questo message è in uscita, quindi carico l'outbox");
        Integer idOutbox = message.getIdOutbox();
        if (idOutbox == null) {
            throw new Throwable("Errore, messagio in uscita senza id outbox");
        }
        Outbox outboxRow = new Outbox();
        outboxRow = outboxRepository.findById(idOutbox).get();
        if (outboxRow.getIgnore() == true) {
            log.info("Sto per cancelare la riga di outbox " + outboxRow.getId());
            outboxRepository.delete(outboxRow);
            log.info("Ho cancelato la riga!!");
            hoCancellato = true;
        } else {
            log.info("Non ho ancora inviato il messagio, quindi vado avanti");
        }
        return hoCancellato;
    }

    /**
     * Recupera il RawMessage in base all'UploadQueue passato come parametro e
     * lo elimina, poi, se il Message a cui si riferisce è in uscita, richiede
     * la cancellazione del relativo Outbox. Se il Message è più recente del
     * numero di giorni impostato nel file di configurazione, rilancia un
     * CleanerWorkerInterruption per interrompere il lo spazzino.
     */
    public boolean cleanRawMessage(UploadQueue uq) throws CleanerWorkerException, Exception, Throwable {
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

            // verifico se il messaggio (receiveTime) è posteriore alla data limite dello spazzino (endTime)
            if (m.getReceiveTime().toInstant(ZoneOffset.UTC).isAfter(getEndTime().toInstant())) {
                log.info("\t\t\t SPAZZINO INTERRUPT");
                log.info("Receive Time: " + m.getReceiveTime().toString());
                log.info("End Time: " + getEndTime().toString());
                log.info("Ok: il messaggio e quelli successivi sono troppo nuovi, quindi il mestiere finisce.");
                throw new CleanerWorkerInterruption("SPAZZINO INTERRUPT: Sono arrivato al Message " + m.getId()
                        + " che è arrivato " + m.getReceiveTime().toString() + ": l'UploadQueue " + uq.getId().toString()
                        + " e il RawMessage " + rm.getId().toString() + " non sono stati cancellati.");
            }

            // se il messaggio è in uscita (OUT) allora devo cancellare l'outbox
            if (m.getInOut() == Message.InOut.OUT) {
                tuttoOK = deleteOutboxRow(m);
            }
            
            if (!tuttoOK) {
                log.info("C'è stato un problema con la cancellazione dell'outbox " + m.getIdOutbox());
                throw new Throwable("Errore nel cancellamento dell'outbox con id = " + m.getIdOutbox());
            } else if (tuttoOK && uq.getUuid().equals(m.getUuidRepository()) && uq.getPath().equals(m.getPathRepository()) && uq.getName().equals(m.getName())) {
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
        } catch (CleanerWorkerInterruption e) {
            log.error("[cleanRawMessage()] Catchato CleanerWorkerInterruption: si fa rollback");
            throw e;
        } catch (Throwable e) {
            e.printStackTrace();
            log.error("Catchato errore in cleanRawMessage " + e.toString());
            throw e;
        }
        return tuttoOK;
    }

    /**
     * Si occupa di recuperare uno specifico UploadQueue dall'id passato come
     * parametro ed eliminarlo insieme ai dati ingombranti (RawMessage ed
     * eventuale Outbox riferito al suo Message). In caso di eccezzione
     * rollbacka le cancellazioni avvenute sotto la sua transazione.
     */
    @Transactional(rollbackFor = Throwable.class, propagation = Propagation.REQUIRED)
    public void cleanUploadQueue(Integer id) throws CleanerWorkerException, Exception {
        // carico l'uploadQUeue
        log.info("Recupero upQ con id " + id.toString());
        UploadQueue uq = uploadQueueRepository.findById(id).get();
        
        if (uq != null && uq.getUploaded() == true && uq.getIdRawMessage() != null && uq.getIdRawMessage().getId() != null) {
            log.info("Pulisco il raw message di upQ " + id.toString());
            try {
                if (cleanRawMessage(uq) == true) {
                    log.info("Se sono arrivato qui, ho cancellato anche UploadQueue " + uq.getId());
                } else {
                    log.info("Non posso cancellare la riga di upload queue " + uq.getId());
                    throw new CleanerWorkerException("Non posso cancellare la riga di upload queue " + uq.getId());
                }
            } catch (CleanerWorkerInterruption i) {
                log.info("[cleanUploadQueue()] Interrompo cleanUploadQueue: UploadQueue.id " + id.toString());
                throw i;
            } catch (Throwable e) {
                log.info("Errore ignoto nella cancellazione dell upload_queue con id " + uq.getId());
                e.printStackTrace();
                throw new Exception("Errore ignoto nella cancellazione dell upload_queue con id " + uq.getId() + "\n" + e.toString(), e);
            }
        } else {
            throw new CleanerWorkerException("Non ho il raw message di upload_queue " + uq.getId());
        }
    }
}
