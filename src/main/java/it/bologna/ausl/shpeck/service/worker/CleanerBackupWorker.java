package it.bologna.ausl.shpeck.service.worker;

import com.sun.mail.imap.IMAPStore;
import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.baborg.PecProvider;
import it.bologna.ausl.shpeck.service.constants.ApplicationConstant;
import it.bologna.ausl.shpeck.service.manager.CleanerManager;
import it.bologna.ausl.shpeck.service.manager.IMAPManager;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.PecProviderRepository;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import it.bologna.ausl.shpeck.service.utils.Diagnostica;
import it.bologna.ausl.shpeck.service.utils.ProviderConnectionHandler;
import java.util.ArrayList;
import java.util.Date;
import org.json.simple.JSONObject;
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
@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
public class CleanerBackupWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CleanerBackupWorker.class);

    @Value("${mailbox.backup-folder}")
    String BACKUP_FOLDER_NAME;

    @Autowired
    CleanerManager cleanerManager;

    @Autowired
    MessageRepository messageRepository;

    @Autowired
    Diagnostica diagnostica;

    @Autowired
    PecRepository pecRepository;

    @Autowired
    PecProviderRepository pecProviderRepository;

    @Autowired
    IMAPManager imapManager;

    @Autowired
    ProviderConnectionHandler providerConnectionHandler;

    private String threadName;

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    private void CleanBackup(Pec casella) {
        try {
            PecProvider idPecProvider = pecProviderRepository.findById(casella.getIdPecProvider().getId()).get();
            casella.setIdPecProvider(idPecProvider);

            // ottenimento dell'oggetto IMAPStore
            IMAPStore store = providerConnectionHandler.createProviderConnectionHandler(casella);
            imapManager.setStore(store);
            imapManager.setMailbox(casella.getIndirizzo());
            imapManager.setLastUID(0);
            imapManager.deleteMessageFromFolder(BACKUP_FOLDER_NAME, casella.getKeepBackup());
            imapManager.closeFolder();
        } catch (Throwable e) {
            log.error("eccezione : ", e);
            log.info("STOP_CleanBackup -> " + " time: " + new Date());
        } finally {
            imapManager.close();
        }

    }

    public void doWork() {
        log.info("------------------------------------------------------------------------");
        log.info("start > *CLEANER BACKUP WORKER* time: " + new Date());
        try {
            // prendi pec che hanno policy 1
            ArrayList<Pec> list = pecRepository.findByAttivaTrueAndIdAziendaRepositoryNotNullAndMessagePolicy(ApplicationConstant.MESSAGE_POLICY_BACKUP);
            // per ogni casella pulisci da backup i messaggi vecchi
            for (Pec casella : list) {
                CleanBackup(casella);
            }
        } catch (Throwable e) {
            log.error("errore nel doWork ", e);
        }
        log.info("stop > *CLEANER BACKUP WORKER* time: " + new Date());
        log.info("------------------------------------------------------------------------");
    }

    @Override
    public void run() {
        MDC.put("logFileName", threadName);
        try {
            log.info("eseguo run di " + threadName);
            doWork();
        } catch (Throwable e) {
            log.info("errore nel main run di CelanerBackupWorker: " + e.toString());
            log.error(e.getMessage());
            e.printStackTrace();
        }
        MDC.remove("logFileName");
    }

    private void writeReportDiagnostica(Throwable e, Integer idUploadQueue) {
        // creazione messaggio di errore
        JSONObject json = new JSONObject();
        if (idUploadQueue != null) {
            json.put("id_upload_queue", idUploadQueue.toString());
        }
        json.put("Exception", e.toString());
        json.put("ExceptionMessage", e.getMessage());

        diagnostica.writeInDiagnoticaReport("SHPECK_ERROR_CLEANER_BACKUP_WORKER", json);
    }

}
