package it.bologna.ausl.shpeck.service.worker;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.Outbox;
import it.bologna.ausl.model.entities.shpeck.Tag;
import it.bologna.ausl.shpeck.service.exceptions.BeforeSendOuboxException;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.manager.MessageTagStoreManager;
import it.bologna.ausl.shpeck.service.manager.SMTPManager;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.OutboxRepository;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import it.bologna.ausl.shpeck.service.transformers.StoreResponse;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.mail.MessagingException;
import javax.mail.SendFailedException;
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
            //List<Outbox> messagesToSend = outboxRepository.findByIdPecAndIgnoreFalse(pec);
            List<Integer> messagesToSend = outboxRepository.findOutboxIdByIdPecAndIgnoreFalse(pec.getId());
            if (messagesToSend != null && messagesToSend.size() > 0) {
                smtpManager.buildSmtpManagerFromPec(pec);
                log.info("numero messaggi : " + messagesToSend.size() + " --> cicla...");

                //for (Outbox outbox : messagesToSend) {
                for (Integer idOutbox : messagesToSend) {
                    StoreResponse response = null;
                    Outbox outbox = new Outbox();
                    outbox = outboxRepository.findById(idOutbox).get();
                    try {
                        log.info("Provo a caricare OUTBOX con id " + idOutbox);
                        log.info("==================== gestione message in outbox con id: " + outbox.getId() + " ====================");
                        log.info("Cerco un message con questo idOutbox");
                        Message m = null;
                        m = messageRepository.getMessageByIdOutbox(idOutbox);
                        if (m == null) {
                            log.info("Non ho ancora un messaggio con questo outbox, quindi salvo i metadati");
                            response = smtpManager.saveMessageAndRaw(outbox);
                            m = response.getMessage();
                            log.info("salvataggio eseguito");
                        }
                        log.info("Provo a inviare messaggio con id outbox " + outbox.getId() + "...");
                        String messagID = null;
                        try {
                            messagID = smtpManager.sendMessage(outbox.getRawData());
                        } catch (Throwable ex) {
                            if (response != null && response.getMessage() != null) {
                                log.error("Errore invio messaggio > metadati già salvati: " + response.getMessage().toString(), ex);
                            } else {
                                log.error("Errore invio messaggio", ex);
                            }
                            log.error("errore: ", ex);
                            if (ex instanceof SendFailedException) {
                                // caso di indirizzo non valido
                                log.error("metto in stato di errore il messaggio " + m.getId() + " a causa di indirizzo errato", ex);
                                log.error("indirizzi: " + Arrays.toString(((SendFailedException) ex).getInvalidAddresses()), ex);
                                m.setMessageStatus(Message.MessageStatus.ERROR);
                                log.error("Metto il tag Errore al messaggio");
                                /*
                                 * gdm: commento outbox.setIgnore(true) perché sennò non ci accorgiamo dell'errore, 
                                 * dato che non viene neanche segnalato nel report
                                */
                                //outbox.setIgnore(true);
                                try {
                                    messageTagStoreManager.createAndSaveErrorMessageTagFromMessage(m, Tag.SystemTagName.technical_error);
                                } catch (Exception e) {
                                    log.error("ERRORE: Ho avuto problemi con il salvataggio dell message tag del messaggio " + m.toString(), e);
                                }
                            } else if (ex instanceof MessagingException) {
                                // caso di connessione momentaneamente non disponibile
                                log.info("connessione momentaneamente non disponibile, riprovo a inviare al prossimo giro", ex);
                            } else {
                                log.error("Errore generico su messaggio con id " + m.getId());
                                log.error("Errore :", ex);
                                log.error("Metto il tag Errore al messaggio");
                                m.setMessageStatus(Message.MessageStatus.ERROR);
                                /*
                                 * gdm: commento outbox.setIgnore(true) perché sennò non ci accorgiamo dell'errore, 
                                 * dato che non viene neanche segnalato nel report
                                */
                                //outbox.setIgnore(true);
                                try {
                                    messageTagStoreManager.createAndSaveErrorMessageTagFromMessage(m, Tag.SystemTagName.technical_error);
                                } catch (Exception e) {
                                    log.error("ERRORE: Ho avuto problemi con il salvataggio dell message tag del messaggio " + m.toString(), e);
                                }
                            }
                        }
                        
                        // TODO: if messagID == null scriviamo errore nel report??

                        if (messagID != null) {
                            log.info("Messaggio inviato correttamente, setto il messaggio come spedito");
                            m.setMessageStatus(Message.MessageStatus.SENT);
                            m.setUuidMessage(messagID);
                            // per default i messaggi inviati devono comparire già visti
                            m.setSeen(Boolean.TRUE);
                            log.info("Stato settato, ora elimino da outbox...");
                            outbox.setIgnore(true);
                            log.info("Controllo se togliere il message tags di errore...");
                            String deleteMT = messageTagStoreManager.removeErrorMessageTagIfExists(m, Tag.SystemTagName.technical_error);
                            log.info("Risultato: " + deleteMT);
                        }
                        smtpManager.updateMetadata(m, outbox);

                        // segnalazione del caricamento di nuovi messaggi in tabella da salvare nello storage
                        if (response != null) {
                            messageSemaphore.release();
                        }
                        log.info("aggiornato");
                    } catch (BeforeSendOuboxException e) {
                        log.error("BeforeSendOuboxException: ", e);

                        if (e.getMessage().equals("BUILD_MAILMESSAGE_FAILED")) {
                            log.error("errore creazione Message su outbox con id: " + outbox.getId());
                        }
                        log.error("Però continuo");
                        continue;
                    } catch (Exception e) {
                        log.error("Errore: ", e);
                    }

                    log.debug("se mail non ha invio massivo viene fatto uno sleep");
                    TimeUnit.MILLISECONDS.sleep(sendDelay);
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
                    + "---> ", e);
        }
        log.info("STOP -> idPec: [" + idPec + "]" + " time: " + new Date());
        log.info("------------------------------------------------------------------------");
        MDC.remove("logFileName");
    }
}
