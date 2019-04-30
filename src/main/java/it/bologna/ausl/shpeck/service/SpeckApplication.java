package it.bologna.ausl.shpeck.service;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
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
import it.bologna.ausl.shpeck.service.worker.ShutdownThread;
import it.bologna.ausl.shpeck.service.worker.UploadWorker;
import java.util.ArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;

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
    
    @Autowired
    ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
    
    @Autowired
    ShutdownThread shutdownThread;
    
    @Autowired
    ApplicationContext context;
    
    @Autowired
    BeanFactory beanFactory;
        
    @Autowired
    UploadWorker uploadWorker;
    
    @Autowired
    PecRepository pecRepository;
    
//    @Autowired
//    TestThread testThread;  
       

    public static void main(String[] args) {
        SpringApplication.run(SpeckApplication.class, args); 
    }
 
    
    @Bean
    public CommandLineRunner schedulingRunner() {
        
        return new CommandLineRunner() {

            public void run(String... args) throws Exception {
                
                // avvio del thread di UploadWorker
//                Thread t = new Thread(uploadWorker);
//                t.start();
                
                // recupera le mail attive
                ArrayList<Pec> pecAttive = pecRepository.findByAttiva(true);
                
                // lancio di IMAPWorker per ogni casella PEC attiva
                log.info("creazione degli IMAPWorker per ogni casella PEC attiva...");
                for (int i = 0; i < pecAttive.size(); i++) {   
                    IMAPWorker imapWorker = beanFactory.getBean(IMAPWorker.class);
                    imapWorker.setThreadName("IMAPWorker_" + i + " PEC: " + pecAttive.get(i).getIndirizzo());
                    imapWorker.setIdPec(pecAttive.get(i).getId());
                    scheduledThreadPoolExecutor.scheduleWithFixedDelay(imapWorker, i * 3 + 2, 60, TimeUnit.SECONDS);
                    log.info("IMAPWorker_su PEC " + pecAttive.get(i).getIndirizzo() + "schedulato correttamente");
                }
                log.info("creazione degli IMAPWorker eseguita con successo");
                
                Runtime.getRuntime().addShutdownHook(shutdownThread);
            }          
         
//                for (int i = 1;i < 4; i++) {
//                    TestThread testThread = new TestThread();
//                    testThread.setName("Thread " + i);
//                    testThread.start();
//                    TimeUnit.SECONDS.sleep(2);
//                }
            
        };
    }   
}
