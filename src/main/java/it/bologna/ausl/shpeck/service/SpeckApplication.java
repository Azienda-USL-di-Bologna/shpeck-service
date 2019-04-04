package it.bologna.ausl.shpeck.service;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.baborg.PecProvider;
import it.bologna.ausl.shpeck.repository.PecProviderRepository;
import it.bologna.ausl.shpeck.repository.PecRepository;

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
import it.bologna.ausl.shpeck.worker.IMAPWorker;
import it.bologna.ausl.shpeck.worker.TestThread;
import it.bologna.ausl.shpeck.worker.ShutdownThread;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 *
 * @author Salo
 */
@SpringBootApplication(scanBasePackages = "it.bologna.ausl.shpeck.worker")
@EnableJpaRepositories({"it.bologna.ausl.shpeck.repository"})
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
    TestThread testThread;    
            
    public static void main(String[] args) {
        SpringApplication.run(SpeckApplication.class, args); 
    }
    
    
    @Bean
    public CommandLineRunner schedulingRunner() {
        return new CommandLineRunner() {
            public void run(String... args) throws Exception {
               
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
            }
        };
    }   
}
