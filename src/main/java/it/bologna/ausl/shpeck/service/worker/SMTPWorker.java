package it.bologna.ausl.shpeck.service.worker;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.configuration.Applicazione;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.Outbox;
import it.bologna.ausl.shpeck.service.exceptions.BeforeSendOuboxException;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.manager.RegularMessageStoreManager;
import it.bologna.ausl.shpeck.service.manager.SMTPManager;
import it.bologna.ausl.shpeck.service.repository.ApplicazioneRepository;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.OutboxRepository;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import it.bologna.ausl.shpeck.service.transformers.MailMessage;
import it.bologna.ausl.shpeck.service.transformers.StoreResponse;
import it.bologna.ausl.shpeck.service.utils.MessageBuilder;
import it.bologna.ausl.shpeck.service.utils.SmtpConnectionHandler;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SMTPWorker implements Runnable {

    @Autowired
    PecRepository pecRepository;

    @Autowired
    MessageRepository messageRepository;

    @Autowired
    OutboxRepository outboxRepository;

    @Autowired
    ApplicazioneRepository applicazioneRepository;

    @Autowired
    Semaphore messageSemaphore;

    @Autowired
    SmtpConnectionHandler smtpConnectionHandler;

    @Autowired
    RegularMessageStoreManager regularMessageStoreManager;

    @Autowired
    SMTPManager smtpManager;

    @Value("${mail.smtp.sendDelay-seconds}")
    Integer defaultDelay;

    private static final Logger log = LoggerFactory.getLogger(SMTPWorker.class);
    private String threadName;
    private Integer idPec;

    private enum applications {
        BABEL, BABORG, DELI, DETE, FIRMONE, GEDI, GIPI, MYALISEO, PECG, PROCTON, RIBALTORG, SCRIVANIA, SHPECK, VERBA
    }

    public SMTPWorker() {
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public Integer getIdPec() {
        return idPec;
    }

    public void setIdPec(Integer idPec) {
        this.idPec = idPec;
    }

    @Override
    public void run() {
        MDC.put("logFileName", threadName);
        log.info("START -> idPec: [" + idPec + "]" + " time: " + new Date());

        try {
            // Prendo la pec
            Pec pec = pecRepository.findById(idPec).get();

            // carico i messaggi con message_status 'TO_SEND'
            // prendo il provider
            // creo un'istanza del manager
            List<Outbox> messagesToSend = outboxRepository.findByIdPecAndIgnoreFalse(pec);
            if (messagesToSend != null && messagesToSend.size() > 0) {
                smtpManager.buildSmtpManagerFromPec(pec);
                log.info("numero messaggi : " + messagesToSend.size() + " --> cicla...");
                for (Outbox outbox : messagesToSend) {
                    StoreResponse response = null;
                    try {

                        log.info("==================== gestione message in outbox con id: " + outbox.getId() + " ====================");
                        response = saveMessageAndUploadQueue(outbox);
                        Message m = response.getMessage();
                        log.info("salvataggio eseguito > provo a inviare messaggio con id outbox " + outbox.getId() + "...");
                        String messagID = smtpManager.sendMessage(outbox.getRawData());
                        if (messagID == null) {
                            log.error("Errore invio messaggio > metadati già salvati: " + response.getMessage().toString());
                            log.error("metto in stato di errore il messaggio " + m.getId());
                            m.setMessageStatus(Message.MessageStatus.ERROR);
                            log.error("setto in outbox come da ignorare");
                            outbox.setIgnore(Boolean.TRUE);
                            outboxRepository.save(outbox);
                        } else {
                            log.info("Messaggio inviato correttamente, setto il messaggio come spedito");
                            m.setMessageStatus(Message.MessageStatus.SENT);
                            m.setUuidMessage(messagID);
                            log.info("Stato settato, ora elimino da outbox...");
                            //TODO: RIATTIVARE!!
                            //outboxRepository.delete(outbox);
                            //log.info("Eliminato");

                            //TODO: 3 righe DA TOGLIERE!!
                            outbox.setIgnore(true);
                            outboxRepository.save(outbox);
                            log.info("test -> eliminato (messo a true ignore)");
                        }
                        log.info("aggiorno lo stato di message a " + m.getMessageStatus().toString() + "...");
                        messageRepository.save(m);
                        // segnalazione del caricamento di nuovi messaggi in tabella da salvare nello storage
                        if (response != null) {
                            messageSemaphore.release();
                        }
                        log.info("aggiornato");
                    } catch (BeforeSendOuboxException e) {
                        log.error("BeforeSendOuboxException: " + e);
                        continue;
                    } catch (Exception e) {
                        log.error("Errore: " + e);
                    }

                    log.debug("sleep per evitare invio massivo...");
                    if (pec.getSendDelay() != null && pec.getSendDelay() >= 0) {
                        TimeUnit.SECONDS.sleep(pec.getSendDelay());
                    } else {
                        TimeUnit.SECONDS.sleep(defaultDelay);
                    }
                    log.debug("sleep terminato, continuo");

                }

            }

            // ciclo i messaggi:
            // carico i raw_message con associato il messaggio
            // se tutto ok, salvo che è stato inviato:
            // m.message_status sent
            // altrimenti setto m.message_status error
            // comunque aggiungo il raw tra quelli da caricare su mongo
        } catch (Throwable e) {
            log.error("Errore del thread " + Thread.currentThread().getName() + "\n"
                    + "---> " + e);
        }
        log.info("STOP -> idPec: [" + idPec + "]" + " time: " + new Date());
        MDC.remove("logFileName");
    }

    public StoreResponse saveMessageAndUploadQueue(Outbox outbox) throws ShpeckServiceException {
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

        } catch (ShpeckServiceException e) {
            throw new BeforeSendOuboxException("Non sono riuscito a salvare i metadati del messaggio in outbox con id " + outbox.getId(), e);
        }
        return storeResponse;
    }
}
