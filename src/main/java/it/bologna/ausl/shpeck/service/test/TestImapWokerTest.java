///*
// * To change this license header, choose License Headers in Project Properties.
// * To change this template file, choose Tools | Templates
// * and open the template in the editor.
// */
//package it.bologna.ausl.shpeck.service.test;
//
//import it.bologna.ausl.model.entities.baborg.Pec;
//import it.bologna.ausl.model.entities.baborg.PecProvider;
//import it.bologna.ausl.shpeck.service.repository.PecProviderRepository;
//import it.bologna.ausl.shpeck.service.repository.PecRepository;
//import java.util.List;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.boot.autoconfigure.domain.EntityScan;
//import org.springframework.context.annotation.Bean;
//import org.springframework.core.annotation.Order;
//import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
//
///**
// *
// * @author Salo
// */
//
//@SpringBootApplication(scanBasePackages = "it.bologna.ausl.shpeck")
//@EnableJpaRepositories({"it.bologna.ausl.shpeck.service.repository"})
//@EntityScan("it.bologna.ausl.model.entities")
//public class TestImapWokerTest {
//
//    /**
//     * @param args the command line arguments
//     */
//    
//    @Autowired
//    public PecRepository pr;
////    
//    @Autowired
//    public PecProviderRepository ppr;
//            
//    private static final Logger log = LoggerFactory.getLogger(TestImapWokerTest.class);
//    
//    public static void main(String[] args) {
//        SpringApplication.run(TestImapWokerTest.class, args); 
//    }
//    
//    
////    @Bean
////    public CommandLineRunner schedulingRunner() {
////        return new CommandLineRunner() {
////            public void run(String... args) throws Exception {
////                log.info("Inizio");
////                List<Pec> pecs = pr.findAll();
////                for (Pec pec : pecs) {
////                    WorkerTest worker = new WorkerTest(pec, ppr);
////                    worker.run();
////                }
////            }
////        };
////    }   
//    
// 
//}