/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.bologna.ausl.shpeck.service.test;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.baborg.PecProvider;
import it.bologna.ausl.shpeck.service.repository.PecProviderRepository;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author Salo
 */
public class WorkerTest {
    
    
    public PecRepository pr;
    
    
    public PecProviderRepository ppr;
    
    private static final Logger log = LoggerFactory.getLogger(WorkerTest.class);
    
    private Pec pec;
    
    private PecProvider provider;
    
    public WorkerTest(Pec p, PecProviderRepository ppr){
        log.info("WorkerTest constructor with Pec " + p.getIndirizzo());
        this.pec = p;
        this.ppr = ppr;
    }
    
    public void run(){
        log.info("RUnno");
        log.info("Carico pec provider");
        this.provider = ppr.findById(this.pec.getIdPecProvider().getId()).get();
        
    }
}
