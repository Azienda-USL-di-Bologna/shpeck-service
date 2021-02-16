package it.bologna.ausl.shpeck.service.worker;

import com.sun.mail.imap.IMAPStore;
import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.baborg.PecProvider;
import it.bologna.ausl.model.entities.shpeck.Folder.FolderType;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.manager.IMAPManager;
import it.bologna.ausl.shpeck.service.repository.FolderRepository;
import it.bologna.ausl.shpeck.service.repository.PecProviderRepository;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import it.bologna.ausl.shpeck.service.utils.ProviderConnectionHandler;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

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

    private String threadName;
    private Integer idPec;

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

        // controlla se importare cartelle
        if (pec.getImportaFolders()) {
            log.info("inizio importazione cartelle");

            log.info("ottenimento oggetto IMAPStore");
            PecProvider idPecProvider = pecProviderRepository.findById(pec.getIdPecProvider().getId()).get();
            pec.setIdPecProvider(idPecProvider);
            IMAPStore store = providerConnectionHandler.createProviderConnectionHandler(pec);
            log.info("Setto lo store");
            imapManager.setStore(store);
            imapManager.setMailbox(pec.getIndirizzo());

            log.info("ottengo la folder di default (quella radice) dal provider...");
            Folder defaultFolder = imapManager.getDefaultFolder();
            log.info("done");

            createFolderInInternauta(pec, defaultFolder);
            log.info("importazione cartelle da legalmail completata");
        }

        // controlla se importare messaggi
        if (pec.getImportaMessages()) {
            log.info("importazione dei messaggi non ancora implementata");
            // TODO: da fare in un RM specifico
        }

        // setta flag di importa_casella a false
        pec.setImportaCasella(Boolean.FALSE);
        pecRepository.save(pec);

        log.info("STOP -> doWork()," + " time: " + new Date());
        log.info("------------------------------------------------------------------------");
    }

    private void createFolderInInternauta(Pec pec, Folder folder) throws MessagingException {
        FolderAux folderInfo = getFolderInfo(folder);
        it.bologna.ausl.model.entities.shpeck.Folder f = null;

        if (!folder.getName().isEmpty() && (!folderInfo.type.name().equals("SPAM"))) {
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
        }

        Folder[] list = folder.listSubscribed();
        if (list != null && list.length > 0) {
            for (Folder subF : list) {
                createFolderInInternauta(pec, subF);
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

    class FolderAux {

        String nameOrig = null;
        String fullnameOrig = null;
        FolderType type = null;
        boolean isCustom = false;
    }
}
