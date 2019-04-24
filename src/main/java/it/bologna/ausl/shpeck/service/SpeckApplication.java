package it.bologna.ausl.shpeck.service;

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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    
//    @Autowired
//    TestThread testThread;  
       

    public static void main(String[] args) {
        SpringApplication.run(SpeckApplication.class, args); 
    }
 
    
    @Bean
    public CommandLineRunner schedulingRunner() {
        
        return new CommandLineRunner() {

            public void run(String... args) throws Exception {
                IMAPWorker imapWorker = (IMAPWorker) context.getBean(IMAPWorker.class);
                imapWorker.setThreadName("worker1");
                log.info("worker_object: " + imapWorker.toString());

                scheduledThreadPoolExecutor.scheduleWithFixedDelay(imapWorker, 3, 3, TimeUnit.SECONDS);
                
                Runtime.getRuntime().addShutdownHook(shutdownThread);
            }
//                
//                if (!messages.isEmpty()) {
//                    log.debug("got messages !");
//                }
                
//               scheduledThreadPoolExecutor.scheduleWithFixedDelay(new IMAPWorker(), 3, 10, TimeUnit.SECONDS);
//               
              
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
