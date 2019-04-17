package it.bologna.ausl.shpeck.service;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;
import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.baborg.PecProvider;
import it.bologna.ausl.model.entities.baborg.projections.generated.PecWithIdPecProvider;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.shpeck.service.manager.IMAPManager;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.PecProviderRepository;
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
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import it.bologna.ausl.shpeck.service.worker.IMAPWorker;
import it.bologna.ausl.shpeck.service.worker.TestThread;
import it.bologna.ausl.shpeck.service.worker.ShutdownThread;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.mail.Folder;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author Salo
 */
@SpringBootApplication(scanBasePackages = "it.bologna.ausl.shpeck")
@EnableJpaRepositories({"it.bologna.ausl.shpeck.service.repository"})
@EntityScan("it.bologna.ausl.model.entities")
public class SpeckApplication {
    /**
     * Punto di partenza dell'applicazione
     */
    
    private static final Logger log = LoggerFactory.getLogger(SpeckApplication.class);
    public static final int MESSAGE_POLICY_NONE = 0;
    public static final int MESSAGE_POLICY_BACKUP = 1;
    public static final int MESSAGE_POLICY_DELETE = 2;
    
    @Autowired
    ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
    
    @Autowired
    ShutdownThread shutdownThread;
    
    @Autowired
    TestThread testThread;  
    
    @Autowired
    ProviderConnectionHandler providerConnectionHandler;
    
    @Autowired
    PecRepository pecRepository;
    
    @Autowired
    MessageRepository messageRepository;
    
    @Autowired
    ApplicationContext context;
    
    @Autowired
    PecMessageStoreManager pecMessageStoreManager;
    
    @Autowired
    RecepitMessageStoreManager recepitMessageStoreManager;
    
    @Autowired
    RegularMessageStoreManager regularMessageStoreManager;
    
    private ArrayList<MailMessage> messages;
    
    private ArrayList<MailMessage> messagesOk;
    private ArrayList<MailMessage> orphans;
    
    public static void main(String[] args) {
        SpringApplication.run(SpeckApplication.class, args); 
    }
    
    
    @Bean
    public CommandLineRunner schedulingRunner() {
        return new CommandLineRunner() {
            @Transactional
            public void run(String... args) throws Exception {
   
                messagesOk = new ArrayList<>();
                orphans = new ArrayList<>();
                
                Pec pec = pecRepository.findById(730).get();
                PecProvider idPecProvider = pec.getIdPecProvider();
                log.info("host: " +idPecProvider.getHost() );
                
                IMAPStore store = providerConnectionHandler.createProviderConnectionHandler(pec);
                
                //IMAPManager manager = new IMAPManager(store, 14);
                IMAPManager manager = new IMAPManager(store);
                messages = manager.getMessages();
               // log.info("size: " + messages.size());
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
                        if(res.get("ok") != null)
                            messagesOk.add(res.get("ok"));
                        else
                            orphans.add(res.get("orphan"));
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
                    manager.messageMover(tmpMessage.getId());
                }
                
                log.info("Verifico la policy del provider : " + pec.getMessagePolicy());
                
                switch(pec.getMessagePolicy()){
                    case (MESSAGE_POLICY_BACKUP):
                        log.info("Message Policy BackUp : sposto nella cartella di backup.");
                        manager.messageMover(messagesOk);
                        break;
                    case (MESSAGE_POLICY_DELETE):
                        log.info("Message Policy DELETE : Cancello i messaggi salvati.");
                        manager.deleteMessage(messagesOk);
                        break;
                    default:
                        log.info("Message Policy None : non faccio nulla.");
                        break;
                }
                
            }
//                
//                if (!messages.isEmpty()) {
//                    log.debug("got messages !");
//                }
                
//               scheduledThreadPoolExecutor.scheduleWithFixedDelay(new IMAPWorker(), 3, 10, TimeUnit.SECONDS);
//               
//               Runtime.getRuntime().addShutdownHook(shutdownThread);
//
//                
//                for (int i = 1;i < 4; i++) {
//                    TestThread testThread = new TestThread();
//                    testThread.setName("Thread " + i);
//                    testThread.start();
//                    TimeUnit.SECONDS.sleep(2);
//                }
            
        };
    }   
}
