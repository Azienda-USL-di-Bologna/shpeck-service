package it.bologna.ausl.shpeck.service.transformers;

import it.bologna.ausl.eml.handler.EmlHandlerUtils;
import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import java.io.IOException;
import java.util.Date;
import javax.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 *
 * @author spritz
 */
@Component
public class StoreManager implements StoreInterface{
    private static final Logger log = LoggerFactory.getLogger(StoreManager.class);
    
    @Autowired
    MessageRepository messageRepository;

    public StoreManager() {
    }
    
    
    
    @Override
    public Message createMessageForStorage(MailMessage mailMessage, Pec pec, boolean isAccettazione) {
        Message message = new Message();
        message.setUuidMessage(mailMessage.getId());
        message.setIdPec(pec);
        message.setSubject(mailMessage.getSubject()!=null ? mailMessage.getSubject() : "");
        message.setMessageStatus(Message.MessageStatus.RECEIVED);
        message.setInOut(Message.InOut.IN);
        message.setIsPec(mailMessage.getIsPec());

        try {
            message.setAttachmentsNumber(EmlHandlerUtils.getAttachments(mailMessage.getOriginal(), null).length);
        } catch (MessagingException | IOException ex) {
            log.error("Errore dello stabilire il numero di allegati", ex);
            message.setAttachmentsNumber(0);
        }
        if (mailMessage.getSendDate() != null) {
            message.setReceiveDate(new java.sql.Timestamp(mailMessage.getSendDate().getTime()).toLocalDateTime());
        } else {
            message.setReceiveDate(new java.sql.Timestamp(new Date().getTime()).toLocalDateTime());
        }
        message.setCreateTime(new java.sql.Timestamp(new Date().getTime()).toLocalDateTime());
        message.setUpdateTime(new java.sql.Timestamp(new Date().getTime()).toLocalDateTime());
        message.setSeen(false);
        
        return message;
    }
    
    public Message storeMessage(Message message){
        return messageRepository.save(message);
    }
    
}
