package it.bologna.ausl.shpeck.service.transformers;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 * @author spritz
 */
public class StoreManager implements StoreInterface{
    
    @Autowired
    MessageRepository messageRepository;
    
    @Override
    public Message storeMessage(MailMessage mailMessage, Pec pec, boolean isAccettazione) {
        
        Message message = new Message();
        message.setUuidMessage(mailMessage.getId());
        message.setIdPec(pec);
        message.setSubject(mailMessage.getSubject()!=null ? mailMessage.getSubject() : "");
        message.setMessageStatus(Message.MessageStatus.RECEIVED);
        message.setInOut(Message.InOut.IN);
        message.setIsPec(mailMessage.getIsPec());
        if (mailMessage.getSendDate() != null) {
            message.setReceiveDate(new java.sql.Timestamp(mailMessage.getSendDate().getTime()).toLocalDateTime());
        } else {
            message.setReceiveDate(new java.sql.Timestamp(new Date().getTime()).toLocalDateTime());
        }
        
        return messageRepository.save(message);
    }
    
}
