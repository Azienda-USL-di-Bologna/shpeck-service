package it.bologna.ausl.shpeck.service.transformers;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 *
 * @author Salo
 */
@Component
public class RegularMessageStoreManager extends StoreManager {
    private static final Logger log = LoggerFactory.getLogger(RegularMessageStoreManager.class);
    
    private MailMessage mailMessage;
    private Pec pec;

    public RegularMessageStoreManager() {
    }
    
    public Pec getPec() {
        return pec;
    }

    public void setPec(Pec pec) {
        this.pec = pec;
    }

    public MailMessage getMailMessage() {
        return mailMessage;
    }

    public void setMailMessage(MailMessage mailMessage) {
        this.mailMessage = mailMessage;
    }
        
    public void store(){
        log.info("Entrato in RegularMessageStoreManager.store()");
        Message regularMessage = createMessageForStorage((MailMessage) mailMessage, pec, false);
        regularMessage.setMessageType(Message.MessageType.MAIL);
        regularMessage.setIsPec(Boolean.FALSE);
        storeMessage(regularMessage);        
    }
    
}
