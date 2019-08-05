package it.bologna.ausl.shpeck.service.manager;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.configuration.Applicazione;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.Outbox;
import it.bologna.ausl.shpeck.service.exceptions.BeforeSendOuboxException;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.repository.ApplicazioneRepository;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.OutboxRepository;
import it.bologna.ausl.shpeck.service.repository.PecProviderRepository;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import it.bologna.ausl.shpeck.service.repository.RawMessageRepository;
import it.bologna.ausl.shpeck.service.transformers.MailMessage;
import it.bologna.ausl.shpeck.service.transformers.StoreResponse;
import it.bologna.ausl.shpeck.service.utils.MessageBuilder;
import it.bologna.ausl.shpeck.service.utils.SmtpConnectionHandler;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;
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
public class SMTPManager {

    private static final Logger log = LoggerFactory.getLogger(SMTPManager.class);
    private Transport transport;
    private Session session;

    @Autowired
    SmtpConnectionHandler smtpConnectionHandler;

    @Autowired
    PecRepository pecRepository;

    @Autowired
    PecProviderRepository pecProviderRepository;

    @Autowired
    RegularMessageStoreManager regularMessageStoreManager;

    @Autowired
    ApplicazioneRepository applicazioneRepository;

    @Autowired
    RawMessageRepository rawMessageRepository;

    @Autowired
    OutboxRepository outboxRepository;

    @Autowired
    MessageRepository messageRepository;

    public SMTPManager() {
    }

    public Transport getTransport() {
        return transport;
    }

    public void setTransport(Transport transport) {
        this.transport = transport;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public void buildSmtpManagerFromPec(Pec pec) throws ShpeckServiceException {
        log.debug("--- inizio buildSmtpManagerFromPec " + pec.toString());
        try {
            log.debug("Creo un SmtpConnectionHandler");
            smtpConnectionHandler.createSmtpSession(pec);
        } catch (Throwable e) {
            log.error("Errore: " + e + "\n"
                    + "Non posso creare l'SMTPManager per pec " + pec.toString() + "\n"
                    + "Rilancio errore");
            throw new ShpeckServiceException("errore nel costruire SMTP Manager: ", e);
        }
    }

    public String sendMessage(String rawData) {
        String res = null;
        try {
            MimeMessage mimeMessage = MessageBuilder.buildMailMessageFromString(rawData);
            //aggiorna i campi dell'header del messaggio per essere consistente con il contenuto del messaggio
            mimeMessage.saveChanges();
            smtpConnectionHandler.getTransport().sendMessage(mimeMessage, mimeMessage.getAllRecipients());
            log.info("sendMessage >> Messaggio inviato!");
            res = mimeMessage.getMessageID();
            log.info("Mime Message Id: " + res);
        } catch (Throwable e) {
            log.error("sendMessage >> Messaggio non inviato: " + e);
        }
        return res;
    }

    public StoreResponse saveMessageAndRaw(Outbox outbox) throws ShpeckServiceException {
        log.info("salva il message e fai upload nella queue...");
        StoreResponse storeResponse = null;
        try {
            log.debug("Buildo il mailMessage dal raw");
            MailMessage mailMessage = new MailMessage(MessageBuilder.buildMailMessageFromString(outbox.getRawData()));
            log.debug("Set inout, idPec e mailMessage...");
            regularMessageStoreManager.setInout(Message.InOut.OUT);
            regularMessageStoreManager.setPec(outbox.getIdPec());
            regularMessageStoreManager.setMailMessage(mailMessage);
            log.debug("Cerco l'applicazione d'origine");
            Applicazione app = applicazioneRepository.findById(outbox.getIdApplicazione().getId());
            log.info("Setto l'applicazione d'origine");
            regularMessageStoreManager.setApplicazione(app);
            log.debug("Setto l'outbox");
            regularMessageStoreManager.setOutbox(outbox);
            log.debug("salvo i metadati...");
            storeResponse = regularMessageStoreManager.store();
//            if (storeResponse != null) {
//                log.info("salvataggio eseguito correttamente");
//                // segnalazione del caricamento di nuovi messaggi in tabella da salvare nello storage
//                messageSemaphore.release();
//            }

        } catch (Throwable e) {
            throw new BeforeSendOuboxException("Non sono riuscito a salvare i metadati del messaggio in outbox con id " + outbox.getId(), e);
        }
        return storeResponse;
    }

    @Transactional(rollbackFor = Throwable.class, propagation = Propagation.REQUIRES_NEW)
    public void updateMetadata(Message m, Outbox outbox) {
        try {
            log.info("updateMetadata");
            //TODO: RIATTIVARE!!
            //outboxRepository.delete(outbox);
            //log.info("Eliminato");

            outboxRepository.save(outbox);
            log.info("test -> non eliminato (messo 'ignore' a " + outbox.getIgnore() + ")");

            log.info("aggiorno lo stato di message a " + m.getMessageStatus().toString() + "...");
            messageRepository.save(m);

            log.info("sono pronto per mettere il messaggio in upload queue");
            log.info("enqueueForUpload -> " + m.getId());
            log.info("chiamo lo store manager per salvare in uploadQueue");
            regularMessageStoreManager.insertToUploadQueue(m);
        } catch (Throwable e) {
            log.error("Errore su updateMetadata " + e);
        }
    }
}
