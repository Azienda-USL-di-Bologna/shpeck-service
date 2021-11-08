package it.bologna.ausl.shpeck.service.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoException;
import it.bologna.ausl.minio.manager.MinIOWrapper;
import it.bologna.ausl.minio.manager.MinIOWrapperFileInfo;
import it.bologna.ausl.minio.manager.exceptions.MinIOWrapperException;
import it.bologna.ausl.model.entities.baborg.Azienda;
import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.UploadQueue;
import it.bologna.ausl.mongowrapper.MongoWrapper;
import it.bologna.ausl.mongowrapper.exceptions.MongoWrapperException;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.factory.MinIOStorageFactory;
import it.bologna.ausl.shpeck.service.transformers.MailMessage;
import it.bologna.ausl.shpeck.service.utils.MessageBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 *
 * @author spritz
 */
@Component
public class MinIOPecStorage implements StorageStrategy {

    @Autowired
    MinIOStorageFactory mongoStorageFactory;

    private String folderPath;
    private MinIOWrapper minIOWrapper;

    public MinIOPecStorage() {
    }

    public MinIOPecStorage(Pec pec, String folderPath) throws UnknownHostException, MongoWrapperException, IOException, ShpeckServiceException {
        minIOWrapper = mongoStorageFactory.getMinIOWrapper(pec.getIdAziendaRepository().getId());
        this.folderPath = pec.getRepositoryRootPath();
    }

    @Override
    public void setFolderPath(Pec pec) {
        this.folderPath = pec.getRepositoryRootPath();
    }

    @Override
    public void setAzienda(Pec pec) throws ShpeckServiceException {
        try {
            this.minIOWrapper = mongoStorageFactory.getMinIOWrapper(pec.getIdAziendaRepository().getId());
        } catch (IOException | MongoException | MongoWrapperException ex) {
            throw new ShpeckServiceException("errore setAzienda su MinIOPecStorage", ex);
        }
    }

    @Override
    public UploadQueue storeMessage(Pec pec, String folderName, UploadQueue objectToUpload) throws ShpeckServiceException {

        String uuidMinIO;
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
            MinIOWrapperFileInfo fileInfo = minIOWrapper.put(new ByteArrayInputStream(baos.toByteArray()), pec.getIdAziendaRepository().getCodice(), path, filename, null, false);
            uuidMinIO = fileInfo.getGeneratedUuid();

            objectToUpload.setUploaded(Boolean.TRUE);
            objectToUpload.setUuid(uuidMinIO);

        } catch (MessagingException e) {
            throw new ShpeckServiceException("Errore nella lettura del MimeMessage", e);
        } catch (MinIOWrapperException ex) {
            throw new ShpeckServiceException("Errore nell'upload del MimeMessage", ex, ShpeckServiceException.ErrorTypes.STOREGE_ERROR);
        } catch (IOException e) {
            throw new ShpeckServiceException("Errore nella serializzazione del MimeMessage", e);
        }

        return objectToUpload;
    }
}
