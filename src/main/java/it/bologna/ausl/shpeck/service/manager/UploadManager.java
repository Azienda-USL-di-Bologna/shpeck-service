package it.bologna.ausl.shpeck.service.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.bologna.ausl.model.entities.baborg.Azienda;
import it.bologna.ausl.model.entities.configuration.ParametroAziende;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.UploadQueue;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.factory.MongoStorageFactory;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.UploadQueueRepository;
import it.bologna.ausl.shpeck.service.storage.MongoStorage;
import it.bologna.ausl.shpeck.service.storage.StorageContext;
import it.bologna.ausl.shpeck.service.worker.UploadWorker;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import it.bologna.ausl.shpeck.service.repository.ParametroAziendeRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

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
    ParametroAziendeRepository parametroAziendaRepository;

    @Autowired
    UploadQueueRepository uploadQueueRepository;

    @Value("${mailbox.inbox-folder}")
    String inboxForlder;

    @Autowired
    Semaphore messageSemaphore;

    @Autowired
    MongoStorageFactory mongoStorageFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Map<String, Object> getMinIOConfigurationObject(Integer idAzienda) throws IOException, ShpeckServiceException {
        Map<String, Object> minIOConfigMap = null;
        ArrayList<ParametroAziende> minIOParametroAziendeListByIdAzienda = parametroAziendaRepository.getMinIOParametroAziendeListByIdAzienda(idAzienda);
        if (minIOParametroAziendeListByIdAzienda != null && minIOParametroAziendeListByIdAzienda.size() == 1) {
            for (ParametroAziende parametroAziende : minIOParametroAziendeListByIdAzienda) {
                minIOConfigMap = objectMapper.readValue(parametroAziende.getValore(), new TypeReference<Map<String, Object>>() {
                });
            }
        } else {
            throw new ShpeckServiceException("Parametro minIOConfig non trovato o doppio per azienda " + idAzienda);
        }
        return minIOConfigMap;
    }

    //@Transactional(rollbackFor = Throwable.class, propagation = Propagation.REQUIRES_NEW)
    public void manage(UploadQueue messageToStore) throws ShpeckServiceException {
        try {
            log.info("Entrato in manage con id su upload_queue: " + messageToStore.getId() + " carico i parametri azienda");
            // ottieni parametri di mongo di un specifico ambiente guardando l'azienda associata alla pec
//            AziendaParametriJson aziendaParams = AziendaParametriJson.parse(objectMapper, messageToStore.getIdRawMessage().getIdMessage().getIdPec().getIdAziendaRepository().getParametri());
//
//            log.info("aziendaParams " + aziendaParams.toString());
//            AziendaParametriJson.MongoParams mongoParams = aziendaParams.getMongoParams();
//
//            Map<String, Object> minIOConfigurationObject = getMinIOConfigurationObject(messageToStore.getIdRawMessage().getIdMessage().getIdPec().getIdAziendaRepository().getId());
//
//            log.info("setto lo storageStrategy");
//            // inizializzazione del context storage
//            storageContext.setStorageStrategy(new MongoStorage(mongoParams.getConnectionString(), mongoParams.getRoot(), minIOConfigurationObject, objectMapper, messageToStore.getIdRawMessage().getIdMessage().getIdPec().getIdAziendaRepository().getCodice()));
            Azienda azienda = messageToStore.getIdRawMessage().getIdMessage().getIdPec().getIdAziendaRepository();
            MongoStorage mongoStorage = mongoStorageFactory.getMongoStorageByAzienda(azienda);
            storageContext.setStorageStrategy(mongoStorage);

            log.info("Fatto lo store del message");
            // esegue lo store del messaggio e ritorna l'oggetto con le proprietà settate (es: uuid, path, ...)
            UploadQueue objectUploaded = storageContext.store(inboxForlder, messageToStore);

            log.info("Carico il messaggio (optional)");
            // ottieni in messaggio associato al contenuto appena caricato
            Optional<Message> message = messageRepository.findById(messageToStore.getIdRawMessage().getIdMessage().getId());

            log.info("verifico presenza del messaggio");
            // se messaggio è presente, si settano le proprietà relative al messaggio appena salvato nello storage
            if (message.isPresent()) {
                log.info("messaggio presente");
                Message messageToUpdate = message.get();
                log.info("messaggio setto il uuid");
                messageToUpdate.setUuidRepository(objectUploaded.getUuid());
                log.info("messaggio setto il path");
                messageToUpdate.setPathRepository(objectUploaded.getPath());
                messageToUpdate.setName(objectUploaded.getName());
                log.info("--> " + messageToUpdate.toString());
                // update del mesaggio con i nuovi parametri
                log.info("salvo");
                //messageRepository.save(messageToUpdate);

                // update Uuid e Path Mongo e il Name
                String updateQuery = "update shpeck.messages set uuid_repository = ?, path_repository = ?, name = ?, update_time = now() where id = ?";
                int updatedRow = jdbcTemplate.update(updateQuery, objectUploaded.getUuid(), objectUploaded.getPath(), objectUploaded.getName(), message.get().getId());
//                messageRepository.updateUuidAndPathMongoAndName(objectUploaded.getUuid(), objectUploaded.getPath(), objectUploaded.getName(), message.get().getId());

//                if (objectUploaded.getUuid() != null) {
                if (updatedRow > 0) {
                    log.info("setto uploaded true");
                    // set come file già trattato nella tabella upload_queue
                    objectUploaded.setUploaded(Boolean.TRUE);
                    log.info("aggiorno objectUploaded");
                    uploadQueueRepository.save(objectUploaded);
                }
            } else {
                log.error("ERRORE: Message con id " + messageToStore.getIdRawMessage().getIdMessage().getId() + " non presente");
            }

        } catch (ShpeckServiceException e) {
            log.error("errore nell'upload del messaggio con id su upload_queue: " + messageToStore.getId());
            log.error("con errore: ", e);
            throw e;
        } catch (Throwable e) {
            log.error("errore nell'upload del messaggio con id su upload_queue: " + messageToStore.getId());
            log.error("con errore: ", e);
            throw new ShpeckServiceException("errore nell'upload del messaggio");
        } finally {
            log.info("manage finito");
        }
    }
}
