/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.bologna.ausl.shpeck.test;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.baborg.PecProvider;
import it.bologna.ausl.shpeck.repository.PecProviderRepository;
import it.bologna.ausl.shpeck.repository.PecRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 * @author Salo
 */
public class WorkerTest {
    @Autowired
    public PecRepository pr;
    
    @Autowired
    public PecProviderRepository ppr;
    
    private static final Logger log = LoggerFactory.getLogger(WorkerTest.class);
    
    private Pec pec;
    
    private PecProvider provider;
    
    public WorkerTest(Pec p){
        log.info("WorkerTest constructor with Pec " + p.getIndirizzo());
        this.pec = p;
    }
    
    public void run(){
        log.info("RUnno");
        log.info("Carico pec provider");
        this.provider = ppr.findById(this.pec.getIdPecProvider().getId()).get();
        
    }
}
