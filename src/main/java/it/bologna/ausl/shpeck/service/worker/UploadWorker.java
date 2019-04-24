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
import it.bologna.ausl.shpeck.service.storage.StorageStrategy;
import it.bologna.ausl.shpeck.service.storage.UploadMessage;
import it.bologna.ausl.shpeck.service.utils.MessageBuilder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 *
 * @author spritz
 */
public class UploadWorker implements Runnable{
    
    private static final Logger log = LoggerFactory.getLogger(UploadWorker.class);
    
    private BlockingQueue<Integer> queue;
    private StorageContext storageContext;
    
    @Value("${mailbox.inbox-forlder}")
    String inboxForlder;
    
    @Autowired
    UploadQueueRepository uploadQueueRepository;
    
    @Autowired
    ObjectMapper objectMapper;
    
    @Autowired
    MessageRepository messageRepository;
    
    
    public UploadWorker() {
    }
    
   
    private void doWork() throws ShpeckServiceException, UnknownHostException {
        log.info("inizio upload");
     
        ArrayList<UploadQueue> messagesToUpload;
        
        do {
            messagesToUpload = uploadQueueRepository.getFromUploadQueue(Boolean.FALSE, Message.InOut.IN.toString());
                
            for (UploadQueue uploadQueue : messagesToUpload) {
                try {
                    AziendaParametriJson aziendaParams = AziendaParametriJson.parse(objectMapper, uploadQueue.getIdRawMessage().getIdMessage().getIdPec().getIdAzienda().getParametri());          
                    AziendaParametriJson.MongoParams mongoParams = aziendaParams.getMongoParams();

                    storageContext = new StorageContext(new MongoStorage(mongoParams.getConnectionString(), mongoParams.getRoot()));
//                    UploadMessage uploadMessage = new UploadMessage(uploadQueue.getIdRawMessage().getRawData());
//                    uploadMessage.setMessage(MessageBuilder.buildMailMessageFromString(uploadQueue.getIdRawMessage().getRawData()));
//                    uploadMessage.setConfigId(uploadQueue.getIdRawMessage().getIdMessage().getIdPec().getId());
//                    uploadMessage.setMessageId(uploadQueue.getIdRawMessage().getIdMessage().getId());
                    UploadQueue objectUploaded = storageContext.store(inboxForlder, uploadQueue);
                    
                    Optional<Message> message = messageRepository.findById(uploadQueue.getIdRawMessage().getIdMessage().getId());
                    Message messageToUpdate = null;
                    if(message.isPresent()){
                        messageToUpdate = message.get();
                        messageToUpdate.setUuidRepository(objectUploaded.getUuid());
                        messageToUpdate.setPathRepository(objectUploaded.getPath());
                    
                        messageRepository.save(messageToUpdate);
                    }
                } catch (Exception e) {
                }
            }
                
                
                
//                for (UploadMessage m : messages) {
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
    }
    

    @Override
    public void run() {
        try {
            Thread.currentThread().setName("UploadWorker");
        } catch (Exception e) {
        }
    }
    
}
