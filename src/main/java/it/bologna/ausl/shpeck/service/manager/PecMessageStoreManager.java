package it.bologna.ausl.shpeck.service.manager;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Folder;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.MessageFolder;
import it.bologna.ausl.shpeck.service.constants.ApplicationConstant;
import it.bologna.ausl.shpeck.service.exceptions.MailMessageException;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.exceptions.StoreManagerExeption;
import it.bologna.ausl.shpeck.service.repository.MessageFolderRepository;
import it.bologna.ausl.shpeck.service.transformers.MailMessage;
import it.bologna.ausl.shpeck.service.transformers.PecMessage;
import it.bologna.ausl.shpeck.service.transformers.StoreResponse;
import java.util.HashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
public class PecMessageStoreManager extends StoreManager {

    @Autowired
    MessageTagStoreManager messageTagStoreManager;

//    @Autowired
//    MessageFolderRepository messageFolderRepository;
    private static final Logger log = LoggerFactory.getLogger(PecMessageStoreManager.class);

    private PecMessage pecMessage;
    private Pec pec;

    public PecMessageStoreManager() {
    }

    public PecMessageStoreManager(PecMessage pecMessage, Pec pec) {
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

    @Transactional(rollbackFor = Throwable.class, propagation = Propagation.REQUIRED)
    public StoreResponse store() throws MailMessageException, StoreManagerExeption, ShpeckServiceException {
        log.info("---inizio PecMessageStoreManager.store()---");
        log.debug("sbusto il messaggio...");

        boolean skipSbustato = false;
        boolean skipBustato = false;

        Message messaggioSbustato = createMessageForStorage((MailMessage) pecMessage, pec);
        messaggioSbustato.setIdApplicazione(getApplicazione());
        messaggioSbustato.setMessageType(Message.MessageType.MAIL);

        log.info("Verifico presenza messaggio...");
        Message messagePresentInDB = getMessageFromDb(messaggioSbustato);
        if (messagePresentInDB != null) {
            if (isValidRecord(messagePresentInDB)) {
                log.info("Messaggio sbustato valido già presente in tabella");
                skipSbustato = true;
                //return new StoreResponse(ApplicationConstant.OK_KEY, pecMessage, messagePresentInDB, false);
            } else {
                log.info("messaggio presente in DB ma non valido, procedo a reperire istanza presente");
                // uso l'istanza che ho già in DB così viene fatto UPDATE al posto di INSERT
                List<Message> tmpList = messageRepository.findByUuidMessageAndIdPecAndMessageType(messagePresentInDB.getUuidMessage(), pec, messagePresentInDB.getMessageType().toString());

                if (tmpList != null && tmpList.size() == 1) {
                    messaggioSbustato = tmpList.get(0);
                } else {
                    log.error("Errore nel reperimento del messaggio sbustato");
                    throw new MailMessageException("Errore nel reperimento del messaggio sbustato");
                }
            }
        }

        if (!skipSbustato) {
            messaggioSbustato = storeMessage(messaggioSbustato);

            try {
                log.debug("salvo il RawMessage dello sbustato");
                storeRawMessage(messaggioSbustato, pecMessage.getRaw_message());
            } catch (MailMessageException e) {
                log.error("Errore nel reperimento del rawMessage dal pecMessage " + e);
                throw new MailMessageException("Errore nel reperimento del rawMessage dal pecMessage", e);
            }
            log.debug("salvato messaggio sbustato con id: " + messaggioSbustato.getId());
            log.debug("salvo gli indirizzi dello sbustato...");
            HashMap mapSbustato = upsertAddresses(pecMessage);
            log.debug("salvataggio avvenuto con successo");

            log.debug("salvo sulla cross messaggio sbustato e indirizzi...");
            storeMessagesAddresses(messaggioSbustato, mapSbustato);
            log.debug("salvataggio avvenuto con successo");
            updateMessageExtension(messaggioSbustato, pecMessage);
        }

        // prendo la busta
        log.debug("salvataggio/gestione della busta...");
        MailMessage envelope = pecMessage.getPecEnvelope();
        Message messaggioBustato = createMessageForStorage(envelope, pec);
        messaggioBustato.setIdApplicazione(getApplicazione());
        messaggioBustato.setIdRelated(messaggioSbustato);
        if (pecMessage.getxTrasporto().equals("errore")) {
            messaggioBustato.setMessageType(Message.MessageType.ERROR);
            messaggioBustato.setMessageStatus(Message.MessageStatus.ERROR);
            messaggioSbustato.setMessageStatus(Message.MessageStatus.RECEIVED);
        } else {
            messaggioBustato.setMessageType(Message.MessageType.PEC);
        }

        // guardo se esiste già la busta su database
        List<Message> tmpList = messageRepository.findByUuidMessageAndIdPecAndMessageType(messaggioBustato.getUuidMessage(), messaggioBustato.getIdPec(), messaggioBustato.getMessageType().toString());

        if (tmpList != null && tmpList.size() == 1) {
            // prendo come messaggio bustato l'istanza che ho su DB così viene fatto UPDATE al posto di INSERT
            Message m = tmpList.get(0);
            messaggioBustato = m;
            if (isValidRecord(messaggioBustato)) {
                skipBustato = true;
                log.info("Messaggio bustato già presente in tabella Messages: " + messaggioBustato.getId().toString());
            }
        } else if (tmpList != null && tmpList.size() > 1) {
            log.error("Errore nel reperimento della busta: più di un record trovato");
            throw new MailMessageException("Errore nel reperimento della busta: più di un record trovato");
        }

        if (!skipBustato) {
            if (skipSbustato && messagePresentInDB != null) {
                messaggioBustato.setIdRelated(messagePresentInDB);
                messaggioBustato = storeMessage(messaggioBustato);
            } else {
                messaggioBustato = storeMessage(messaggioBustato);
            }
            try {
                log.debug("Salvo il RawMessage della BUSTA");
                storeRawMessage(messaggioBustato, envelope.getRaw_message());
            } catch (MailMessageException e) {
                log.error("Errore nel retrieving data del rawMessage dal pecMessage " + e.getMessage());
                throw new MailMessageException("Errore nel retrieving data del rawMessage dal pecMessage", e);
            }
            log.debug("salvato messaggio busta con id: " + messaggioBustato.getId());
            log.debug("Salvo gli indirizzi dello sbustato");
            HashMap mapBusta = upsertAddresses(envelope);
            log.debug("Salvo sulla cross messaggio bustato e indirizzi...");
            storeMessagesAddresses(messaggioBustato, mapBusta);
            log.debug("salvataggio avvenuto con successo");
            updateMessageExtension(messaggioBustato, envelope);
            boolean isToUpload = true;
            if ((messaggioSbustato.getUuidRepository() != null && !messaggioSbustato.getUuidRepository().equals(""))
                    && (messaggioBustato.getUuidRepository() != null && !messaggioBustato.getUuidRepository().equals(""))) {
                isToUpload = false;
            }

            if (skipSbustato) {
                return new StoreResponse(ApplicationConstant.OK_KEY, pecMessage, messaggioBustato, isToUpload, null);
            }
            return new StoreResponse(ApplicationConstant.OK_KEY, pecMessage, messaggioBustato, isToUpload, ((skipSbustato == true) ? null : messaggioSbustato));

        } else {
            return new StoreResponse(ApplicationConstant.OK_KEY, pecMessage, messaggioBustato, false, ((skipSbustato == true) ? null : messaggioSbustato));
        }
    }
}
