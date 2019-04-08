package it.bologna.ausl.shpeck.service.worker;

import it.bologna.ausl.model.entities.baborg.Pec;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Salo
 */
public class IMAPWorker implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(IMAPWorker.class);
    //private static Pec pec;

//    public IMAPWorker(Pec p) {
//        this.pec = p;
//    }
    
    @Override
    public void run() {
        try{
            Thread.currentThread().setName("ImapWorker");
            log.info("Inizio thread: " + Thread.currentThread().getName() + " time: " + new Date());
            TimeUnit.SECONDS.sleep(5);
        } catch(Throwable e){
            e.printStackTrace();
        }
        
        log.info("Fine thread: " + Thread.currentThread().getName() + " time: " + new Date());
        
        
        
        //log.info("Partito IMAPWorker per " + pec.getDescrizione() + " - ore " + new Date().toString());
        
        //log.info("Esco dal worker");
    }
    
}
