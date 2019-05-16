package it.bologna.ausl.shpeck.service.manager;

import it.bologna.ausl.eml.handler.EmlHandlerUtils;
import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.configuration.Applicazione;
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
import it.bologna.ausl.shpeck.service.exceptions.StoreManagerExeption;
import it.bologna.ausl.shpeck.service.repository.ApplicazioneRepository;
import it.bologna.ausl.shpeck.service.repository.MessageAddressRepository;
import it.bologna.ausl.shpeck.service.repository.RawMessageRepository;
import it.bologna.ausl.shpeck.service.repository.UploadQueueRepository;
import it.bologna.ausl.shpeck.service.transformers.MailMessage;
import it.bologna.ausl.shpeck.service.transformers.StoreInterface;
import it.bologna.ausl.shpeck.service.utils.MessageBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class StoreManager implements StoreInterface {

    private static final Logger log = LoggerFactory.getLogger(StoreManager.class);

    private Message.InOut inout = Message.InOut.IN;

    private Applicazione applicazione;

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

    @Autowired
    ApplicazioneRepository applicazioneRepository;

    public StoreManager() {
    }

    public Applicazione getApplicazione() {
        return applicazione;
    }

    public void setApplicazione(Applicazione applicazione) {
        this.applicazione = applicazione;
    }

    @Override
    public Message createMessageForStorage(MailMessage mailMessage, Pec pec) {
        Message message = new Message();
        message.setUuidMessage(mailMessage.getId());
        message.setIdPec(pec);
        message.setSubject(mailMessage.getSubject() != null ? mailMessage.getSubject() : "");
        if (Message.InOut.IN == inout) {
            message.setMessageStatus(Message.MessageStatus.RECEIVED);
            message.setInOut(Message.InOut.IN);
            message.setIsPec(mailMessage.getIsPec());
            if (mailMessage.getSendDate() != null) {
                message.setReceiveDate(new java.sql.Timestamp(mailMessage.getSendDate().getTime()).toLocalDateTime());
            } else {
                message.setReceiveDate(new java.sql.Timestamp(new Date().getTime()).toLocalDateTime());
            }
        } else {
            message.setMessageStatus(Message.MessageStatus.TO_SEND);
            message.setInOut(Message.InOut.OUT);
            message.setIsPec(false);
            message.setMessageType(Message.MessageType.MAIL);
        }

        try {
            message.setAttachmentsNumber(EmlHandlerUtils.getAttachments(mailMessage.getOriginal(), null).length);
        } catch (MessagingException | IOException ex) {
            log.error("Errore dello stabilire il numero di allegati", ex);
            message.setAttachmentsNumber(0);
        }

        String inReplyTo = mailMessage.getInReplyTo();
        if (inReplyTo != null) {
            message.setInReplyTo(inReplyTo);
        }

        return message;
    }

    @Override
    public Message storeMessage(Message message) {
        return messageRepository.save(message);
    }

    @Override
    public Recepit storeRecepit(Recepit recepit) {
        return recepitRepository.save(recepit);
    }

    @Override
    public Message getMessageFromDb(Message message) {
        Message messaggioPresente = messageRepository.findByUuidMessageAndIdPecAndMessageType(message.getUuidMessage(), message.getIdPec(), message.getMessageType().toString());
        if (messaggioPresente != null) {
            log.info("Messaggio GIA' presente su database");
        } else {
            log.info("Messaggio NON presente su database");
        }

        return messaggioPresente;
    }

    public void insertRawMessage(Message message, String rawData) throws ShpeckServiceException {
        RawMessage rawMessage = new RawMessage();
        rawMessage.setIdMessage(message);
        rawMessage.setRawData(rawData);
        rawMessageRepository.save(rawMessage);
    }

    public MessageAddress storeMessageAddress(Message m, Address a, MessageAddress.AddressRoleType type) {

        MessageAddress res = null;
        MessageAddress recordAlreadyPresent = messageAddressRepository.findByIdMessageAndIdAddressAndAddressRole(m, a, type.toString());

        if (recordAlreadyPresent == null) {
            MessageAddress messageAddress = new MessageAddress();
            messageAddress.setIdAddress(a);
            messageAddress.setIdMessage(m);
            messageAddress.setAddressRole(type);

            res = messageAddressRepository.save(messageAddress);
        }

        return res;
    }

    public void storeMessagesAddresses(Message message, HashMap addresses) {
        log.info("---Entrato in storeMessagesAddress---");

        log.info("analisi indirizzi FROM");
        ArrayList<Address> list = (ArrayList<Address>) addresses.get("from");
        if (list != null && list.size() > 0) {
            log.info("Ciclo gli indirizzi FROM e li salvo su messages_addresses");
            for (Address address : list) {
                MessageAddress ma = storeMessageAddress(message, address, MessageAddress.AddressRoleType.FROM);
                log.info((ma == null ? "messageAddress (" + message.getId() + "," + address.getId() + ", FROM)" + "già presente" : "messageAddress (" + message.getId() + "," + address.getId() + ", FROM)" + "inserito"));
            }
        }

        list = (ArrayList<Address>) addresses.get("to");
        if (list != null && list.size() > 0) {
            log.info("Ciclo gli indirizzi TO e li salvo su messages_addresses");
            for (Address address : list) {
                MessageAddress ma = storeMessageAddress(message, address, MessageAddress.AddressRoleType.TO);
                log.info((ma == null ? "messageAddress (" + message.getId() + "," + address.getId() + ", TO)" + "già presente" : "messageAddress (" + message.getId() + "," + address.getId() + ", TO)" + "inserito"));
            }
        }
        list = (ArrayList<Address>) addresses.get("cc");
        if (list != null && list.size() > 0) {
            log.info("Ciclo gli indirizzi CC e li salvo su messages_addresses");
            for (Address address : list) {
                MessageAddress ma = storeMessageAddress(message, address, MessageAddress.AddressRoleType.CC);
                log.info((ma == null ? "messageAddress (" + message.getId() + "," + address.getId() + ", CC)" + "già presente" : "messageAddress (" + message.getId() + "," + address.getId() + ", CC)" + "inserito"));
            }
        }
        log.info("---Uscito da storeMessagesAddress---");
    }

    public List<Address> saveAndReturnAddresses(javax.mail.Address[] addresses, Map<String, Address.RecipientType> map) {
        ArrayList<Address> list = new ArrayList<>();
        for (int i = 0; i < addresses.length; i++) {
            InternetAddress internetAddress = (InternetAddress) addresses[i];
            Address address;
            log.info("verifico presenza di " + internetAddress.getAddress());
            address = addessRepository.findByMailAddress(internetAddress.getAddress());

            if (address == null) {
                log.info("indirizzo non trovato: lo salvo");
                address = new Address();
                address.setMailAddress(internetAddress.getAddress());
                address.setOriginalAddress(internetAddress.getPersonal());
                address.setRecipientType(Address.RecipientType.UNKNOWN);
            }

            if ((address.getRecipientType().equals(Address.RecipientType.UNKNOWN))) {
                if (map != null) {
                    switch (map.get(internetAddress.getAddress())) {
                        case PEC:
                            address.setRecipientType(Address.RecipientType.PEC);
                            break;
                        case REGULAR_EMAIL:
                            address.setRecipientType(Address.RecipientType.REGULAR_EMAIL);
                            break;
                    }
                }
                try {
                    addessRepository.save(address);
                } catch (Exception ex) {
                    log.error("Indirizzo già presente: " + address.getMailAddress());
                }
            }

            list.add(address);
        }
        return list;
    }

    public HashMap upsertAddresses(MailMessage mailMessage) throws StoreManagerExeption, ShpeckServiceException {
        log.info("---Inizio upsertAddresses---");
        HashMap<String, ArrayList> map = new HashMap<>();
        log.info("Verifico presenza di mittenti...");
        if (mailMessage.getFrom() != null) {
            ArrayList<Address> fromArrayList = new ArrayList<>();
            javax.mail.Address[] from = mailMessage.getFrom();
            log.info("Mittenti presenti, ciclo gli indirizzi FROM");
            fromArrayList = (ArrayList<Address>) saveAndReturnAddresses(from, null);
            // inserisco l'arraylist nella mappa con chiave 'from'
            map.put("from", fromArrayList);
            log.info("mittente presente");
        } else {
            log.info("Mittente non presente");
            throw new StoreManagerExeption("upsertAddresses: Mittente non presente");
        }

        // Se non ho neanche un destinatario devo lanciare l'errore il mestiere
        if (mailMessage.getTo() == null && mailMessage.getCc() == null && mailMessage.getReply_to() == null) {
            log.info("Destinatari non presenti");
            throw new StoreManagerExeption("upsertAddresses: Destinatari non presenti");
        }

        log.info("Verifico presenza di destinatari TO");
        if (mailMessage.getTo() != null) {
            ArrayList<Address> toArrayList = new ArrayList<>();
            javax.mail.Address[] to = mailMessage.getTo();
            log.info("ciclo gli indirizzi TO e se non presenti li salvo");
            Map<String, Address.RecipientType> recipientsType = MessageBuilder.getRecipientsType(mailMessage);
            toArrayList = (ArrayList<Address>) saveAndReturnAddresses(to, (recipientsType.isEmpty() ? null : recipientsType));
            //log.debug("Aggiungo l'array degli indirizzi to alla mappa con chiave 'to'");
            map.put("to", toArrayList);
        } else {
            log.info("destinatari TO non presenti");
        }

        log.info("Verifico presenza di destinatari CC");
        if (mailMessage.getCc() != null) {
            ArrayList<Address> ccArrayList = new ArrayList<>();
            javax.mail.Address[] cc = mailMessage.getCc();
            log.debug("ciclo gli indirizzi CC e se non presenti li salvo");
            ccArrayList = (ArrayList<Address>) saveAndReturnAddresses(cc, null);
            //log.debug("Aggiungo l'array degli indirizzi cc alla mappa con chiave 'cc'");
            map.put("cc", ccArrayList);
        } else {
            log.info("destinatari CC non presenti");
        }

        log.info("Verifico presenza di destinatari Reply_To");
        if (mailMessage.getReply_to() != null) {
            ArrayList<Address> replyArrayList = new ArrayList<>();
            javax.mail.Address[] replyTo = mailMessage.getReply_to();
            log.info("ciclo gli indirizzi reply_to e li salvo");
            replyArrayList = (ArrayList<Address>) saveAndReturnAddresses(replyTo, null);
            log.debug("Aggiungo l'array degli indirizzi reply_to alla mappa con chiave 'replyTo'");
            map.put("replyTo", replyArrayList);
        } else {
            log.info("destinatari Reply_To non presenti");
        }
        log.info("---Fine upsertAddresses---");
        return map;
    }

    @Override
    public RawMessage storeRawMessageAndUploadQueue(Message message, String raw) {
        log.info("Metodo storeRawMessage");
        RawMessage rawMessage = new RawMessage();
        rawMessage.setIdMessage(message);
        rawMessage.setRawData(raw);
        log.info("salvataggio del rawMessage...");
        rawMessage = rawMessageRepository.save(rawMessage);
        log.info("rawMessage salvato");

        UploadQueue uploadQueue = new UploadQueue();
        uploadQueue.setIdRawMessage(rawMessage);
        log.info("inserimento del rawMessage in upload_queue...");
        uploadQueueRepository.save(uploadQueue);
        log.info("inserimento in upload_queue avvenuto con successo");

        return rawMessage;
    }

    public Message.InOut getInout() {
        return inout;
    }

    public void setInout(Message.InOut inout) {
        this.inout = inout;
    }

    /**
     * Inserisce il messaggio raw nella coda di upload
     */
    @Override
    public void insertToUploadQueue(RawMessage raw) {
        // dal raw -> prendo il messaggio -> prendo la pec -> prendo l'azienda -> prendo il tipo di connessione repository
        // setto in un nuovo UploadQueue        
    }

    /**
     * Rimuove il messaggio dalla coda di upload e il raw messagge
     */
    @Override
    public void removeFromUploadQueue(UploadQueue uq) {
        // prendo il raw message in una variabile
        // elimino la riga di uploadqueue
        // elimino il raw message
    }

}
