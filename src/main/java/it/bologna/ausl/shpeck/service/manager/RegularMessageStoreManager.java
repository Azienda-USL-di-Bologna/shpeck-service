package it.bologna.ausl.shpeck.service.manager;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.shpeck.service.constants.ApplicationConstant;
import it.bologna.ausl.shpeck.service.exceptions.MailMessageException;
import it.bologna.ausl.shpeck.service.transformers.MailMessage;
import it.bologna.ausl.shpeck.service.transformers.StoreResponse;
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
        
    @Transactional(rollbackFor = Throwable.class)
    public StoreResponse store() throws MailMessageException{
        log.info("Entrato in RegularMessageStoreManager.store()");
        Message regularMessage = createMessageForStorage((MailMessage) mailMessage, pec);
        regularMessage.setMessageType(Message.MessageType.MAIL);
        regularMessage.setIsPec(Boolean.FALSE);
        if(!isPresent(regularMessage)){
            regularMessage = storeMessage(regularMessage);
            log.info("Messaggio salvato " + regularMessage.toString());
            try{
                log.info("Salvo il RawMessage del REGULARMESSAGE");
                storeRawMessageAndUploadQueue(regularMessage, mailMessage.getRaw_message());
            }
            catch (MailMessageException e){
                log.error("Errore nel retrieving data del rawMessage dal mailMessage " +  e.getMessage());
                throw new MailMessageException("Errore nel retrieving data del rawMessage dal mailMessage", e);
            }
        }
        else {
            log.info("Messaggio già presente in tabella Messages con uuid: " + regularMessage.getUuidMessage());
        }
        log.info("Salvo gli indirizzi del regular message");
        HashMap mapMessagesAddress = upsertAddresses(mailMessage);
        log.info("Salvo sulla cross il regular message e indirizzi");
        storeMessagesAddresses(regularMessage, mapMessagesAddress);
        
        return new StoreResponse(ApplicationConstant.OK_KEY, mailMessage, regularMessage);
    }
}