package it.bologna.ausl.shpeck.service.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.stereotype.Component;

/**
 *
 * @author spritz
 */
@Component
public class MinIOPecStorage implements StorageStrategy {

    private String folderPath;
    private Azienda azienda;
    private MinIOWrapper minIOWrapper;

    @Autowired
    MinIOStorageFactory minIOStorageFactory;

    public MinIOPecStorage() {
    }

    public MinIOPecStorage(Pec pec, String folderPath) throws UnknownHostException, MongoWrapperException, IOException, ShpeckServiceException {
        this.minIOWrapper = minIOStorageFactory.getMinIOWrapper(pec.getIdAziendaRepository().getId());
        this.folderPath = pec.getRepositoryRootPath();
    }

    @Override
    public void setFolderPath(Pec pec) {
        this.folderPath = pec.getRepositoryRootPath();
    }

    @Override
    public void setAzienda(Pec pec) {
        this.azienda = pec.getIdAziendaRepository();
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
            this.azienda = pec.getIdAziendaRepository();
            MinIOWrapperFileInfo fileInfo = minIOWrapper.put(new ByteArrayInputStream(baos.toByteArray()), azienda.getCodice(), path, filename, null, false);
            uuidMinIO = fileInfo.getGeneratedUuid();

            objectToUpload.setUploaded(Boolean.TRUE);
            objectToUpload.setUuid(uuidMinIO);

        } catch (MessagingException | MinIOWrapperException | IOException ex) {
            throw new ShpeckServiceException("Errore nell'upload del MimeMessage", ex, ShpeckServiceException.ErrorTypes.STOREGE_ERROR);
        }

        return objectToUpload;
    }
}
