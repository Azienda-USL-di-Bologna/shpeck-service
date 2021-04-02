package it.bologna.ausl.shpeck.service.worker;

import com.sun.mail.imap.IMAPStore;
import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.baborg.PecProvider;
import it.bologna.ausl.model.entities.configuration.Applicazione;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.shpeck.service.constants.ApplicationConstant;
import it.bologna.ausl.shpeck.service.exceptions.BeanCreationNotAllowedExceptionShpeck;
import it.bologna.ausl.shpeck.service.exceptions.CannotCreateTransactionShpeck;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.exceptions.StoreManagerExeption;
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
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.PecProviderRepository;
import it.bologna.ausl.shpeck.service.transformers.StoreResponse;
import it.bologna.ausl.shpeck.service.utils.Diagnostica;
import it.bologna.ausl.shpeck.service.utils.ProviderConnectionHandler;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Semaphore;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

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

    @Value("${mailbox.backup-orphan-message-folder}")
    String BACKUP_ORPHAN_MESSAGE_FOLDER_NAME;

    @Autowired
    PecRepository pecRepository;

    @Autowired
    MessageRepository messageRepository;

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
    Diagnostica diagnostica;

    @Autowired
    Semaphore messageSemaphore;

    @Value("${test-mode}")
    Boolean testMode;

    @Value("${mailbox.inbox-folder}")
    String INBOX_FOLDER_NAME;

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

    public Applicazione getApplicazione() {
        return applicazione;
    }

    public void setApplicazione(Applicazione applicazione) {
        this.applicazione = applicazione;
    }

    private void init() {
//        log.debug("reperimento applicazione");
//        applicazione = applicazioneRepository.findById(idApplicazione);

        log.debug("setting messages array");
        if (messages == null) {
            messages = new ArrayList<>();
        } else {
            messages.clear();
        }

        log.debug("setting messagesOk array");
        if (messagesOk == null) {
            messagesOk = new ArrayList<>();
        } else {
            messagesOk.clear();
        }

        log.debug("setting messagesOrphans array");
        if (messagesOrphans == null) {
            messagesOrphans = new ArrayList<>();
        } else {
            messagesOrphans.clear();
        }
    }

    private void handleMessageSwitchingByPolicy(Pec pec, MailMessage message) throws Throwable {
        try {
            ArrayList<MailMessage> tmp = new ArrayList<MailMessage>();
            tmp.add(message);
            switch (pec.getMessagePolicy()) {
                case (ApplicationConstant.MESSAGE_POLICY_BACKUP):
                    log.info("Message Policy della casella: BACKUP, sposta nella cartella di backup");
                    imapManager.messageMover(tmp, null);
                    break;

                case (ApplicationConstant.MESSAGE_POLICY_DELETE):
                    log.info("Message Policy della casella: DELETE, Cancella i messaggi salvati");
                    imapManager.deleteMessage(tmp);
                    break;
                default:
                    log.info("Message Policy della casella: NONE, non si fa nulla");
                    break;
            }
        } catch (Throwable e) {
            log.error("ERRORE nella gestione del messaggio " + message.getId() + " in base alla POLICY " + pec.getMessagePolicy(), e);
            throw e;
        }
    }

    @Override
    public void run() {
        MDC.put("logFileName", threadName);
        log.info("------------------------------------------------------------------------");
        log.info("START > idPec: [" + idPec + "]" + " time: " + new Date());

        init();

        try {
            log.debug("reperimento pec...");
            Pec pec = pecRepository.findById(idPec).get();
            log.debug("pec caricata con successo");
            PecProvider idPecProvider = pecProviderRepository.findById(pec.getIdPecProvider().getId()).get();
            pec.setIdPecProvider(idPecProvider);
            log.info("host: " + idPecProvider.getHost());

            // ottenimento dell'oggetto IMAPStore
            IMAPStore store = providerConnectionHandler.createProviderConnectionHandler(pec);
            log.info("Setto lo store");
            imapManager.setStore(store);
            imapManager.setMailbox(pec.getIndirizzo());

            // prendo il lastUID del messaggio in casella
            log.info("prendo il lastUID del messaggio in casella... ");

            if (pec.getMessagePolicy() == ApplicationConstant.MESSAGE_POLICY_NONE) {
                if (pec.getLastuid() != null) {
                    log.info("Setto il last uuid " + pec.getLastuid());
                    imapManager.setLastUID(pec.getLastuid());
                    //imapManager.setLastUID(1119);
                }
            } else {
                // se sono in policy BACKUP o DELETE
                log.info("message policy backup o delete");
                imapManager.setLastUID(0);
            }

            // ottenimento dei messaggi
            messages = imapManager.getMessages(INBOX_FOLDER_NAME);

            MailProxy mailProxy;
            StoreResponse res = null;

            for (MailMessage message : messages) {
                log.info("==================== gestione messageId: " + message.getId() + " ====================");
                log.info("oggetto: " + message.getSubject());
                log.info("providerUID: " + message.getProviderUid());
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
                                if (res.isToUpload()) {
                                    log.info("prendo la busta dalla res");
                                    Message busta = res.getMessage();
                                    log.info("metto in upload queue la busta");
                                    pecMessageStoreManager.insertToUploadQueue(busta);
                                    log.info("metto in upload queue la mail sbustata");
                                    pecMessageStoreManager.insertToUploadQueue(busta.getIdRelated());
                                } else {
                                    log.info("Il messaggio non è da mettere su mongo: " + res.toString());
                                }
                                break;

                            case RECEPIT:
                                log.info("tipo calcolato: RICEVUTA");
                                recepitMessageStoreManager.setPecRecepit((PecRecepit) mailProxy.getMail());
                                recepitMessageStoreManager.setPec(pec);
                                recepitMessageStoreManager.setApplicazione(applicazione);
                                log.info("salvataggio metadati...");
                                res = recepitMessageStoreManager.store();
                                log.info("gestione metadati -> OK");
                                if (res != null) {
                                    if (res.isToUpload()) {
                                        log.info("prendo il message ricevuta dalla res");
                                        Message ricevuta = messageRepository.findById(res.getMessage().getId()).get();
                                        log.info("metto in upload queue la ricevuta");
                                        recepitMessageStoreManager.insertToUploadQueue(ricevuta);
                                    } else {
                                        log.info("Il messaggio non è da mettere su mongo: " + res.toString());
                                    }
                                } else {
                                    log.info("ricevuta già presente");
                                }
                                break;

                            case MAIL:
                                log.info("tipo calcolato: REGULAR MAIL");
                                regularMessageStoreManager.setMailMessage((MailMessage) mailProxy.getMail());
                                regularMessageStoreManager.setPec(pec);
                                regularMessageStoreManager.setApplicazione(applicazione);
                                log.info("salvataggio metadati...");
                                res = regularMessageStoreManager.store();
                                log.info("gestione metadati -> OK");
                                if (res.isToUpload()) {
                                    log.info("prendo il regular message dalla res");
                                    Message regularMessage = res.getMessage();
                                    log.info("metto in upload queue il regular message");
                                    regularMessageStoreManager.insertToUploadQueue(regularMessage);
                                } else {
                                    log.info("Il messaggio non è da mettere su mongo: " + res.toString());
                                }

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

                    // aggiornamento lastUID relativo alla casella appena scaricata
                    if (pec.getMessagePolicy() == ApplicationConstant.MESSAGE_POLICY_NONE) {
                        imapManager.setLastUID(message.getProviderUid());
                        pec = imapManager.updateLastUID(pec);
                        pec = pecRepository.findById(pec.getId()).get();
                    }
                    // TODO: spostare messaggio per messaggio e non tutto insieme come ora
                    // una volta fatto significa che se un messaggio è già presente su DB e un caso stranissimo e si deve segnalare
                    handleMessageSwitchingByPolicy(pec, message);

                } catch (CannotCreateTransactionException ex) {
                    throw new CannotCreateTransactionShpeck(ex.getMessage());
                } catch (BeanCreationNotAllowedException ex) {
                    throw new BeanCreationNotAllowedExceptionShpeck(ex.getMessage());
                } catch (OutOfMemoryError e) {
                    log.error("ERRORE: OutOfMemoryError: ", e);
                    throw new StoreManagerExeption(e.getMessage());
                } catch (Throwable e) {
                    log.error("eccezione nel processare il messaggio corrente: ", e);
                    // scrittura in generic report
                    JSONObject json = new JSONObject();
                    json.put("Mailbox", pec.getIndirizzo());
                    if (message.getId() != null) {
                        json.put("messageID", message.getId());
                    }
                    json.put("From", message.getFrom() != null ? message.getFrom()[0].toString() : null);
                    json.put("Subject", message.getSubject() != null ? message.getSubject() : null);
                    json.put("Exception", e.toString());
                    json.put("ExceptionMessage", e.getMessage());
                    diagnostica.writeInDiagnoticaReport("SHPECK_ERROR_PROCESSING_MESSAGE", json);
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

            if (!testMode) {
                // le ricevute orfane si salvano sempre nella cartella di backup apposita
                for (MailMessage tmpMessage : messagesOrphans) {
                    imapManager.messageMover(tmpMessage.getId(), BACKUP_ORPHAN_MESSAGE_FOLDER_NAME);
                }
            }

            imapManager.closeFolder();

            // aggiornamento lastUID relativo alla casella appena scaricata
            //imapManager.updateLastUID(pec);
        } catch (CannotCreateTransactionShpeck | BeanCreationNotAllowedExceptionShpeck e) {
            log.error("eccezione : ", e);
            log.info("STOP_WITH_EXCEPTION -> " + " idPec: [" + idPec + "]" + " time: " + new Date());
        } catch (ShpeckServiceException e) {
            String message = "";
            if (e.getCause().getClass().isInstance(com.sun.mail.util.FolderClosedIOException.class)) {
                message = "\n\t" + e.getCause().getMessage();
            }
            log.error("Errore: " + message, e);
        } catch (Throwable e) {
            log.error("eccezione : ", e);
            log.info("STOP_WITH_EXCEPTION -> " + " idPec: [" + idPec + "]" + " time: " + new Date());
        }
        imapManager.close();

        log.info("STOP -> idPec: [" + idPec + "]" + " time: " + new Date());
        log.info("------------------------------------------------------------------------");
        MDC.remove("logFileName");
    }

}
