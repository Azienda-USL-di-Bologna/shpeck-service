package it.bologna.ausl.shpeck.service.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.bologna.ausl.model.entities.baborg.AziendaParametriJson;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.UploadQueue;
import it.bologna.ausl.mongowrapper.exceptions.MongoWrapperException;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.UploadQueueRepository;
import it.bologna.ausl.shpeck.service.storage.MongoStorage;
import it.bologna.ausl.shpeck.service.storage.StorageContext;
import it.bologna.ausl.shpeck.service.worker.UploadWorker;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author spritz
 */
@Service
public class UploadManager {

    private static final Logger log = LoggerFactory.getLogger(UploadWorker.class);

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    StorageContext storageContext;

    @Autowired
    MessageRepository messageRepository;

    @Autowired
    UploadQueueRepository uploadQueueRepository;

    @Value("${mailbox.inbox-folder}")
    String inboxForlder;

    @Autowired
    Semaphore messageSemaphore;

    @Transactional(rollbackFor = Throwable.class, propagation = Propagation.REQUIRES_NEW)
    public void manage(UploadQueue messageToStore) throws ShpeckServiceException {
        try {
            // ottieni parametri di mongo di un specifico ambiente guardando l'azienda associata alla pec
            AziendaParametriJson aziendaParams = AziendaParametriJson.parse(objectMapper, messageToStore.getIdRawMessage().getIdMessage().getIdPec().getIdAziendaRepository().getParametri());
            AziendaParametriJson.MongoParams mongoParams = aziendaParams.getMongoParams();

            // inizializzazione del context storage
            storageContext.setStorageStrategy(new MongoStorage(mongoParams.getConnectionString(), mongoParams.getRoot()));

            // esegue lo store del messaggio e ritorna l'oggetto con le proprietà settate (es: uuid, path, ...)
            UploadQueue objectUploaded = storageContext.store(inboxForlder, messageToStore);

            // ottieni in messaggio associato al contenuto appena caricato
            Optional<Message> message = messageRepository.findById(messageToStore.getIdRawMessage().getIdMessage().getId());

            // se messaggio è presente, si settano le proprietà relative al messaggio appena salvato nello storage
            if (message.isPresent()) {
                Message messageToUpdate = message.get();
                messageToUpdate.setUuidRepository(objectUploaded.getUuid());
                messageToUpdate.setPathRepository(objectUploaded.getPath());
                messageToUpdate.setName(objectUploaded.getName());
                // update del mesaggio con i nuovi parametri                    
                messageRepository.save(messageToUpdate);
                if (objectUploaded.getUuid() != null) {
                    // set come file già trattato nella tabella upload_queue
                    objectUploaded.setUploaded(Boolean.TRUE);
                    uploadQueueRepository.save(objectUploaded);
                }
            }
        } catch (Throwable e) {
            log.error("errore nell'upload del messaggio con id su upload_queue: " + messageToStore.getId());
            log.error("con errore: " + e);
            throw new ShpeckServiceException("errore nell'upload del messaggio");
        }
    }
}
