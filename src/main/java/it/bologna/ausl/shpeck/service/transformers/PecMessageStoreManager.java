package it.bologna.ausl.shpeck.service.transformers;

import it.bologna.ausl.eml.handler.EmlHandlerUtils;
import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import java.io.IOException;
import javax.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    
    
    @Transactional
    public void store() {
        log.info("Entrato in PecMessageStoreManager.store()");
        log.info("Sbusto il messaggio");
        Message messaggioSbustato = createMessageForStorage((MailMessage) pecMessage, pec, false);
        messaggioSbustato.setMessageType(Message.MessageType.MAIL);
        storeMessage(messaggioSbustato);
        log.info("salvato: id " + messaggioSbustato.getId());
              
        // prendo la busta
        log.info("Salvo la busta");
        MailMessage envelope = pecMessage.getPecEnvelope();
        Message messaggioBustato = createMessageForStorage(envelope, pec, false);
        messaggioBustato.setIdRelated(messaggioSbustato);
        if(pecMessage.getxTrasporto().equals("errore"))
            messaggioBustato.setMessageType(Message.MessageType.ERROR);
        else
            messaggioBustato.setMessageType(Message.MessageType.PEC);
        storeMessage(messaggioBustato);
        log.info("salvato: id " + messaggioBustato.getId());
    }
}
