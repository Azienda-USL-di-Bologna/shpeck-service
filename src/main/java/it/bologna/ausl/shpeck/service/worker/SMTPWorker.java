/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.bologna.ausl.shpeck.service.worker;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.baborg.PecProvider;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.Outbox;
import it.bologna.ausl.shpeck.service.exceptions.BeforeSendOuboxException;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.manager.PecMessageStoreManager;
import it.bologna.ausl.shpeck.service.manager.RecepitMessageStoreManager;
import it.bologna.ausl.shpeck.service.manager.RegularMessageStoreManager;
import it.bologna.ausl.shpeck.service.manager.SMTPManager;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.OutboxRepository;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import it.bologna.ausl.shpeck.service.transformers.MailMessage;
import it.bologna.ausl.shpeck.service.transformers.StoreResponse;
import it.bologna.ausl.shpeck.service.utils.MessageBuilder;
import it.bologna.ausl.shpeck.service.utils.ProviderConnectionHandler;
import it.bologna.ausl.shpeck.service.utils.SmtpConnectionHandler;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import javax.mail.MessagingException;
import javax.mail.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    MessageRepository messageRepository;
    
    @Autowired
    OutboxRepository outboxRepository;
        
    @Autowired
    Semaphore messageSemaphore;
    
    @Autowired
    SmtpConnectionHandler smtpConnectionHandler;
    
    @Autowired
    RegularMessageStoreManager regularMessageStoreManager;
    
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
        Thread.currentThread().setName("SmtpWorker::mailbox: " + threadName);
        log.info("START -> [" + Thread.currentThread().getName() + "]" + " idPec: [" + idPec + "]" + " time: " + new Date());
        try{
            // Prendo la pec
            Pec pec = pecRepository.findById(idPec).get();
            PecProvider pecProvider = pec.getIdPecProvider();
            // carico i messaggi con message_status 'TO_SEND'
            // prendo il provider
            // creo un'istanza del manager
            List<Outbox> messagesToSend = outboxRepository.findByIdPecAndIgnoreFalse(pec);
            if(messagesToSend != null && messagesToSend.size() > 0){
                smtpManager.buildSmtpManagerFromPec(pec);
                log.info("Numero messaggi : " + messagesToSend.size() + " --> ciclo...");
                for (Outbox outbox : messagesToSend) {
                    StoreResponse response = null;
                    try{
                        response = saveMessageAndUploadQueue(outbox);
                        Message m = response.getMessage();
                        log.info("Salvataggio eseguito: provo a inviare...");
                        boolean sent = smtpManager.sendMessage(outbox.getRawData());
                        if(!sent){
                            log.error("Errore nell'invio del messaggio: metadati già salvati: " + response.getMessage().toString());
                            log.error("Metto in stato di errore il messaggio " + m.getId());
                            m.setMessageStatus(Message.MessageStatus.ERROR);
                            log.error("setto l'outbox come da ignorare");
                            outbox.setIgnore(Boolean.TRUE);
                            outboxRepository.save(outbox);
                        }
                        else{
                            log.info("Messaggio inviato correttamente, setto il messaggio come spedito...");
                            m.setMessageStatus(Message.MessageStatus.SENT);
                            log.info("Stato settato, ora elimino da outbox...");
                            outboxRepository.delete(outbox);
                            log.info("Eliminato");
                        }  
                        log.info("Aggiorno lo stato di message a " + m.getMessageType().toString());
                        messageRepository.save(m);
                        log.info("Aggiornato");
                    }
                    catch (BeforeSendOuboxException e){
                        log.error("ERRORE: " + e.getMessage());
                    }
                    catch (Exception e){
                        log.error("Errore: " + e.getMessage());
                    }
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
    
    @Transactional(rollbackFor = Throwable.class)
    public StoreResponse saveMessageAndUploadQueue(Outbox outbox) throws ShpeckServiceException{
        log.info("Entrato in saveMessageAndUploadQueue...");
        StoreResponse storeResponse = null;
        try{
            log.info("Buildo il mailMessage dal raw");
            MailMessage mailMessage = new MailMessage(MessageBuilder.buildMailMessageFromString(outbox.getRawData()));
            regularMessageStoreManager.setInout(Message.InOut.OUT);
            regularMessageStoreManager.setPec(outbox.getIdPec());
            regularMessageStoreManager.setMailMessage(mailMessage);
            log.info("Salvo i metadati...");
            storeResponse = regularMessageStoreManager.store();
        }
        catch (ShpeckServiceException e){
            throw new BeforeSendOuboxException("Non sono riuscito a salvare i metadati del messaggio in outbox con id " + outbox.getId(), e);
        }
        return storeResponse;
    }
}
