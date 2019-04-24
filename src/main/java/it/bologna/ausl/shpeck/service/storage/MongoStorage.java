package it.bologna.ausl.shpeck.service.storage;

import it.bologna.ausl.mongowrapper.MongoWrapper;
import it.bologna.ausl.mongowrapper.exceptions.MongoWrapperException;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

/**
 *
 * @author spritz
 */
public class MongoStorage implements StorageStrategy{
    
    private String folderPath;
    private MongoWrapper mongo;
    
    public static final int UPSTATUS_UPLOADED = 1;

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

    @Override
    public UploadMessage storeMessage(String folderName, UploadMessage uploadMessage) throws ShpeckServiceException{
        MimeMessage mimeMessage = uploadMessage.getMessage();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if (folderName == null) {
            folderName = "";
        }
        
        String res = null;
        try {
            mimeMessage.writeTo(baos);
            String from = null;
            try {
                from = mimeMessage.getFrom()[0].toString();
            } catch (Exception e) {
                from = "NONE";
            }
            String filename; // = from + "  " + mimeMessage.getSubject() + "" + mimeMessage.getMessageID() + "";

            if (mimeMessage.getSentDate() != null) {
                SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy HH:mm:ss");
                String asGmt = df.format(mimeMessage.getSentDate().getTime()) + " GMT";
                filename = asGmt + " " + from + ".eml";
            } else {
                filename = mimeMessage.getMessageID() + " " + from + ".eml";
            }
            filename = filename.replace(':', ' ').replaceAll("[^0-9a-zA-Z@ _\\.\\-]", "");
            //be sure to get an unique name
            filename = uploadMessage.getMessageId() + "_" + filename;
            uploadMessage.setPath(this.folderPath + "/" + folderName);
            uploadMessage.setName(filename);
            res = mongo.put(new ByteArrayInputStream(baos.toByteArray()), filename, this.folderPath + "/" + folderName, false);

            uploadMessage.setStatus(UPSTATUS_UPLOADED);
            uploadMessage.setUuid(res);

        } catch (MessagingException e) {
            throw new ShpeckServiceException("Error Uploading Mime message", e);
        } catch (IOException e) {
            throw new ShpeckServiceException("Error serializing Mime message", e);
        }

        return uploadMessage;
    }

    
    
}
