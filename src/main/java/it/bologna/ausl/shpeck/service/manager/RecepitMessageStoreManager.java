package it.bologna.ausl.shpeck.service.manager;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.Recepit;
import it.bologna.ausl.shpeck.service.constants.ApplicationConstant;
import it.bologna.ausl.shpeck.service.exceptions.MailMessageException;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.exceptions.StoreManagerExeption;
import it.bologna.ausl.shpeck.service.transformers.MailMessage;
import it.bologna.ausl.shpeck.service.transformers.PecRecepit;
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
public class RecepitMessageStoreManager extends StoreManager {

    private static final Logger log = LoggerFactory.getLogger(RecepitMessageStoreManager.class);

    private PecRecepit pecRecepit;
    private Pec pec;

    public RecepitMessageStoreManager() {
    }

    public PecRecepit getPecRecepit() {
        return pecRecepit;
    }

    public void setPecRecepit(PecRecepit pecRecepit) {
        this.pecRecepit = pecRecepit;
    }

    public Pec getPec() {
        return pec;
    }

    public void setPec(Pec pec) {
        this.pec = pec;
    }

    @Transactional(rollbackFor = Throwable.class, propagation = Propagation.REQUIRED)
    public StoreResponse store() throws MailMessageException, StoreManagerExeption, ShpeckServiceException {
        log.info("--- inizio RecepitMessageStoreManager.store() ---");
        Message messaggioDiRicevuta = createMessageForStorage((MailMessage) pecRecepit, pec);
        messaggioDiRicevuta.setIdApplicazione(getApplicazione());
        messaggioDiRicevuta.setMessageType(Message.MessageType.RECEPIT);
        messaggioDiRicevuta.setIsPec(Boolean.TRUE);
        //OLD  - Message relatedMessage = messageRepository.findByUuidMessageAndIsPecFalse(pecRecepit.getReference());
        String referredMessageIdFromRecepit = null;
        try {
            referredMessageIdFromRecepit = PecRecepit.getReferredMessageIdFromRecepit(pecRecepit.getOriginal());
        } catch (Throwable e) {
            log.error("referredMessageIdFromRecepit è null", e);
        }

        Message relatedMessage = messageRepository.findByUuidMessageAndIsPecFalse(referredMessageIdFromRecepit);

        if (relatedMessage == null) {
            log.warn("La ricevuta è orfana! Si riferisce a " + pecRecepit.getReference());
            return new StoreResponse(ApplicationConstant.ORPHAN_KEY, pecRecepit, messaggioDiRicevuta);
        }

        messaggioDiRicevuta.setIdRelated(relatedMessage);
        if (getMessageFromDb(messaggioDiRicevuta) != null) {
            return new StoreResponse(ApplicationConstant.OK_KEY, pecRecepit, messaggioDiRicevuta);
        }

        storeMessage(messaggioDiRicevuta);

        try {
            log.debug("Salvo il RawMessage della RICEVUTA");
            storeRawMessageAndUploadQueue(messaggioDiRicevuta, pecRecepit.getRaw_message());
        } catch (MailMessageException e) {
            log.error("Errore nel retrieving data del rawMessage dal pecRecepit " + e.getMessage());
            throw new MailMessageException("Errore nel retrieving data del rawMessage dal pecRecepit", e);
        }

        log.debug("Salvo gli indirizzi della ricevuta");
        HashMap mapRicevuta = upsertAddresses(pecRecepit);

        log.debug("Salvo sulla cross messaggio ricevuta e indirizzi");
        storeMessagesAddresses(messaggioDiRicevuta, mapRicevuta);

        Recepit recepit = new Recepit();
        recepit.setIdMessage(messaggioDiRicevuta);
        switch (pecRecepit.getxRicevuta()) {
            case "accettazione":
                recepit.setRecepitType(Recepit.RecepitType.ACCETTAZIONE);
                break;
            case "preavviso-errore-consegna":
                recepit.setRecepitType(Recepit.RecepitType.PREAVVISO_ERRORE_CONSEGNA);
                break;
            case "presa-in-carico":
                recepit.setRecepitType(Recepit.RecepitType.PRESA_IN_CARICO);
                break;
            case "non-accettazione":
                recepit.setRecepitType(Recepit.RecepitType.NON_ACCETTAZIONE);
                break;
            case "rilevazione-virus":
                recepit.setRecepitType(Recepit.RecepitType.RILEVAZIONE_VIRUS);
                break;
            case "errore-consegna":
                recepit.setRecepitType(Recepit.RecepitType.ERRORE_CONSEGNA);
                break;
            case "avvenuta-consegna":
                recepit.setRecepitType(Recepit.RecepitType.CONSEGNA);
                break;
            default:
                log.error("X-RICEVUTA UNKNOWN!!!! (boh)");
                break;
        }

        messaggioDiRicevuta.setIdRecepit(recepit);
        storeMessage(messaggioDiRicevuta);
        return new StoreResponse(ApplicationConstant.OK_KEY, pecRecepit, messaggioDiRicevuta);
    }

}
