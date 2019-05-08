/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.bologna.ausl.shpeck.service.worker;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.baborg.PecProvider;
import it.bologna.ausl.model.entities.shpeck.Outbox;
import it.bologna.ausl.shpeck.service.manager.PecMessageStoreManager;
import it.bologna.ausl.shpeck.service.manager.RecepitMessageStoreManager;
import it.bologna.ausl.shpeck.service.manager.RegularMessageStoreManager;
import it.bologna.ausl.shpeck.service.manager.SMTPManager;
import it.bologna.ausl.shpeck.service.repository.OutboxRepository;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import it.bologna.ausl.shpeck.service.transformers.MailMessage;
import it.bologna.ausl.shpeck.service.utils.ProviderConnectionHandler;
import it.bologna.ausl.shpeck.service.utils.SmtpConnectionHandler;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import javax.mail.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 *
 * @author Salo
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SMTPWorker implements Runnable {
        
    @Autowired
    PecRepository pecRepository;
    
    @Autowired
    OutboxRepository outboxRepository;
        
    @Autowired
    Semaphore messageSemaphore;
    
    @Autowired
    SmtpConnectionHandler smtpConnectionHandler;
    
    @Autowired
    SMTPManager smtpManager;

    private static final Logger log = LoggerFactory.getLogger(SMTPWorker.class);
    public static final int MESSAGE_POLICY_NONE = 0;
    public static final int MESSAGE_POLICY_BACKUP = 1;
    public static final int MESSAGE_POLICY_DELETE = 2;
    private String threadName;
    private Integer idPec;

    public SMTPWorker() {
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public Integer getIdPec() {
        return idPec;
    }

    public void setIdPec(Integer idPec) {
        this.idPec = idPec;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("SmptWorker::mailbox: " + threadName);
        log.info("START -> [" + Thread.currentThread().getName() + "]" + " idPec: [" + idPec + "]" + " time: " + new Date());
        try{
            // Prendo la pec
            Pec pec = pecRepository.findById(idPec).get();
            PecProvider pecProvider = pec.getIdPecProvider();
            // carico i messaggi con message_status 'TO_SEND'
            // prendo il provider
            // creo un'istanza del manager
            List<Outbox> messagesToSend = outboxRepository.findByIdPec(pec);
            if(messagesToSend != null && messagesToSend.size() > 0){
                smtpManager.buildSmtpManagerFromPec(pec);
                for (Outbox outbox : messagesToSend) {
                    smtpManager.sendMessage(outbox.getRawData());
                }
            }
            
            // ciclo i messaggi:
                // carico i raw_message con associato il messaggio
                // se tutto ok, salvo che è stato inviato:
                    // m.message_status sent
                // altrimenti setto m.message_status error
                // comunque aggiungo il raw tra quelli da caricare su mongo
        }
        catch(Exception e){
            log.error("Errore del thread " + Thread.currentThread().getName()  + "\n"
                    + "---> " + e.getMessage());
            e.printStackTrace();
        }
        log.info("STOP -> [" + Thread.currentThread().getName() + "]" + " idPec: [" + idPec + "]" + " time: " + new Date());
    }
}
