package it.bologna.ausl.shpeck.service.worker;

import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.storage.StorageContext;
import it.bologna.ausl.shpeck.service.storage.UploadMessage;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author spritz
 */
public class UploadWorker implements Runnable{
    
    private static final Logger log = LoggerFactory.getLogger(UploadWorker.class);
    
    private BlockingQueue<Integer> queue;
    private StorageContext storageContext;
    
    

    public UploadWorker() {
    }

   
    private void doWork() throws ShpeckServiceException, UnknownHostException {
//        log.info("inizio upload");
//        
//        ArrayList<UploadMessage> messages;
//        
//        do {
//                messages = db.getInboxMessageToUpload();
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
//
//            } while (!messages.isEmpty());
    }
    

    @Override
    public void run() {
        try {
            Thread.currentThread().setName("UploadWorker");
        } catch (Exception e) {
        }
    }
    
}
