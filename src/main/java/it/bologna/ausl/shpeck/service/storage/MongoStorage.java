package it.bologna.ausl.shpeck.service.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.bologna.ausl.model.entities.shpeck.UploadQueue;
import it.bologna.ausl.mongowrapper.MongoWrapper;
import it.bologna.ausl.mongowrapper.exceptions.MongoWrapperException;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.transformers.MailMessage;
import it.bologna.ausl.shpeck.service.utils.MessageBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Map;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

/**
 *
 * @author spritz
 */
public class MongoStorage implements StorageStrategy {

    private String folderPath;
    private MongoWrapper mongo;

    public MongoStorage() {
    }

    public MongoStorage(String mongouri, String folderPath) throws UnknownHostException, MongoWrapperException {
        mongo = new MongoWrapper(mongouri);
        this.folderPath = folderPath;
    }

    public MongoStorage(String mongouri, String folderPath,
            Map<String, Object> minIOConfigurationObject,
            ObjectMapper om,
            String codiceAzienda,
            boolean mongoAndMinIOActive) throws UnknownHostException, MongoWrapperException {
        mongo = MongoWrapper.getWrapper(mongoAndMinIOActive,
                mongouri, (String) minIOConfigurationObject.get("DBDriver"),
                (String) minIOConfigurationObject.get("DBUrl"), (String) minIOConfigurationObject.get("DBUsername"),
                (String) minIOConfigurationObject.get("DBPassword"), codiceAzienda,
                (Integer) minIOConfigurationObject.get("maxPoolSize"), om);
        //mongo = new MongoWrapper(mongouri);
        this.folderPath = folderPath;
    }

    public void setMongo(MongoWrapper mongo) {
        this.mongo = mongo;
    }

    @Override
    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    @Override
    //@Transactional(rollbackFor = Throwable.class, propagation = Propagation.REQUIRED)
    public UploadQueue storeMessage(String folderName, UploadQueue objectToUpload) throws ShpeckServiceException {

        String uuidMongo;
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
                String asGmt = "";
                try {
                    asGmt = df.format(MailMessage.getSendDateInGMT(mimeMessage)) + " GMT";

                } catch (Exception e) {
                    asGmt = df.format(mimeMessage.getSentDate().getTime()) + " GMT";
                }
                filename = asGmt + " " + from + ".eml";
            } else {
                filename = MessageBuilder.getClearMessageID(mimeMessage.getMessageID()) + " " + from + ".eml";
            }
            filename = filename.replace(':', ' ').replaceAll("[^0-9a-zA-Z@ _\\.\\-]", "");
            //assicurarsi che sia un nome unico
            filename = objectToUpload.getIdRawMessage().getIdMessage().getId() + "_" + filename;

            String path = this.folderPath + "/" + objectToUpload.getIdRawMessage().getIdMessage().getIdPec().getIndirizzo() + "/" + folderName;
            objectToUpload.setPath(path);
            objectToUpload.setName(filename);
            uuidMongo = mongo.put(new ByteArrayInputStream(baos.toByteArray()), filename, path, false);

            objectToUpload.setUploaded(Boolean.TRUE);
            objectToUpload.setUuid(uuidMongo);

        } catch (MessagingException e) {
            throw new ShpeckServiceException("Errore nella lettura del MimeMessage", e);
        } catch (MongoWrapperException ex) {
            throw new ShpeckServiceException("Errore nell'upload del MimeMessage", ex, ShpeckServiceException.ErrorTypes.STOREGE_ERROR);
        } catch (IOException e) {
            throw new ShpeckServiceException("Errore nella serializzazione del MimeMessage", e);
        }

        return objectToUpload;
    }
}
