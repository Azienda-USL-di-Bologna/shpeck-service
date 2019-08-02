package it.bologna.ausl.shpeck.service.worker;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.Outbox;
import it.bologna.ausl.model.entities.shpeck.Tag;
import it.bologna.ausl.shpeck.service.exceptions.BeforeSendOuboxException;
import it.bologna.ausl.shpeck.service.manager.MessageTagStoreManager;
import it.bologna.ausl.shpeck.service.manager.SMTPManager;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.OutboxRepository;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import it.bologna.ausl.shpeck.service.transformers.StoreResponse;
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
    Semaphore messageSemaphore;

    @Autowired
    SMTPManager smtpManager;

    @Autowired
    MessageTagStoreManager messageTagStoreManager;

    @Value("${mail.smtp.sendNormalDelay-milliseconds}")
    Integer defaultDelayNormailMail;

    @Value("${mail.smtp.sendMassiveDelay-milliseconds}")
    Integer defaultDelayMassiveMail;

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
        log.info("------------------------------------------------------------------------");
        log.info("START -> idPec: [" + idPec + "]" + " time: " + new Date());

        try {
            // Prendo la pec
            log.debug("recupero PEC...");
            Pec pec = pecRepository.findById(idPec).get();
            log.debug("PEC caricata con successo");

            // check se casella ha invio massivo oppure no
            Integer sendDelay = 0;
            if (pec != null && pec.getMassiva()) {
                sendDelay = defaultDelayMassiveMail;
                log.debug("casella con invio massivo");
            } else {
                sendDelay = defaultDelayNormailMail;
                log.debug("casella con invio NON massivo");
            }

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
                        response = smtpManager.saveMessageAndRaw(outbox);
                        Message m = response.getMessage();
                        log.info("salvataggio eseguito > provo a inviare messaggio con id outbox " + outbox.getId() + "...");
                        String messagID = smtpManager.sendMessage(outbox.getRawData());
                        if (messagID == null) {
                            log.error("Errore invio messaggio > metadati già salvati: " + response.getMessage().toString());
                            log.error("metto in stato di errore il messaggio " + m.getId());
                            m.setMessageStatus(Message.MessageStatus.ERROR);
                            log.error("Metto il tag Errore al messaggio");
                            try {
                                messageTagStoreManager.createAndSaveErrorMessageTagFromMessage(m, Tag.SystemTagName.technical_error);
                            } catch (Exception e) {
                                log.error("ERRORE: Ho avuto problemi con il salvataggio dell message tag del messaggio " + m.toString());
                                log.error(e.toString());
                            }
                        } else {
                            log.info("Messaggio inviato correttamente, setto il messaggio come spedito");
                            m.setMessageStatus(Message.MessageStatus.SENT);
                            m.setUuidMessage(messagID);
                            // per default i messaggi inviati devono comparire già visti
                            m.setSeen(Boolean.TRUE);
                            log.info("Stato settato, ora elimino da outbox...");
                            outbox.setIgnore(true);
                        }

                        smtpManager.updateMetadata(m, outbox);

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

                    log.debug("se mail non ha invio massivo viene fatto uno sleep");
                    if (pec.getSendDelay() != null && pec.getSendDelay() >= 0) {
                        TimeUnit.MILLISECONDS.sleep(pec.getSendDelay());
                    } else {
                        TimeUnit.MILLISECONDS.sleep(sendDelay);
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
        log.info("------------------------------------------------------------------------");
        MDC.remove("logFileName");
    }
}
