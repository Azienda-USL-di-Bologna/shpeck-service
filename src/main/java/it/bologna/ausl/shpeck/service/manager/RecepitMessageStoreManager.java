package it.bologna.ausl.shpeck.service.manager;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.Recepit;
import it.bologna.ausl.model.entities.shpeck.Tag;
import it.bologna.ausl.shpeck.service.constants.ApplicationConstant;
import it.bologna.ausl.shpeck.service.exceptions.MailMessageException;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.exceptions.StoreManagerExeption;
import it.bologna.ausl.shpeck.service.transformers.MailMessage;
import it.bologna.ausl.shpeck.service.transformers.PecRecepit;
import it.bologna.ausl.shpeck.service.transformers.StoreResponse;
import java.util.HashMap;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    MessageTagStoreManager messageTagStoreManager;

//    @PersistenceContext
//    private EntityManager em;
    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Transactional(rollbackFor = Throwable.class, propagation = Propagation.REQUIRES_NEW)
    public StoreResponse store() throws MailMessageException, StoreManagerExeption, ShpeckServiceException, Exception {

        StoreResponse recepitResponse = gestioneRicevuta();
        return gestioneRelated(recepitResponse);
    }

    @Transactional(rollbackFor = Throwable.class)
    public StoreResponse gestioneRicevuta() throws MailMessageException, ShpeckServiceException {
        log.info("--- inizio RecepitMessageStoreManager.store() ---");

        Message messaggioDiRicevuta = createMessageForStorage((MailMessage) pecRecepit, pec);
        messaggioDiRicevuta.setIdApplicazione(getApplicazione());
        messaggioDiRicevuta.setMessageType(Message.MessageType.RECEPIT);
        messaggioDiRicevuta.setIsPec(Boolean.TRUE);

        if (getMessageFromDb(messaggioDiRicevuta) != null) {
            return new StoreResponse(ApplicationConstant.OK_KEY, pecRecepit, messaggioDiRicevuta, false);
        }

        storeMessage(messaggioDiRicevuta);

        try {
            log.debug("Salvo il RawMessage della RICEVUTA");
            storeRawMessage(messaggioDiRicevuta, pecRecepit.getRaw_message());
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
                // qui non posso mai entrare
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

        log.debug("Setto la ricevuta del messaggio di ricevuta");
        messaggioDiRicevuta.setIdRecepit(recepit);
        log.debug("Salvo il messaggio di ricevuta...");
        messaggioDiRicevuta = storeMessage(messaggioDiRicevuta);
        return new StoreResponse(ApplicationConstant.OK_KEY, pecRecepit, messaggioDiRicevuta, true);
    }

    //@Transactional(rollbackFor = Throwable.class)
    public StoreResponse gestioneRelated(StoreResponse recepitResponse) throws Exception {

        Message messaggioDiRicevuta = recepitResponse.getMessage();

        String referredMessageIdFromRecepit = null;
        try {
            referredMessageIdFromRecepit = PecRecepit.getReferredMessageIdFromRecepit(pecRecepit.getOriginal());
        } catch (Throwable e) {
            log.error("referredMessageIdFromRecepit = null", e);
        }

        Message relatedMessage = messageRepository.findByUuidMessageAndIsPecFalse(referredMessageIdFromRecepit);

        if (relatedMessage == null) {
            log.warn("ricevuta orfana - si riferisce a " + pecRecepit.getReference());
            return new StoreResponse(ApplicationConstant.ORPHAN_KEY, pecRecepit, messaggioDiRicevuta, recepitResponse.isToUpload());
        }

        switch (pecRecepit.getxRicevuta()) {
            case "accettazione":
                if (!(relatedMessage.getMessageStatus().toString().equalsIgnoreCase(Message.MessageStatus.CONFIRMED.toString()))
                        && !(relatedMessage.getMessageStatus().toString().equalsIgnoreCase(Message.MessageStatus.ERROR.toString()))) {
                    relatedMessage.setMessageStatus(Message.MessageStatus.ACCEPTED);
                }
                break;
            case "preavviso-errore-consegna":
                relatedMessage.setMessageStatus(Message.MessageStatus.ERROR);
                messageTagStoreManager.createAndSaveErrorMessageTagFromMessage(relatedMessage, Tag.SystemTagName.in_error);
                break;
            case "presa-in-carico":
                // qui non posso mai entrare
                break;
            case "non-accettazione":
                relatedMessage.setMessageStatus(Message.MessageStatus.ERROR);
                messageTagStoreManager.createAndSaveErrorMessageTagFromMessage(relatedMessage, Tag.SystemTagName.in_error);
                break;
            case "rilevazione-virus":
                relatedMessage.setMessageStatus(Message.MessageStatus.ERROR);
                messageTagStoreManager.createAndSaveErrorMessageTagFromMessage(relatedMessage, Tag.SystemTagName.in_error);
                break;
            case "errore-consegna":
                relatedMessage.setMessageStatus(Message.MessageStatus.ERROR);
                messageTagStoreManager.createAndSaveErrorMessageTagFromMessage(relatedMessage, Tag.SystemTagName.in_error);
                break;
            case "avvenuta-consegna":
                relatedMessage.setMessageStatus(Message.MessageStatus.CONFIRMED);
                break;
            default:
                log.error("X-RICEVUTA UNKNOWN!!!! (boh)");
                break;
        }

        log.debug("Faccio update dello stato del messaggio related -> " + relatedMessage.getMessageStatus().toString());

//        if (relatedMessage.getMessageStatus() != Message.MessageStatus.ERROR) {
        //messageRepository.updateMessageStatus(relatedMessage.getMessageStatus().toString(), relatedMessage.getId());
//        messageRepository.updateRelatedMessage(messaggioDiRicevuta.getId(), relatedMessage.getId());
//            Message m = em.find(Message.class, relatedMessage.getId());
//            m.setMessageStatus(relatedMessage.getMessageStatus());
//            //m.setIdRelated(messaggioDiRicevuta);
//            messaggioDiRicevuta.setIdRelated(m);
//            m.setUpdateTime(LocalDateTime.now());
//            messaggioDiRicevuta.setUpdateTime(LocalDateTime.now());
//            em.merge(m);
//            em.merge(messaggioDiRicevuta);
        String updateQuery1 = "update shpeck.messages set message_status = ?, update_time = now() where id = ?";
        jdbcTemplate.update(updateQuery1, relatedMessage.getMessageStatus().toString(), relatedMessage.getId());

        String updateQuery2 = "update shpeck.messages set id_related = ?, update_time = now() where id = ?";
        jdbcTemplate.update(updateQuery2, relatedMessage.getId(), messaggioDiRicevuta.getId());
//        }
        return new StoreResponse(ApplicationConstant.OK_KEY, pecRecepit, messaggioDiRicevuta, recepitResponse.isToUpload());
    }
}
