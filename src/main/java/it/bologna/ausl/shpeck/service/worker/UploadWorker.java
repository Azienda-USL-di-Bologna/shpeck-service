package it.bologna.ausl.shpeck.service.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.bologna.ausl.model.entities.baborg.AziendaParametriJson;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.UploadQueue;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.UploadQueueRepository;
import it.bologna.ausl.shpeck.service.storage.MongoStorage;
import it.bologna.ausl.shpeck.service.storage.StorageContext;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author spritz
 */
@Component
public class UploadWorker implements Runnable{
    
    private static final Logger log = LoggerFactory.getLogger(UploadWorker.class);
    
    private StorageContext storageContext;
    
    @Value("${mailbox.inbox-forlder}")
    String inboxForlder;
    
    @Autowired
    UploadQueueRepository uploadQueueRepository;
    
    @Autowired
    ObjectMapper objectMapper;
    
    @Autowired
    MessageRepository messageRepository;
    
    @Autowired
    Semaphore messageSemaphore;
    
    
    public UploadWorker() {
    }
    
    @Override
    public void run() {
        try {
            /**
             * esegue un primo doWork() perchè se il sistema riparte, 
             * si potrebbe avere dei record in upload_queue ancora da uploadare
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
                   
                } catch (InterruptedException e) {
                    log.warn("InterruptedException: continue");
                    continue;
                }
            }
        } catch (Exception e) {
        }
    }
    
    @Transactional
    private void doWork() throws ShpeckServiceException, UnknownHostException {
        log.info("inizio doWork() per storage");
     
        ArrayList<UploadQueue> messagesToUpload;
        
        do {
            // prendi i messaggi da caricare presenti in upload_queue
            messagesToUpload = uploadQueueRepository.getFromUploadQueue(Boolean.FALSE, Message.InOut.IN.toString());
                
            for (UploadQueue uploadQueue : messagesToUpload) {
                try {
                    // ottieni parametri di mongo di un specifico ambiente guardando l'azienda associata alla pec
                    AziendaParametriJson aziendaParams = AziendaParametriJson.parse(objectMapper, uploadQueue.getIdRawMessage().getIdMessage().getIdPec().getIdAziendaRepository().getParametri());          
                    AziendaParametriJson.MongoParams mongoParams = aziendaParams.getMongoParams();

                    // inizializzazione del context storage
                    storageContext = new StorageContext(new MongoStorage(mongoParams.getConnectionString(), mongoParams.getRoot()));

                    // esegue lo store del messaggio e ritorna l'oggetto con le proprietà settate (es: uuid, path, ...)
                    UploadQueue objectUploaded = storageContext.store(inboxForlder, uploadQueue);
                    
                    // ottieni in messaggio associato al contenuto appena caricato
                    Optional<Message> message = messageRepository.findById(uploadQueue.getIdRawMessage().getIdMessage().getId());
                    Message messageToUpdate = null;
                    
                    // se messaggio è presente, si settano le proprietà relative al messaggio appena salvato nello storage
                    if(message.isPresent()){
                        messageToUpdate = message.get();
                        messageToUpdate.setUuidRepository(objectUploaded.getUuid());
                        messageToUpdate.setPathRepository(objectUploaded.getPath());
                        messageToUpdate.setName(objectUploaded.getName());
                        // update del mesaggio con i nuovi parametri                    
                        messageRepository.save(messageToUpdate);
                        
                        // set come file già trattato nella tabella upload_queue
                        objectUploaded.setUploaded(Boolean.TRUE);
                        uploadQueueRepository.save(objectUploaded);
                        
                    }
                } catch (Exception e) {
                }
            }
                
// TODO: caso SMTP
//          for (UploadMessage m : messages) {
//                    try {
//                        sm.setFolderPath(rootPath + "/" + db.getMailConfigDescription(m.getConfigId()));
//                        sm.storeMessage("INBOX", m);
//                    } catch (PecGWMessageAlreadyExistsException e) {
//                        log.warn("Message " + m.getMessageId() + " " + m.getName() + " already on cmis");
//                        db.rollback();
//                        db.setAlreadyUploaded(m.getMessageId());
//                        db.commit();
//                        continue;
//                    } catch (PecGWException e) {
//                        db.rollback();
//                        db.close();
//                        throw new PecGWException("Error storing message to cmis", e);
//                    }
//                    db.setUploaded(m);
//                    db.deleteRawMessage(m.getMessageId());
//                    db.commit();
//                }

        } while (!messagesToUpload.isEmpty());
        log.info("fine doWork() per storage");
    }
    

   
    
}
