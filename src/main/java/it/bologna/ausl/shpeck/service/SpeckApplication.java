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
import shpeck.service.workers.IMAPWorker;

/**
 *
 * @author Salo
 */
@SpringBootApplication
@EnableJpaRepositories({"it.bologna.ausl.shpeck.repository"})
@EntityScan("it.bologna.ausl.model.entities")
public class SpeckApplication {
    /**
     * Punto di partenza dell'applicazione
     */
       
    private static final Logger log = LoggerFactory.getLogger(SpeckApplication.class);
    
    @Autowired
    PecRepository pr;
    
    @Autowired
    PecProviderRepository ppr;
    
    public static void main(String[] args) {
        SpringApplication.run(SpeckApplication.class, args);        
    }
    
    @Bean
    public void shpeck() {
        log.info("SHPECK-SERVICE AVVIATO -> " + new Date());
        ArrayList<Pec> list = (ArrayList) pr.findAll();
        for (Pec pec : list) {
            log.info(pec.getDescrizione() + ":");
                try {
                    if(pec.getIdPecProvider() != null)
                    {
                        log.info("PRENDO PECPROV " + pec.getIdPecProvider());
//                        PecProvider pp = pec.getIdPecProvider();
//                        log.info(pp.toString());
//                        log.info("\t provider " + pp.getDescrizione());
//                        log.info("\t host " + pp.getHost());
//                        log.info("\t protocol " + pp.getProtocol());
//                        log.info("\t port" + pp.getPort());
                        log.info("****");
                    }
                    else
                        log.info("EHI! NON ESISTE IL PROVIDER PER " + pec.getDescrizione() );
                } catch (Exception e) {
                    log.error("OHIO !!!! ERRORE " + e.toString());
                    e.printStackTrace();
                }
        }
        log.info("##############################");
        log.info("FINNITTTO.... -> " + new Date());
        log.info("##############################");
    };
    
}
