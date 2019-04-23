package it.bologna.ausl.shpeck.service.transformers;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.shpeck.service.constants.ApplicationConstant;
import it.bologna.ausl.shpeck.service.exceptions.MailMessageException;
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
public class PecMessageStoreManager extends StoreManager {
    
    private static final Logger log = LoggerFactory.getLogger(PecMessageStoreManager.class);
    
    private PecMessage pecMessage;
    private Pec pec;
    

    public PecMessageStoreManager() {
    }

    public PecMessageStoreManager(PecMessage pecMessage, Pec pec){
        this.pecMessage = pecMessage;
        this.pec = pec;
    }

    public PecMessage getPecMessage() {
        return pecMessage;
    }

    public void setPecMessage(PecMessage pecMessage) {
        this.pecMessage = pecMessage;
    }

    public Pec getPec() {
        return pec;
    }

    public void setPec(Pec pec) {
        this.pec = pec;
    }
    
    
    @Transactional(rollbackFor = MailMessageException.class)
    public Map<String, MailMessage> store() throws MailMessageException {
        
        Map<String, MailMessage> res = new HashMap<>();
        
        log.info("Entrato in PecMessageStoreManager.store()");
        log.info("Sbusto il messaggio...");
        Message messaggioSbustato = createMessageForStorage((MailMessage) pecMessage, pec, false);
        messaggioSbustato.setMessageType(Message.MessageType.MAIL);
        if(isPresent(messaggioSbustato)){
            res.put("ok", pecMessage);
            return res;
        }
        storeMessage(messaggioSbustato);
        try{
            log.info("Salvo il RawMessage dello SBUSTATO");
            storeRawMessage(messaggioSbustato, pecMessage.getRaw_message());
        } catch (MailMessageException e){
            log.error("Errore nel retrieving data del rawMessage dal pecMessage " +  e.getMessage());
            e.printStackTrace();
            throw e;
        }
        log.info("salvato messaggio sbustato con id: " + messaggioSbustato.getId());
        log.info("Salvo gli indirizzi dello sbustato");
        HashMap mapSbustato = upsertAddresses(pecMessage);
        
        log.info("Salvo sulla cross messaggio SBUstato e indirizzi");
        storeMessagesAddresses(messaggioSbustato, mapSbustato);
        
        // prendo la busta
        log.info("Salvataggio della busta...");
        MailMessage envelope = pecMessage.getPecEnvelope();
        Message messaggioBustato = createMessageForStorage(envelope, pec, false);
        messaggioBustato.setIdRelated(messaggioSbustato);
        if(pecMessage.getxTrasporto().equals("errore"))
            messaggioBustato.setMessageType(Message.MessageType.ERROR);
        else
            messaggioBustato.setMessageType(Message.MessageType.PEC);
        storeMessage(messaggioBustato);
        try{
            log.info("Salvo il RawMessage della BUSTA");
            storeRawMessage(messaggioBustato, envelope.getRaw_message());
        } catch (MailMessageException e){
            log.error("Errore nel retrieving data del rawMessage dal pecMessage " +  e.getMessage());
            e.printStackTrace();
            throw e;
        }
        log.info("salvato messaggio busta con id: " + messaggioBustato.getId());
        log.info("Salvo gli indirizzi dello sbustato");
        HashMap mapBusta = upsertAddresses(envelope);
        log.info("Salvo sulla cross messaggio bustato e indirizzi");
        storeMessagesAddresses(messaggioBustato, mapBusta);
        
        res.put(ApplicationConstant.OK_KEY, pecMessage);
        return res;
    }
}
