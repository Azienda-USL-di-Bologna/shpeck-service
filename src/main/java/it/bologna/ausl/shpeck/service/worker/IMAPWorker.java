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

    @Value("${id-applicazione}")
    String idApplicazione;

    private ArrayList<MailMessage> messages;
    private ArrayList<MailMessage> messagesOk;
    private ArrayList<MailMessage> messagesOrphans;

    public IMAPWorker() {
        messagesOk = new ArrayList<>();
        messagesOrphans = new ArrayList<>();
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

        if (messagesOrphans == null) {
            messagesOrphans = new ArrayList<>();
        } else {
            messagesOrphans.clear();
        }
    }

    @Override
    public void run() {
        MDC.put("logFileName", threadName);
        log.info("------------------------------------------------------------------------");
        log.info("START > idPec: [" + idPec + "]" + " time: " + new Date());

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
                //imapManager.setLastUID(1119);
            }

            // ottenimento dei messaggi
            messages = imapManager.getMessages();

            MailProxy mailProxy;
            StoreResponse res = null;

            for (MailMessage message : messages) {
                log.info("==================== gestione messageId: " + message.getId() + " ====================");

                try {
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
                                log.info("gestione metadati -> OK");
                                break;

                            case RECEPIT:
                                log.info("tipo calcolato: RICEVUTA");
                                recepitMessageStoreManager.setPecRecepit((PecRecepit) mailProxy.getMail());
                                recepitMessageStoreManager.setPec(pec);
                                recepitMessageStoreManager.setApplicazione(applicazione);
                                log.info("salvataggio metadati...");
                                res = recepitMessageStoreManager.store();
                                log.info("gestione metadati -> OK");
                                break;

                            case MAIL:
                                log.info("tipo calcolato: REGULAR MAIL");
                                regularMessageStoreManager.setMailMessage((MailMessage) mailProxy.getMail());
                                regularMessageStoreManager.setPec(pec);
                                regularMessageStoreManager.setApplicazione(applicazione);
                                log.info("salvataggio metadati...");
                                res = regularMessageStoreManager.store();
                                log.info("gestione metadati -> OK");
                                break;

                            default:
                                res = null;
                                log.error("tipo calcolato: *** DATO SCONOSCIUTO ***");
                        }
                    }

                    // segnalazione del caricamento di nuovi messaggi in tabella da salvare nello storage
                    messageSemaphore.release();

                    // individuazione dei messaggi OK e quelli ORPHAN
                    if (res != null) {
                        if (res.getStatus().equals(ApplicationConstant.OK_KEY)) {
                            messagesOk.add(res.getMailMessage());
                        } else {
                            messagesOrphans.add(res.getMailMessage());
                        }
                    }
                } catch (Throwable e) {
                    log.error("eccezione nel processare il messaggio corrente: " + e);
                }
            }

            log.info("___esito e policy___");
            log.info("messaggi 'OK': " + ((messagesOk == null || messagesOk.isEmpty()) ? "nessuno" : ""));
            messagesOk.forEach((mailMessage) -> {
                log.info(mailMessage.getId());
            });

            log.info("messaggi 'ORFANI': " + ((messagesOrphans == null || messagesOrphans.isEmpty()) ? "nessuno" : ""));
            messagesOrphans.forEach((mailMessage) -> {
                log.info(mailMessage.getId());
            });

            // le ricevute orfane si salvano sempre nella cartella di backup
            for (MailMessage tmpMessage : messagesOrphans) {
                imapManager.messageMover(tmpMessage.getId());
            }

            // individuazione della policy della casella
            switch (pec.getMessagePolicy()) {
                case (ApplicationConstant.MESSAGE_POLICY_BACKUP):
                    log.info("Message Policy della casella: BACKUP, sposta nella cartella di backup");
                    imapManager.messageMover(messagesOk);
                    break;

                case (ApplicationConstant.MESSAGE_POLICY_DELETE):
                    log.info("Message Policy della casella: DELETE, Cancella i messaggi salvati");
                    imapManager.deleteMessage(messagesOk);
                    break;

                default:
                    log.info("Message Policy della casella: NONE, non si fa nulla");
                    break;
            }

            // aggiornamento lastUID relativo alla casella appena scaricata
            imapManager.updateLastUID(pec);

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
        log.info("------------------------------------------------------------------------");
        MDC.remove("logFileName");
    }

}
