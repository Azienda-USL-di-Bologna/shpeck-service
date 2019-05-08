package it.bologna.ausl.shpeck.service.storage;

import it.bologna.ausl.model.entities.shpeck.UploadQueue;
import it.bologna.ausl.mongowrapper.MongoWrapper;
import it.bologna.ausl.mongowrapper.exceptions.MongoWrapperException;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.utils.MessageBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author spritz
 */
public class MongoStorage implements StorageStrategy{
    
    private String folderPath;
    private MongoWrapper mongo;

    public MongoStorage() {
    }
    
    public MongoStorage(String mongouri, String folderPath) throws UnknownHostException, MongoWrapperException {
        mongo = new MongoWrapper(mongouri);
        this.folderPath = folderPath;
    }
    
    @Override
    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public UploadQueue storeMessage(String folderName, UploadQueue objectToUpload) throws ShpeckServiceException {
        
        String res = null;
        String filename; 
        MimeMessage mimeMessage;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if (folderName == null) {
            folderName = "";
        }
        
        try {
            mimeMessage = MessageBuilder.buildMailMessageFromString(objectToUpload.getIdRawMessage().getRawData());
            mimeMessage.writeTo(baos);
            String from = null;
            try {
                from = mimeMessage.getFrom()[0].toString();
            } catch (Exception e) {
                from = "NONE";
            }

            if (mimeMessage.getSentDate() != null) {
                SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy HH:mm:ss");
                String asGmt = df.format(mimeMessage.getSentDate().getTime()) + " GMT";
                filename = asGmt + " " + from + ".eml";
            } else {
                filename = mimeMessage.getMessageID() + " " + from + ".eml";
            }
            filename = filename.replace(':', ' ').replaceAll("[^0-9a-zA-Z@ _\\.\\-]", "");
            //assicurarsi che sia un nome unico
            filename = objectToUpload.getIdRawMessage().getIdMessage().getId()+ "_" + filename;
            
            String path = this.folderPath + "/" + objectToUpload.getIdRawMessage().getIdMessage().getIdPec().getIndirizzo()+ "/" + folderName;
            objectToUpload.setPath(path);
            objectToUpload.setName(filename);
            res = mongo.put(new ByteArrayInputStream(baos.toByteArray()), filename, path, false);

            objectToUpload.setUploaded(Boolean.TRUE);
            objectToUpload.setUuid(res);
            
        } catch (MessagingException e) {
            throw new ShpeckServiceException("Errore nell'upload del MimeMessage", e);
        } catch (IOException e) {
            throw new ShpeckServiceException("Errore nella serializzazione del MimeMessage", e);
        }

        return objectToUpload;
    }
}