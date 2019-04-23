package it.bologna.ausl.shpeck.service.transformers;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.shpeck.service.constants.ApplicationConstant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author Salo
 */
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
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
        
    @Transactional
    public Map<String, MailMessage> store(){
        Map<String, MailMessage> res = new HashMap<>();
        log.info("Entrato in RegularMessageStoreManager.store()");
        Message regularMessage = createMessageForStorage((MailMessage) mailMessage, pec, false);
        regularMessage.setMessageType(Message.MessageType.MAIL);
        regularMessage.setIsPec(Boolean.FALSE);
        if(!isPresent(regularMessage)){
            storeMessage(regularMessage);
        }
        else {
        }
        log.info("Salvo gli indirizzi del regular message");
        HashMap mapBusta = upsertAddresses(mailMessage);
        log.info("Salvo sulla cross il regular message e indirizzi");
        storeMessagesAddresses(regularMessage, mapBusta);
        
        res.put(ApplicationConstant.OK_KEY, mailMessage);
        return res;
    }
}