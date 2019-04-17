package it.bologna.ausl.shpeck.service.transformers;

import it.bologna.ausl.eml.handler.EmlHandlerUtils;
import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.Recepit;
import it.bologna.ausl.shpeck.service.repository.AddessRepository;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.RecepitRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import it.bologna.ausl.model.entities.shpeck.Address;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;


/**
 *
 * @author spritz
 */
@Component
//@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class StoreManager implements StoreInterface{  
    private static final Logger log = LoggerFactory.getLogger(StoreManager.class);
    
    @Autowired
    MessageRepository messageRepository;
    
    @Autowired
    RecepitRepository recepitRepository;
    
    @Autowired
    AddessRepository addessRepository;
   
    public StoreManager() {
    }
    
    @Override
    public Message createMessageForStorage(MailMessage mailMessage, Pec pec, boolean isAccettazione) {
        Message message = new Message();
        message.setUuidMessage(mailMessage.getId());
        message.setIdPec(pec);
        message.setSubject(mailMessage.getSubject()!=null ? mailMessage.getSubject() : "");
        message.setMessageStatus(Message.MessageStatus.RECEIVED);
        message.setInOut(Message.InOut.IN);
        message.setIsPec(mailMessage.getIsPec());

        try {
            message.setAttachmentsNumber(EmlHandlerUtils.getAttachments(mailMessage.getOriginal(), null).length);
        } catch (MessagingException | IOException ex) {
            log.error("Errore dello stabilire il numero di allegati", ex);
            message.setAttachmentsNumber(0);
        }
        if (mailMessage.getSendDate() != null) {
            message.setReceiveDate(new java.sql.Timestamp(mailMessage.getSendDate().getTime()).toLocalDateTime());
        } else {
            message.setReceiveDate(new java.sql.Timestamp(new Date().getTime()).toLocalDateTime());
        }
        
        return message;
    }
    
    @Override
    public Message storeMessage(Message message){
        return messageRepository.save(message);
    }
    
    @Override
    public Recepit storeRecepit(Recepit recepit) {
        return recepitRepository.save(recepit);
    }
    
    @Override
    public boolean isPresent(Message message){
        Message messaggioPresente = messageRepository.findByUuidMessageAndIdPecAndMessageType(message.getUuidMessage(), message.getIdPec(), message.getMessageType().toString());
        return messaggioPresente != null;
    }
    
    public void upsertAddresses(MailMessage mailMessage){
        if(mailMessage.getFrom() != null){   
            javax.mail.Address[] from = mailMessage.getFrom();
            for (int i = 0; i < from.length; i++) {
                InternetAddress internetAddress = (InternetAddress) from[i];
                Address address = new Address();
                address.setMailAddress(internetAddress.getAddress());
                address.setOriginalAddress(internetAddress.getPersonal());
                address.setRecipientType(Address.RecipientType.UNKNOWN);
                try{
                    addessRepository.save(address);
                }
                catch(Exception ex){
                    log.error("Indirizzo già presente: " + address.getMailAddress());
                }
            }
        }
        
        if(mailMessage.getTo() != null){            
            javax.mail.Address[] to = mailMessage.getTo();
            for (int i = 0; i < to.length; i++) {
                InternetAddress internetAddress = (InternetAddress) to[i];
                Address address = new Address();
                address.setMailAddress(internetAddress.getAddress());
                address.setOriginalAddress(internetAddress.getPersonal());
                address.setRecipientType(Address.RecipientType.UNKNOWN);
                try{
                    addessRepository.save(address);
                }
                catch(Exception ex){
                    log.error("Indirizzo già presente: " + address.getMailAddress());
                }
            }
        }
        
        if(mailMessage.getCc() != null){            
            javax.mail.Address[] cc = mailMessage.getCc();
            for (int i = 0; i < cc.length; i++) {
                InternetAddress internetAddress = (InternetAddress) cc[i];
                Address address = new Address();
                address.setMailAddress(internetAddress.getAddress());
                address.setOriginalAddress(internetAddress.getPersonal());
                address.setRecipientType(Address.RecipientType.UNKNOWN);
                try{
                    addessRepository.save(address);
                }
                catch(Exception ex){
                    log.error("Indirizzo già presente: " + address.getMailAddress());
                }
            }
        }
        
        if(mailMessage.getReply_to() != null){
            javax.mail.Address[] replyTo = mailMessage.getReply_to();
            for (int i = 0; i < replyTo.length; i++) {
                InternetAddress internetAddress = (InternetAddress) replyTo[i];
                Address address = new Address();
                address.setMailAddress(internetAddress.getAddress());
                address.setOriginalAddress(internetAddress.getPersonal());
                address.setRecipientType(Address.RecipientType.UNKNOWN);
                try{
                    addessRepository.save(address);
                }
                catch(Exception ex){
                    log.error("Indirizzo già presente: " + address.getMailAddress());
                }
            }
        }
    }
}
