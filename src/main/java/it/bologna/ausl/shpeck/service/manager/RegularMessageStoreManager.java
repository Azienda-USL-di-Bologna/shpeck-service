package it.bologna.ausl.shpeck.service.manager;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.Outbox;
import it.bologna.ausl.shpeck.service.constants.ApplicationConstant;
import it.bologna.ausl.shpeck.service.exceptions.MailMessageException;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.exceptions.StoreManagerExeption;
import it.bologna.ausl.shpeck.service.transformers.MailMessage;
import it.bologna.ausl.shpeck.service.transformers.StoreResponse;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
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
    private Outbox outbox; // Viene valorizzato solo nel caso di SMTP per un messaggio spedito da Pico.

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

    public Outbox getOutbox() {
        return outbox;
    }

    public void setOutbox(Outbox outbox) {
        this.outbox = outbox;
    }

    @Transactional(rollbackFor = Throwable.class, propagation = Propagation.REQUIRES_NEW)
    public StoreResponse store() throws MailMessageException, StoreManagerExeption, ShpeckServiceException {

        log.info("--- inizio RegularMessageStoreManager.store() ---");
        Message regularMessage = createMessageForStorage((MailMessage) mailMessage, pec);
        regularMessage.setIdApplicazione(getApplicazione());
        regularMessage.setIdOutbox(((outbox == null) || (outbox.getId() == null) ? null : outbox.getId()));
        regularMessage.setMessageType(Message.MessageType.MAIL);
        regularMessage.setIsPec(Boolean.FALSE);
        regularMessage.setExternalId(((outbox == null) || (outbox.getExternalId() == null) ? null : outbox.getExternalId()));
        Message messagePresentInDB = getMessageFromDb(regularMessage);
        if (messagePresentInDB == null) {
            regularMessage = storeMessage(regularMessage);
            log.info("Messaggio salvato " + regularMessage.toString());
            try {
                log.debug("Salvo il RawMessage del RegularMessage");
                storeRawMessage(regularMessage, mailMessage.getRaw_message());
            } catch (MailMessageException e) {
                log.error("Errore nel retrieving data del rawMessage dal mailMessage " + e.getMessage());
                throw new MailMessageException("Errore nel retrieving data del rawMessage dal mailMessage", e);
            }
        } else {
            log.info("Messaggio già presente in tabella Messages: " + messagePresentInDB.toString());
            regularMessage = messagePresentInDB;
        }
        log.debug("salvo/aggiorno gli indirizzi del regular message");
        HashMap mapMessagesAddress = upsertAddresses(mailMessage);
        log.debug("salvo/aggiorno sulla cross il regular message e indirizzi");
        storeMessagesAddresses(regularMessage, mapMessagesAddress);

        return new StoreResponse(ApplicationConstant.OK_KEY, mailMessage, regularMessage, true);
    }
}
