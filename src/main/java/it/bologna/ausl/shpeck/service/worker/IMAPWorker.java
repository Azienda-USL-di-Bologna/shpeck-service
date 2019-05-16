package it.bologna.ausl.shpeck.service.worker;

import com.sun.mail.imap.IMAPStore;
import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.baborg.PecProvider;
import it.bologna.ausl.model.entities.configuration.Applicazione;
import it.bologna.ausl.shpeck.service.constants.ApplicationConstant;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.manager.IMAPManager;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import it.bologna.ausl.shpeck.service.transformers.MailMessage;
import it.bologna.ausl.shpeck.service.transformers.MailProxy;
import it.bologna.ausl.shpeck.service.transformers.PecMessage;
import it.bologna.ausl.shpeck.service.manager.PecMessageStoreManager;
import it.bologna.ausl.shpeck.service.transformers.PecRecepit;
import it.bologna.ausl.shpeck.service.manager.RecepitMessageStoreManager;
import it.bologna.ausl.shpeck.service.manager.RegularMessageStoreManager;
import it.bologna.ausl.shpeck.service.repository.ApplicazioneRepository;
import it.bologna.ausl.shpeck.service.repository.PecProviderRepository;
import it.bologna.ausl.shpeck.service.transformers.StoreResponse;
import it.bologna.ausl.shpeck.service.utils.ProviderConnectionHandler;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Semaphore;
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
 * @author spritz
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class IMAPWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(IMAPWorker.class);

    public static final int MESSAGE_POLICY_NONE = 0;
    public static final int MESSAGE_POLICY_BACKUP = 1;
    public static final int MESSAGE_POLICY_DELETE = 2;

    private String threadName;
    private Integer idPec;
    private Applicazione applicazione;

    @Autowired
    PecRepository pecRepository;

    @Autowired
    PecProviderRepository pecProviderRepository;

    @Autowired
    ApplicazioneRepository applicazioneRepository;

    @Autowired
    ProviderConnectionHandler providerConnectionHandler;

    @Autowired
    IMAPManager imapManager;

    @Autowired
    PecMessageStoreManager pecMessageStoreManager;

    @Autowired
    RecepitMessageStoreManager recepitMessageStoreManager;

    @Autowired
    RegularMessageStoreManager regularMessageStoreManager;

    @Autowired
    Semaphore messageSemaphore;

    @Value("${imap.reset-lastuid-minutes}")
    Integer resetLastuidMinutes;

    @Value("${id-applicazione}")
    String idApplicazione;

    private ArrayList<MailMessage> messages;
    private ArrayList<MailMessage> messagesOk;
    private ArrayList<MailMessage> orphans;

    public IMAPWorker() {
        messagesOk = new ArrayList<>();
        orphans = new ArrayList<>();
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

    private void init() {

        applicazione = applicazioneRepository.findById(idApplicazione);

        if (messages == null) {
            messages = new ArrayList<>();
        } else {
            messages.clear();
        }

        if (messagesOk == null) {
            messagesOk = new ArrayList<>();
        } else {
            messagesOk.clear();
        }

        if (orphans == null) {
            orphans = new ArrayList<>();
        } else {
            orphans.clear();
        }

    }

    @Override
    public void run() {
        MDC.put("logFileName", threadName);
        log.info("START -> idPec: [" + idPec + "]" + " time: " + new Date());

        init();

        try {
            Pec pec = pecRepository.findById(idPec).get();
            PecProvider idPecProvider = pecProviderRepository.findById(pec.getIdPecProvider().getId()).get();
            pec.setIdPecProvider(idPecProvider);
            log.info("host: " + idPecProvider.getHost());

            // ottenimento dell'oggetto IMAPStore
            IMAPStore store = providerConnectionHandler.createProviderConnectionHandler(pec);
            imapManager.setStore(store);

            // prendo il lastUID del messaggio in casella
            if (pec.getLastuid() != null) {
                imapManager.setLastUID(pec.getLastuid());
            }

            // ottenimento dei messaggi
            messages = imapManager.getMessages();

            MailProxy mailProxy;
            StoreResponse res = null;

            for (MailMessage message : messages) {
                log.info("gestione messageId: " + message.getId());

                mailProxy = new MailProxy(message);

                if (null == mailProxy.getType()) {
                    log.error("tipo calcolato: *** DATO SCONOSCIUTO ***");
                } else {
                    switch (mailProxy.getType()) {
                        case PEC:
                            log.info("tipo calcolato: PEC");
                            pecMessageStoreManager.setPecMessage((PecMessage) mailProxy.getMail());
                            pecMessageStoreManager.setPec(pec);
                            pecMessageStoreManager.setApplicazione(applicazione);

                            log.info("salvataggio metadati...");
                            res = pecMessageStoreManager.store();
                            log.info("salvataggio metadati -> OK");
                            break;

                        case RECEPIT:
                            log.info("tipo calcolato: RICEVUTA");
                            recepitMessageStoreManager.setPecRecepit((PecRecepit) mailProxy.getMail());
                            recepitMessageStoreManager.setPec(pec);
                            recepitMessageStoreManager.setApplicazione(applicazione);
                            log.info("salvataggio metadati...");
                            res = recepitMessageStoreManager.store();
                            log.info("salvataggio metadati -> OK");
                            break;
                        case MAIL:
                            log.info("tipo calcolato: REGULAR MAIL");
                            regularMessageStoreManager.setMailMessage((MailMessage) mailProxy.getMail());
                            regularMessageStoreManager.setPec(pec);
                            regularMessageStoreManager.setApplicazione(applicazione);
                            log.info("salvataggio metadati...");
                            res = regularMessageStoreManager.store();
                            log.info("salvataggio metadati -> OK");
                            break;
                        default:
                            res = null;
                            log.error("tipo calcolato: *** DATO SCONOSCIUTO ***");
                            break;
                    }
                }

                // segnalazione del caricamento di nuovi messaggi in tabella da salvare nello storage
                messageSemaphore.release();

                // individuazione dei messaggi OK e quelli ORPHAN
                if (res != null) {
                    if (res.getStatus().equals(ApplicationConstant.OK_KEY)) {
                        messagesOk.add(res.getMailMessage());
                    } else {
                        orphans.add(res.getMailMessage());
                    }
                }
            }

            log.info("messaggi 'OK': " + ((messagesOk == null || messagesOk.isEmpty()) ? "nessuno" : ""));
            for (MailMessage mailMessage : messagesOk) {
                log.info(mailMessage.getId());
            }

            log.info("messaggi 'ORFANI': " + ((orphans == null || orphans.isEmpty()) ? "nessuno" : ""));
            for (MailMessage mailMessage : orphans) {
                log.info(mailMessage.getId());
            }

            // le ricevute orfane si salvano sempre nella cartella di backup
            for (MailMessage tmpMessage : orphans) {
                imapManager.messageMover(tmpMessage.getId());
            }

            switch (pec.getMessagePolicy()) {
                case (MESSAGE_POLICY_BACKUP):
                    log.info("Message Policy della casella: BACKUP, sposto nella cartella di backup");
                    imapManager.messageMover(messagesOk);
                    break;
                case (MESSAGE_POLICY_DELETE):
                    log.info("Message Policy della casella: DELETE, Cancello i messaggi salvati");
                    imapManager.deleteMessage(messagesOk);
                    break;
                default:
                    log.info("Message Policy della casella: NONE, non si fa nulla");
                    break;
            }

            updateLastUID(pec);

        } catch (ShpeckServiceException e) {
            String message = "";
            if (e.getCause().getClass().isInstance(com.sun.mail.util.FolderClosedIOException.class)) {
                message = "\n\t" + e.getCause().getMessage();
            }
            log.error("Errore: " + message, e);
        } catch (Throwable e) {
            log.error("eccezione : " + e);
            log.info("STOP_WITH_EXCEPTION -> " + " idPec: [" + idPec + "]" + " time: " + new Date());
        }

        log.info("STOP -> idPec: [" + idPec + "]" + " time: " + new Date());
        MDC.remove("logFileName");
    }

    private void updateLastUID(Pec pec) {
        log.info("salvataggio lastUID nella PEC...");

        if (pec.getResetLastuidTime() == null) {
            // prima volta che fa run e il reset_lastuid_time non è settato
            pec.setResetLastuidTime(new java.sql.Timestamp(new Date().getTime()).toLocalDateTime());
            pec.setLastuid(imapManager.getLastUID());
        } else {
            // calcolo la differenza (in minuti) per capire se riazzerare la sequenza o meno
            LocalDateTime now = new java.sql.Timestamp(new Date().getTime()).toLocalDateTime();
            long minutes = pec.getResetLastuidTime().until(now, ChronoUnit.MINUTES);
            if (minutes > resetLastuidMinutes) {
                // i minuti passati dall'ultimo azzeramento sono superiori al valore di configurazione quindi azzeriamo
                pec.setResetLastuidTime(now);
                pec.setLastuid(0L);
            } else {
                // non è ancora il momento di azzerare la sequenza, aggiorno solo il lastuid
                pec.setLastuid(imapManager.getLastUID());
            }
        }
        pecRepository.save(pec);
        log.info("salvataggio lastUID -> OK");
    }
}
