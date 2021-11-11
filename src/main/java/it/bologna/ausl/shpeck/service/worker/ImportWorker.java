package it.bologna.ausl.shpeck.service.worker;

import com.sun.mail.imap.IMAPStore;
import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.baborg.PecProvider;
import it.bologna.ausl.model.entities.shpeck.Folder.FolderType;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.MessageFolder;
import it.bologna.ausl.shpeck.service.exceptions.BeanCreationNotAllowedExceptionShpeck;
import it.bologna.ausl.shpeck.service.exceptions.CannotCreateTransactionShpeck;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.exceptions.StoreManagerExeption;
import it.bologna.ausl.shpeck.service.manager.IMAPManager;
import it.bologna.ausl.shpeck.service.manager.PecMessageStoreManager;
import it.bologna.ausl.shpeck.service.manager.RecepitMessageStoreManager;
import it.bologna.ausl.shpeck.service.manager.RegularMessageStoreManager;
import it.bologna.ausl.shpeck.service.repository.FolderRepository;
import it.bologna.ausl.shpeck.service.repository.MessageFolderRepository;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.PecProviderRepository;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import it.bologna.ausl.shpeck.service.transformers.MailMessage;
import it.bologna.ausl.shpeck.service.transformers.MailProxy;
import it.bologna.ausl.shpeck.service.transformers.PecMessage;
import it.bologna.ausl.shpeck.service.transformers.PecRecepit;
import it.bologna.ausl.shpeck.service.transformers.StoreResponse;
import it.bologna.ausl.shpeck.service.utils.Diagnostica;
import it.bologna.ausl.shpeck.service.utils.ProviderConnectionHandler;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.annotation.Autowired;
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
public class ImportWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ImportWorker.class);

    @Autowired
    PecRepository pecRepository;

    @Autowired
    ProviderConnectionHandler providerConnectionHandler;

    @Autowired
    IMAPManager imapManager;

    @Autowired
    FolderRepository folderRepository;

    @Autowired
    PecProviderRepository pecProviderRepository;

    @Autowired
    PecMessageStoreManager pecMessageStoreManager;

    @Autowired
    RecepitMessageStoreManager recepitMessageStoreManager;

    @Autowired
    RegularMessageStoreManager regularMessageStoreManager;

    @Autowired
    MessageRepository messageRepository;

    @Autowired
    MessageFolderRepository messageFolderRepository;

    @Autowired
    Diagnostica diagnostica;

    private String threadName;
    private Integer idPec;
    private ArrayList<String> folders = new ArrayList<>();

    public ImportWorker() {
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
        try {
            doWork();
        } catch (Throwable e) {
            log.error("errore", e);
        } finally {
            // interrompo esecuzione del runnable
            Thread.currentThread().interrupt();
            log.info("stop esecuzione del runnable ImportWorker");
        }
        MDC.remove("logFileName");
    }

    public void doWork() throws ShpeckServiceException, NoSuchProviderException, UnsupportedEncodingException, MessagingException {
        log.info("------------------------------------------------------------------------");
        log.info("START -> doWork()," + " time: " + new Date());

        log.debug("reperimento pec...");
        Pec pec = pecRepository.findById(idPec).get();

        log.info("ottenimento oggetto IMAPStore");
        PecProvider idPecProvider = pecProviderRepository.findById(pec.getIdPecProvider().getId()).get();
        pec.setIdPecProvider(idPecProvider);
        IMAPStore store = providerConnectionHandler.createProviderConnectionHandler(pec);
        log.info("Setto lo store");
        imapManager.setStore(store);
        imapManager.setMailbox(pec.getIndirizzo());

        // controlla se importare cartelle
        if (pec.getImportaFolders()) {
            log.info("inizio importazione cartelle");

            log.info("ottengo la folder di default (quella radice) dal provider...");
            Folder defaultFolder = imapManager.getDefaultFolder();
            log.info("done");

            createFolderFromInternauta(pec, defaultFolder, true);
            log.info("importazione cartelle da legalmail completata");
            // setta flag di importa_casella a false
            pec.setImportaFolders(Boolean.FALSE);
        } else {
            log.info("cartelle da non importare");
        }

        // controlla se importare messaggi
        if (pec.getImportaMessages()) {
            log.info("importazione dei messaggi");

            //creazione lista di folder da scannare
            ArrayList<it.bologna.ausl.model.entities.shpeck.Folder> folders = (ArrayList<it.bologna.ausl.model.entities.shpeck.Folder>) folderRepository.findAllByIdPec(pec);
            // per ognuna di queste ottieni messaggi e salvali nella cartella corretta

            for (it.bologna.ausl.model.entities.shpeck.Folder folder : folders) {
                if (folder.getFullnameInProvider() != null && !folder.getFullnameInProvider().equals("")) {
                    ArrayList<MailMessage> messages = imapManager.getMessages(folder.getFullnameInProvider());
                    log.info("=== START importazione cartella " + folder.getFullnameInProvider() + " ===");
                    importMessages(messages, pec, folder.getFullnameInProvider());
                    log.info("=== STOP importazione cartella " + folder.getFullnameInProvider() + " ===");
                }
            }

            // setta flag di importa_messaggi a false
            pec.setImportaMessages(Boolean.FALSE);
        } else {
            log.info("messaggi da non importare");
        }

        // setta flag di importa_casella a false
        pec.setImportaCasella(Boolean.FALSE);
        pecRepository.save(pec);

        log.info("STOP -> doWork()," + " time: " + new Date());
        log.info("------------------------------------------------------------------------");
    }

    private void createFolderFromInternauta(Pec pec, Folder folder, boolean saveToDB) throws MessagingException {
        FolderAux folderInfo = getFolderInfo(folder);
        it.bologna.ausl.model.entities.shpeck.Folder f = null;

        if (!folder.getName().isEmpty() && (!folderInfo.type.name().equals("SPAM"))) {
            if (saveToDB) {
                if (!folderInfo.isCustom) {
                    f = folderRepository.findByIdPecAndType(pec, folderInfo.type.name());
                    f.setFullnameInProvider(folderInfo.fullnameOrig);
                    f.setNameInProvider(folderInfo.nameOrig);
                    folderRepository.save(f);
                } else {
                    f = folderRepository.findByIdPecAndTypeAndName(pec, folderInfo.type.name(), folderInfo.nameOrig);
                    if (f == null) {
                        it.bologna.ausl.model.entities.shpeck.Folder newFolder = new it.bologna.ausl.model.entities.shpeck.Folder();
                        newFolder.setName(folderInfo.nameOrig);
                        newFolder.setDescription(folderInfo.fullnameOrig);
                        newFolder.setType(folderInfo.type);
                        newFolder.setIdPec(pec);
                        newFolder.setNameInProvider(folderInfo.nameOrig);
                        newFolder.setFullnameInProvider(folderInfo.fullnameOrig);
                        folderRepository.save(newFolder);
                    } else {
                        log.info("folder: " + f.getName() + " presente nel sistema, non inserisco");
                    }
                }
            } else {
                folders.add(folderInfo.fullnameOrig);
            }
        }

        Folder[] list = folder.listSubscribed();
        if (list != null && list.length > 0) {
            for (Folder subF : list) {
                createFolderFromInternauta(pec, subF, saveToDB);
            }
        }
    }

    // mappatura delle cartelle
    private FolderAux getFolderInfo(Folder folder) {
        FolderAux res = new FolderAux();
        res.nameOrig = folder.getName();
        res.fullnameOrig = folder.getFullName();
        if (res.nameOrig != null && !res.nameOrig.isEmpty()) {
            switch (res.nameOrig) {
                case "INBOX":
                    res.isCustom = false;
                    res.type = FolderType.INBOX;
                    break;
                case "Draft":
                    res.isCustom = false;
                    res.type = FolderType.DRAFT;
                    break;
                case "Trash":
                    res.isCustom = false;
                    res.type = FolderType.TRASH;
                    break;
                case "Spedite":
                    res.isCustom = false;
                    res.type = FolderType.SENT;
                    break;
                case "Posta Indesiderata":
                    res.isCustom = false;
                    res.type = FolderType.SPAM;
                    break;
                default:
                    res.isCustom = true;
                    res.type = FolderType.CUSTOM;
            }
        }

        return res;
    }

    private void importMessages(ArrayList<MailMessage> messages, Pec pec, String folderName) throws CannotCreateTransactionShpeck, BeanCreationNotAllowedExceptionShpeck, StoreManagerExeption {
        MailProxy mailProxy;
        StoreResponse res = null;

        it.bologna.ausl.model.entities.shpeck.Folder folder = folderRepository.findByIdPecAndFullnameInProvider(pec, folderName);

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
                            //pecMessageStoreManager.setApplicazione(applicazione);
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

                            if (res.getMessaggioSbustato() != null) {
                                MessageFolder mf = messageFolderRepository.findByIdMessageAndDeletedFalse(res.getMessaggioSbustato());
                                if (mf != null) {
                                    log.info("inserimento messaggio nella folder " + folder);
                                    mf.setIdFolder(folder);
                                    messageFolderRepository.save(mf);
                                }
                            } else {
                                log.info("nessun spostamento folder da attuare");
                            }

                            break;

                        case RECEPIT:
                            log.info("tipo calcolato: RICEVUTA");
                            recepitMessageStoreManager.setPecRecepit((PecRecepit) mailProxy.getMail());
                            recepitMessageStoreManager.setPec(pec);
//                            recepitMessageStoreManager.setApplicazione(applicazione);
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
//                            regularMessageStoreManager.setApplicazione(applicazione);
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

                            if (res.getMessaggioSbustato() != null) {
                                MessageFolder mf = messageFolderRepository.findByIdMessageAndDeletedFalse(res.getMessaggioSbustato());
                                if (mf != null) {
                                    log.info("inserimento messaggio nella folder " + folder);
                                    mf.setIdFolder(folder);
                                    messageFolderRepository.save(mf);
                                }
                            } else {
                                log.info("nessun spostamento folder da attuare");
                            }

                            break;

                        default:
                            res = null;
                            log.error("tipo calcolato: *** DATO SCONOSCIUTO ***");
                    }
                }

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
                diagnostica.writeInDiagnoticaReport("SHPECK_ERROR_IMPORT_PROCESSING_MESSAGE", json);
            }
        }
    }

    class FolderAux {

        String nameOrig = null;
        String fullnameOrig = null;
        FolderType type = null;
        boolean isCustom = false;
    }
}
