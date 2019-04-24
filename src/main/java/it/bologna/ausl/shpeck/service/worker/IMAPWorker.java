package it.bologna.ausl.shpeck.service.worker;

import com.sun.mail.imap.IMAPStore;
import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.baborg.PecProvider;
import it.bologna.ausl.shpeck.service.constants.ApplicationConstant;
import it.bologna.ausl.shpeck.service.manager.IMAPManager;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import it.bologna.ausl.shpeck.service.transformers.MailMessage;
import it.bologna.ausl.shpeck.service.transformers.MailProxy;
import it.bologna.ausl.shpeck.service.transformers.PecMessage;
import it.bologna.ausl.shpeck.service.transformers.PecMessageStoreManager;
import it.bologna.ausl.shpeck.service.transformers.PecRecepit;
import it.bologna.ausl.shpeck.service.transformers.RecepitMessageStoreManager;
import it.bologna.ausl.shpeck.service.transformers.RegularMessageStoreManager;
import it.bologna.ausl.shpeck.service.utils.ProviderConnectionHandler;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    
    @Autowired
    PecRepository pecRepository;
    
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
    
    @Transactional
    @Override
    public void run() {
        try{
            Thread.currentThread().setName("ImapWorker::mailbox: " + threadName);
            log.info("START -> " + Thread.currentThread().getName() + " time: " + new Date());
            
            Pec pec = pecRepository.findById(730).get();
            PecProvider idPecProvider = pec.getIdPecProvider();
            log.info("host: " +idPecProvider.getHost() );

            IMAPStore store = providerConnectionHandler.createProviderConnectionHandler(pec);

            //IMAPManager manager = new IMAPManager(store, 14);
            //IMAPManager manager = new IMAPManager(store);
            imapManager.setStore(store);
            imapManager.setLastUID(75);
            messages = imapManager.getMessages();
            MailProxy mailProxy;
                
            Map<String, MailMessage> res = null;

            for (MailMessage message : messages) {
                log.info("---------------------------------");
                log.info("ID: " + message.getId());
                log.info("HEADER: " + message.getString_headers());
                log.info("SUBJECT: " + message.getSubject());

                mailProxy = new MailProxy(message);
                log.info("type: " + mailProxy.getType());

                if(null == mailProxy.getType())
                    log.error("*** DATO SCONOSCIUTO ***");
                else switch (mailProxy.getType()) {
                    case PEC:
                        log.info("è PEC: me la salvo");
                        pecMessageStoreManager.setPecMessage((PecMessage) mailProxy.getMail());
                        pecMessageStoreManager.setPec(pec);
                        res = pecMessageStoreManager.store();
                        //pecMessageStoreManager.upsertAddresses((MailMessage) mailProxy.getMail());
                        break;

                    case RECEPIT:
                        log.info("è una RICEVUTA: me la salvo");
                        recepitMessageStoreManager.setPecRecepit((PecRecepit) mailProxy.getMail());
                        recepitMessageStoreManager.setPec(pec);
                        res = recepitMessageStoreManager.store();
                        //recepitMessageStoreManager.upsertAddresses((MailMessage) mailProxy.getMail());
                        break;
                    case MAIL:
                        log.info("è una REGULAR MAIL: me la salvo");
                        regularMessageStoreManager.setMailMessage((MailMessage) mailProxy.getMail());
                        regularMessageStoreManager.setPec(pec);
                        res = regularMessageStoreManager.store();
                        //regularMessageStoreManager.upsertAddresses((MailMessage) mailProxy.getMail());
                        break;
                    default:
                        res = null;
                        log.error("*** DATO SCONOSCIUTO ***");
                        break;
                }

                if(res!=null){
                    if(res.get(ApplicationConstant.OK_KEY) != null)
                        messagesOk.add(res.get(ApplicationConstant.OK_KEY));
                    else
                        orphans.add(res.get(ApplicationConstant.ORPHAN_KEY));
                }

            }
            log.info("GLI 'OK':");
            for (MailMessage mailMessage : messagesOk) {
                System.out.println(mailMessage.getId());
            }
            log.info("GLI 'ORFANI':");
            for (MailMessage mailMessage : orphans) {
                System.out.println(mailMessage.getId());
            }

            // le ricevute orfane si salvano sempre nella cartella di backup
            for (MailMessage tmpMessage : orphans) {
                imapManager.messageMover(tmpMessage.getId());
            }

            log.info("Verifico la policy del provider : " + pec.getMessagePolicy());

            switch(pec.getMessagePolicy()){
                case (MESSAGE_POLICY_BACKUP):
                    log.info("Message Policy BackUp : sposto nella cartella di backup.");
                    imapManager.messageMover(messagesOk);
                    break;
                case (MESSAGE_POLICY_DELETE):
                    log.info("Message Policy DELETE : Cancello i messaggi salvati.");
                    imapManager.deleteMessage(messagesOk);
                    break;
                default:
                    log.info("Message Policy None : non faccio nulla.");
                    break;
            }
            
        //    TimeUnit.SECONDS.sleep(5);
        } catch(Throwable e){
            e.printStackTrace();
        }
        
        log.info("STOP -> " + Thread.currentThread().getName() + " time: " + new Date());
        
        
        
        //log.info("Partito IMAPWorker per " + pec.getDescrizione() + " - ore " + new Date().toString());
        
        //log.info("Esco dal worker");
    }
    
}
