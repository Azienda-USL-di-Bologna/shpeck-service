/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package shpeck.service.workers;

import it.bologna.ausl.model.entities.baborg.Pec;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Salo
 */
public class IMAPWorker implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(IMAPWorker.class);
    private static Pec pec;

    public IMAPWorker(Pec p) {
        this.pec = p;
    }
    
    @Override
    public void run() {
        log.info("Partito IMAPWorker per " + pec.getDescrizione() + " - ore " + new Date().toString());
        
        log.info("Esco dal worker");
    }
    
}
