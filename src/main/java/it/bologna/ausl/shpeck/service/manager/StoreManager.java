package it.bologna.ausl.shpeck.service.manager;

import it.bologna.ausl.eml.handler.EmlHandlerUtils;
import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.MessageAddress;
import it.bologna.ausl.model.entities.shpeck.Recepit;
import it.bologna.ausl.shpeck.service.repository.AddessRepository;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.RecepitRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import it.bologna.ausl.model.entities.shpeck.Address;
import it.bologna.ausl.model.entities.shpeck.RawMessage;
import it.bologna.ausl.model.entities.shpeck.UploadQueue;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.repository.MessageAddressRepository;
import it.bologna.ausl.shpeck.service.repository.RawMessageRepository;
import it.bologna.ausl.shpeck.service.repository.UploadQueueRepository;
import it.bologna.ausl.shpeck.service.transformers.MailMessage;
import it.bologna.ausl.shpeck.service.transformers.StoreInterface;
import java.util.HashMap;
import java.util.List;
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
    MessageAddressRepository messageAddressRepository;
    
    @Autowired
    RecepitRepository recepitRepository;
    
    @Autowired
    AddessRepository addessRepository;
    
    @Autowired
    RawMessageRepository rawMessageRepository;
    
    @Autowired
    UploadQueueRepository uploadQueueRepository;
   
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
        if(messaggioPresente != null)
            log.info("Messaggio GIA' trovato in casella");
        else
            log.info("Messaggio NON trovato in casella");
        
        return messaggioPresente != null;
    }
    
    public void insertRawMessage(Message message, String rawData) throws ShpeckServiceException {

        RawMessage rawMessage = new RawMessage();
        rawMessage.setIdMessage(message);
        rawMessage.setRawData(rawData);
        rawMessageRepository.save(rawMessage);       
    }
    
    public MessageAddress storeMessageAddress(Message m, Address a, MessageAddress.AddressRoleType type){
        MessageAddress messageAddress = new MessageAddress();
        messageAddress.setIdAddress(a);
        messageAddress.setIdMessage(m);
        messageAddress.setRecipientType(type);
        return messageAddressRepository.save(messageAddress);
    }
    
    public void storeMessagesAddresses(Message message, HashMap addresses){
        log.info("Entrato in storeMessagesAddress");
        log.info("Prendo gli indirizzi FROM");
        ArrayList<Address> list = (ArrayList<Address>) addresses.get("from");
        if(list!=null && list.size() > 0){
            log.info("Ciclo gli indirizzi FROM e li salvo su messages_addresses");
            for (Address address : list) {
                MessageAddress ma = storeMessageAddress(message, address, MessageAddress.AddressRoleType.FROM);
                log.info("Salvato message_address " + ma);
            }
        }
        
        list = (ArrayList<Address>) addresses.get("to");
        if(list!=null && list.size() > 0){
            log.info("Ciclo gli indirizzi TO e li salvo su messages_addresses");
            for (Address address : list) {
                MessageAddress ma = storeMessageAddress(message, address, MessageAddress.AddressRoleType.TO);
                log.info("Salvato message_address " + ma);
            }
        }
        list = (ArrayList<Address>) addresses.get("cc");
        if(list!=null && list.size() > 0){
            log.info("Ciclo gli indirizzi CC e li salvo su messages_addresses");
            for (Address address : list) {
                MessageAddress ma = storeMessageAddress(message, address, MessageAddress.AddressRoleType.CC);
                log.info("Salvato message_address " + ma);
            }
        }
            
    }
    
    public List<Address> saveAndReturnAddresses(javax.mail.Address[] addresses){
        ArrayList<Address> list = new ArrayList<Address>();
        for (int i = 0; i < addresses.length; i++){
            InternetAddress internetAddress = (InternetAddress) addresses[i];
                Address address = new Address();
                log.info("verifico presenza di " + internetAddress.getAddress());
                address = addessRepository.findByMailAddress(internetAddress.getAddress());
                if(address == null){
                    log.info("indirizzo non trovato: lo salvo");
                    address = new Address();
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
                log.info("Aggiungo indirizzo all'array da tornare");
                list.add(address);
            }
        return list;
    }

    
    public HashMap upsertAddresses(MailMessage mailMessage){
        log.info("Entrato in upsertAddresses");
        HashMap<String,ArrayList> map = new HashMap<String,ArrayList>();
        log.info("Verifico presenza di mittenti");
        if(mailMessage.getFrom() != null){
            ArrayList<Address> fromArrayList = new ArrayList<Address>();
            javax.mail.Address[] from = mailMessage.getFrom();
            log.info("ciclo gli indirizzi from e li salvo");
            fromArrayList = (ArrayList<Address>) saveAndReturnAddresses(from);
            // inserisco l'arraylist nella mappa con chiave 'from'
            if(fromArrayList.size() > 0){
                log.info("FROM: " + fromArrayList.toString());
                log.info("Aggiungo l'array degli indirizzi from alla mappa con chiave 'from'");
                map.put("from", fromArrayList);
            }
            else
                log.error("ATTENZIONE: PROBLEMI CON LA GESTIONE DEGLI INDIRIZZI FROM");
        }
        
        log.info("Verifico presenza di destinatari TO");
        if(mailMessage.getTo() != null){     
            ArrayList<Address> toArrayList = new ArrayList<Address>();
            javax.mail.Address[] to = mailMessage.getTo();
            log.info("ciclo gli indirizzi to e li salvo");
            toArrayList = (ArrayList<Address>) saveAndReturnAddresses(to);
            if(toArrayList.size() > 0){
                log.info("TO: " + toArrayList.toString());
                log.info("Aggiungo l'array degli indirizzi to alla mappa con chiave 'to'");
                map.put("to", toArrayList);
            }
            else
                log.error("ATTENZIONE: PROBLEMI CON LA GESTIONE DEGLI INDIRIZZI TO");
        }
        
        log.info("Verifico presenza di destinatari CC");
        if(mailMessage.getCc() != null){
            ArrayList<Address> ccArrayList = new ArrayList<Address>();
            javax.mail.Address[] cc = mailMessage.getCc();
            log.info("ciclo gli indirizzi cc e li salvo");
            ccArrayList = (ArrayList<Address>) saveAndReturnAddresses(cc);
            if(ccArrayList.size() > 0){
                log.info("CC: " + ccArrayList.toString());
                log.info("Aggiungo l'array degli indirizzi cc alla mappa con chiave 'cc'");
                map.put("cc", ccArrayList);
            }
            else
                log.error("ATTENZIONE: PROBLEMI CON LA GESTIONE DEGLI INDIRIZZI CC");
        }
        
        log.info("Verifico presenza di destinatari Reply_To");
        if(mailMessage.getReply_to() != null){
            ArrayList<Address> replyArrayList = new ArrayList<Address>();
            javax.mail.Address[] replyTo = mailMessage.getReply_to();
            log.info("ciclo gli indirizzi reply_to e li salvo");
            replyArrayList = (ArrayList<Address>) saveAndReturnAddresses(replyTo);
            if(replyArrayList.size() > 0){
                log.info("REPLY_TO: " + replyArrayList.toString());
                log.info("Aggiungo l'array degli indirizzi reply_to alla mappa con chiave 'replyTo'");
                map.put("replyTo", replyArrayList);
            }
            else
                log.error("ATTENZIONE: PROBLEMI CON LA GESTIONE DEGLI INDIRIZZI REPLY_TO");
        }
        log.info("Ritorno la mappa " +  map.toString());
        return map;
    }

    @Override
    public RawMessage storeRawMessageAndUploadQueue(Message message, String raw) {
        log.info("Metodo storeRawMessage");
        RawMessage rawMessage = new RawMessage();
        rawMessage.setIdMessage(message);
        rawMessage.setRawData(raw);
        log.info("salvataggio del raw");
        rawMessage = rawMessageRepository.save(rawMessage);
        
        UploadQueue uploadQueue = new UploadQueue();
        uploadQueue.setIdRawMessage(rawMessage);
        uploadQueueRepository.save(uploadQueue);
        
        return rawMessage;
    }
}
