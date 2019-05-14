package it.bologna.ausl.shpeck.service.worker;

import com.sun.mail.imap.IMAPStore;
import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.baborg.PecProvider;
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
import it.bologna.ausl.shpeck.service.repository.PecProviderRepository;
import it.bologna.ausl.shpeck.service.transformers.StoreResponse;
import it.bologna.ausl.shpeck.service.utils.ProviderConnectionHandler;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    
    @Autowired
    PecRepository pecRepository;
    
    @Autowired
    PecProviderRepository pecProviderRepository;
    
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
    
 
    private ArrayList<MailMessage> messages;
    private final ArrayList<MailMessage> messagesOk;
    private final ArrayList<MailMessage> orphans;

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
    
    //@Transactional
    @Override
    public void run() {
        MDC.put("logFileName", threadName);
        //Thread.currentThread().setName("ImapWorker::mailbox: " + threadName);
        log.info("START -> idPec: [" + idPec + "]" + " time: " + new Date());
        
        try{        
            Pec pec = pecRepository.findById(idPec).get();
            PecProvider idPecProvider = pecProviderRepository.findById(pec.getIdPecProvider().getId()).get();
            pec.setIdPecProvider(idPecProvider);
            log.info("host: " +idPecProvider.getHost());

            // ottenimento dell'oggetto IMAPStore
            IMAPStore store = providerConnectionHandler.createProviderConnectionHandler(pec);

            //IMAPManager manager = new IMAPManager(store, 14);
            //IMAPManager manager = new IMAPManager(store);
            imapManager.setStore(store);
            //imapManager.setLastUID(75);
            messages = imapManager.getMessages();
            MailProxy mailProxy;
                
            StoreResponse res = null;

            for (MailMessage message : messages) {
                log.info("gestione messageId: " + message.getId());

                mailProxy = new MailProxy(message);

                if(null == mailProxy.getType())
                    log.error("tipo calcolato: *** DATO SCONOSCIUTO ***");
                else switch (mailProxy.getType()) {
                    case PEC:
                        log.info("tipo calcolato: PEC");
                        pecMessageStoreManager.setPecMessage((PecMessage) mailProxy.getMail());
                        pecMessageStoreManager.setPec(pec);
                        log.info("salvataggio dei metadati...");
                        res = pecMessageStoreManager.store();
                        log.info("salvataggio dei metadati -> OK");
                        break;

                    case RECEPIT:
                        log.info("tipo calcolato: RICEVUTA");
                        recepitMessageStoreManager.setPecRecepit((PecRecepit) mailProxy.getMail());
                        recepitMessageStoreManager.setPec(pec);
                        log.info("salvataggio dei metadati...");
                        res = recepitMessageStoreManager.store();
                        log.info("salvataggio dei metadati -> OK");
                        break;
                    case MAIL:
                        log.info("tipo calcolato: REGULAR MAIL");
                        regularMessageStoreManager.setMailMessage((MailMessage) mailProxy.getMail());
                        regularMessageStoreManager.setPec(pec);
                        log.info("salvataggio dei metadati...");
                        res = regularMessageStoreManager.store();
                        log.info("salvataggio dei metadati -> OK");
                        break;
                    default:
                        res = null;
                        log.error("tipo calcolato: *** DATO SCONOSCIUTO ***");
                        break;
                }
                
                // segnalazione del caricamento di nuovi messaggi in tabella da salvare nello storage
                messageSemaphore.release();

                if(res!=null){
                    if(res.getStatus().equals(ApplicationConstant.OK_KEY))
                        messagesOk.add(res.getMailMessage());
                    else
                        orphans.add(res.getMailMessage());
                }
            }
            
            log.info("GLI 'OK':");
            for (MailMessage mailMessage : messagesOk) {
                log.info(mailMessage.getId());
            }
            log.info("GLI 'ORFANI':");
            for (MailMessage mailMessage : orphans) {
                log.info(mailMessage.getId());
            }

            // le ricevute orfane si salvano sempre nella cartella di backup
            for (MailMessage tmpMessage : orphans) {
                imapManager.messageMover(tmpMessage.getId());
            }

            log.info("Verifico la policy del provider: " + pec.getMessagePolicy());

            switch(pec.getMessagePolicy()){
                case (MESSAGE_POLICY_BACKUP):
                    log.info("Message Policy BACKUP: sposto nella cartella di backup.");
                    imapManager.messageMover(messagesOk);
                    break;
                case (MESSAGE_POLICY_DELETE):
                    log.info("Message Policy DELETE: Cancello i messaggi salvati.");
                    imapManager.deleteMessage(messagesOk);
                    break;
                default:
                    log.info("Message Policy None: non faccio nulla.");
                    break;
            }
        } catch (ShpeckServiceException e){
            String message = "";
            if (e.getCause().getClass().isInstance(com.sun.mail.util.FolderClosedIOException.class)) {
                message = "\n\t" + e.getCause().getMessage();
            }
            log.error("Errore: " + message, e);
        } catch(Throwable e){
            log.error("eccezione : " + e);
            // TODO: gestione errore e pensare se metterlo anche in db
            log.info("STOP CON -> [" + Thread.currentThread().getName() + "]" + " idPec: [" + idPec + "]" + " time: " + new Date());
        }
        log.info("STOP -> idPec: [" + idPec + "]" + " time: " + new Date());
        MDC.remove("logFileName");
    }
}
