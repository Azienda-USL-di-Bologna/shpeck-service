package it.bologna.ausl.shpeck.service.manager;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.configurazione.Applicazione;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.MessageAddress;
import it.bologna.ausl.model.entities.shpeck.Recepit;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.RecepitRepository;
import java.util.ArrayList;
import it.bologna.ausl.model.entities.shpeck.Address;
import it.bologna.ausl.model.entities.shpeck.MessageExtension;
import it.bologna.ausl.model.entities.shpeck.RawMessage;
import it.bologna.ausl.model.entities.shpeck.UploadQueue;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckIllegalRecepitException;
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
import java.util.Set;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import it.bologna.ausl.shpeck.service.repository.AddressRepository;
import it.bologna.ausl.shpeck.service.repository.MessageExtensionRepository;
import it.bologna.ausl.shpeck.service.transformers.PecMessage;
import java.time.ZonedDateTime;
import java.util.Optional;
import javax.mail.internet.MimeMessage;

/**
 *
 * @author spritz
 */
@Component
public class StoreManager implements StoreInterface {

    private static final Logger log = LoggerFactory.getLogger(StoreManager.class);

    private Message.InOut inout = Message.InOut.IN;

    private Applicazione applicazione;

    @Autowired
    MessageRepository messageRepository;
    @Autowired
    MessageExtensionRepository messageExtensionRepository;

    @Autowired
    MessageAddressRepository messageAddressRepository;

    @Autowired
    RecepitRepository recepitRepository;

    @Autowired
    AddressRepository addessRepository;

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
                try {
                    message.setReceiveTime(mailMessage.getReceiveDate());
                } catch (Exception ex) {
                    message.setReceiveTime(mailMessage.getSendDate());
                }
            } else {
                message.setReceiveTime(ZonedDateTime.now());
            }
            if (mailMessage.getReceiveDateProvider() != null) {
                message.setReceiveDateProvider(mailMessage.getReceiveDateProvider());
            }
        } else {
            message.setMessageStatus(Message.MessageStatus.TO_SEND);
            message.setInOut(Message.InOut.OUT);
            message.setIsPec(false);
            message.setMessageType(Message.MessageType.MAIL);
        }

        // se il message è PEC non calcolare gli allegati
        if (!PecMessage.isPecMessage(mailMessage.getOriginal())) {
            try {
//            message.setAttachmentsNumber(EmlHandlerUtils.getAttachments(mailMessage.getOriginal(), null).length);
                message.setAttachmentsNumber(MessageBuilder.messageHasAttachment(mailMessage.getOriginal()));
            } catch (ShpeckServiceException ex) {
                log.error("Errore dello stabilire il numero di allegati", ex);
                message.setAttachmentsNumber(0);
            }
        } else {
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

    public void updateMessageExtension(Message message,
            MailMessage mailMessage) throws StoreManagerExeption {
        try {
            MessageExtension messageExtension = messageExtensionRepository
                    .findById(message.getId()).get();

            javax.mail.Address[] from = retrieveFromAddresses(mailMessage);

            if (from != null) {
                String stringAddressToSave = "";
                for (javax.mail.Address address : from) {

                    if (address.toString() != null && !address.toString().equals("")) {
                        stringAddressToSave
                                += (stringAddressToSave.length() != 0 ? "; " : "") // se non è vuoto aggiungo ;
                                + address.toString();
                    }

                    InternetAddress internetAddress = (InternetAddress) address;
                    if (internetAddress.getPersonal() != null
                            && !internetAddress.getPersonal().equals("")
                            && !internetAddress.getPersonal().equals(stringAddressToSave)) {
                        stringAddressToSave
                                += (stringAddressToSave.length() != 0 ? "; " : "") // se non è vuoto aggiungo ;
                                + internetAddress.getPersonal();
                    }
                }
                if (!stringAddressToSave.equals("")) {
                    messageExtension.setFullAddressFrom(stringAddressToSave);
                    messageExtension = messageExtensionRepository.save(messageExtension);
                }
            }

        } catch (Throwable ex) {
            log.error("Impossibible aggiornare il MessageExtesione", ex);
        }
    }

    @Override
    public Recepit storeRecepit(Recepit recepit) {
        return recepitRepository.save(recepit);
    }

    @Override
    public Message getMessageFromDb(Message message) {

        Message messaggioPresente = null;

        List<Message> messaggiPresenti = messageRepository.findByUuidMessageAndIdPecAndMessageType(message.getUuidMessage(), message.getIdPec(), message.getMessageType().toString());

        if (messaggiPresenti != null && messaggiPresenti.size() > 0) {
            if (message.getMessageType() != Message.MessageType.MAIL) {
                log.info("messaggio trovato su database");
                messaggioPresente = messaggiPresenti.get(0);
            } else {
                for (Message m : messaggiPresenti) {
                    // devo trovare quella che ha idRelated nullo
                    if (m.getIdRelated() == null) {
                        log.info("messaggio già presente su database");
                        messaggioPresente = m;
                        break;
                    }
                }
            }
        } else {
            log.info("messaggio NON presente su database");
        }
        return messaggioPresente;
    }

    @Override
    public boolean isValidRecord(Message message) {
        boolean res = false;

        if (message.getIdMessagePecgw() != null) {
            log.info("Messaggio importato... è OK per definizione: VALIDATO");
            return true;
        }

        // deve avere i riferimenti al repository
        if ((message.getUuidRepository() != null && !message.getUuidRepository().equals(""))
                && (message.getPathRepository() != null && !message.getPathRepository().equals(""))) {
            // se message != MAIL è sufficiente vedere che uuidRepository e pathRepository siano != NULL
            // se message == MAIL non basta:
            // guardo se ha una cartella associata; se questo non è vero, per essere valido allora deve avere righe su krint
            if (message.getMessageType() == Message.MessageType.MAIL) {
                log.info("E' una mail normale, quindi vediamo se si trova in una cartella...");
                int quanteFolder = messageRepository.getMessagesFolderCount(message.getId());
                if (quanteFolder > 0) {
                    log.info("E' in una qualche folder, quindi tutto a posto!");
                    return true;
                } else {
                    // controllo se ha righe su krint
                    Integer rowNumber = 0;
                    try {
                        rowNumber = messageRepository.getRowFromKrint(String.valueOf(message.getId()));
                        log.debug("numero di righe di log su krint: " + rowNumber);
                        if (rowNumber <= 0) {
                            log.error("record di message con id: " + message.getId() + " non valido");
                            log.info("Ultima spiaggia: è settata come seen?" + message.getSeen());
                            res = message.getSeen();
                        } else {
                            log.info("record presenti in krint quindi il messaggio è stato gestito");
                            res = true;
                        }
                    } catch (Throwable e) {
                        log.debug("non esistono righe su krint per il messaggio con id: " + message.getId());
                        res = false;
                    }
                }
            } else {
                res = true;
            }
        }
        return res;
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

    public void storeMessagesAddresses(Message message, HashMap addresses) throws ShpeckServiceException {

        log.info("--- inizio storeMessagesAddress ---");
        log.debug("analisi indirizzi FROM");
        ArrayList<Address> list = (ArrayList<Address>) addresses.get("from");
        if (list != null && list.size() > 0) {
            log.debug("Ciclo gli indirizzi FROM e li salvo su messages_addresses");
            for (Address address : list) {
                MessageAddress ma = storeMessageAddress(message, address, MessageAddress.AddressRoleType.FROM);
                log.debug((ma == null ? "messageAddress (" + message.getId() + "," + address.getId() + ", FROM) " + "già presente" : "messageAddress (" + message.getId() + "," + address.getId() + ", FROM) " + "inserito"));
            }
        }

        list = (ArrayList<Address>) addresses.get("to");
        if (list != null && list.size() > 0) {
            log.debug("Ciclo gli indirizzi TO e li salvo su messages_addresses");
            for (Address address : list) {
                MessageAddress ma = storeMessageAddress(message, address, MessageAddress.AddressRoleType.TO);
                log.debug((ma == null ? "messageAddress (" + message.getId() + "," + address.getId() + ", TO) " + "già presente" : "messageAddress (" + message.getId() + "," + address.getId() + ", TO) " + "inserito"));
            }
        }
        list = (ArrayList<Address>) addresses.get("cc");
        if (list != null && list.size() > 0) {
            log.debug("Ciclo gli indirizzi CC e li salvo su messages_addresses");
            for (Address address : list) {
                MessageAddress ma = storeMessageAddress(message, address, MessageAddress.AddressRoleType.CC);
                log.debug((ma == null ? "messageAddress (" + message.getId() + "," + address.getId() + ", CC) " + "già presente" : "messageAddress (" + message.getId() + "," + address.getId() + ", CC) " + "inserito"));
            }
        }

        list = (ArrayList<Address>) addresses.get("replyTo");
        if (list != null && list.size() > 0) {
            ArrayList<Address> froms = (ArrayList<Address>) addresses.get("from");
            log.debug("Ciclo gli indirizzi REPLY_TO e li salvo su messages_addresses");
            for (Address address : list) {
                if (froms == null || froms.isEmpty()) {
                    log.debug("Il messageAddress di tipo FROM non c'è, quindi setto quello di tipo in REPLY_TO come FROM");
                    MessageAddress ma = storeMessageAddress(message, address, MessageAddress.AddressRoleType.FROM);
                    if (froms == null) {
                        froms = new ArrayList<Address>();
                    }
                    froms.add(address);
                    log.debug("Come FROM del messageAddress(" + message.getId() + "è stato salvato l'address del REPLY_TO" + address.getId());
                } else if (!froms.stream().anyMatch(a -> a.getMailAddress().equals(address.getMailAddress()))) {
                    MessageAddress ma = storeMessageAddress(message, address, MessageAddress.AddressRoleType.REPLY_TO);
                    log.debug((ma == null ? "messageAddress (" + message.getId() + "," + address.getId() + ", REPLY_TO) " + "già presente" : "messageAddress (" + message.getId() + "," + address.getId() + ", REPLY_TO) " + "inserito"));
                }

            }
        }
        log.info("--- fine storeMessagesAddress ---");
    }

    public List<Address> saveAndReturnAddresses(javax.mail.Address[] addresses, Map<String, Address.RecipientType> map) {
        ArrayList<Address> list = new ArrayList<>();

        try {
            if (map != null) {
                // caso di calcolo destinatari
                log.debug("calcolo destinatari");
                map.forEach((key, value) -> {
                    log.debug("verifico presenza di " + key);
                    Address address = addessRepository.findByMailAddress(key.toLowerCase());

                    if (address == null) {
                        log.debug("indirizzo non trovato: lo salvo");
                        address = new Address();
                        address.setMailAddress(key.toLowerCase());
                        address.setRecipientType(Address.RecipientType.UNKNOWN);
                    } else {
                        log.debug("indirizzo trovato con id: " + key.toLowerCase());
                    }

                    if ((address.getRecipientType().equals(Address.RecipientType.UNKNOWN))) {
                        switch (value) {
                            case PEC:
                                address.setRecipientType(Address.RecipientType.PEC);
                                break;
                            case REGULAR_EMAIL:
                                address.setRecipientType(Address.RecipientType.REGULAR_EMAIL);
                                break;
                        }

                        try {
                            addessRepository.save(address);
                        } catch (Exception ex) {
                            log.error("Indirizzo già presente: " + address.getMailAddress(), ex);
                        }
                    }
                    list.add(address);
                });
            } else {
                // caso di calcolo mittenti, reply_to, cc
                log.debug("calcolo mittenti / cc / reply_to");
                for (int i = 0; i < addresses.length; i++) {
                    InternetAddress internetAddress = (InternetAddress) addresses[i];
                    Address address;
                    log.info("verifico presenza di " + internetAddress.getAddress());
                    address = addessRepository.findByMailAddress(internetAddress.getAddress().toLowerCase());

                    if (address == null) {
                        log.info("indirizzo non trovato: lo salvo");
                        address = new Address();
                        address.setMailAddress(internetAddress.getAddress().toLowerCase());
                        //address.setOriginalAddress(internetAddress.getPersonal());
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
                            log.error("Indirizzo già presente: " + address.getMailAddress().toLowerCase(), ex);
                        }
                    }
                    list.add(address);
                }
            }

            // aggiorna la tipologia di indirizzo (se PEc o REGULAR_MAIL) prendendo da XML della ricevuta la tipologia dei destinatari
            updateDestinatariType(map);
        } catch (Throwable e) {
            log.error("errore in saveAndReturnAddresses: ", e);
        }
        return list;
    }

    private void updateDestinatariType(Map<String, Address.RecipientType> map) {

        if (map != null) {
            Set<String> keys = map.keySet();
            ArrayList<String> list = new ArrayList<>();
            for (String key : keys) {
                list.add(key);
            }

            List<Address> addresses = addessRepository.getAddresses(list);

            if (!addresses.isEmpty()) {
                for (Address address : addresses) {
                    if ((address.getRecipientType().equals(Address.RecipientType.UNKNOWN))) {

                        switch (map.get(address.getMailAddress())) {
                            case PEC:
                                address.setRecipientType(Address.RecipientType.PEC);
                                break;
                            case REGULAR_EMAIL:
                                address.setRecipientType(Address.RecipientType.REGULAR_EMAIL);
                                break;
                        }
                    }
                }
                try {
                    addessRepository.saveAll(addresses);
                } catch (Exception e) {
                    log.error("errore updateDestinatariType: ", e);
                }
            }
        }
    }

    private javax.mail.Address[] retrieveFromAddresses(MailMessage mailMessage) throws StoreManagerExeption {
        javax.mail.Address[] from = mailMessage.getFrom();
        if (from == null) {
            try {
                from = InternetAddress.parseHeader(mailMessage.getOriginal().getHeader("From", ","), true);
            } catch (MessagingException ex) {
                log.error("unable to determine From address", ex);
                from = new InternetAddress[1];
                try {
                    from[0] = new InternetAddress(mailMessage.getOriginal().getHeader("From", ","));
                } catch (Throwable e) {
                    throw new StoreManagerExeption("errore nel settare un mittente già rotto in partenza", e);
                }

            }
        }
        return from;
    }

    public HashMap upsertAddresses(MailMessage mailMessage) throws StoreManagerExeption, ShpeckServiceException {

        boolean ricevutaConsegnaType = false;

        log.info("---Inizio upsertAddresses---");
        HashMap<String, ArrayList> map = new HashMap<>();
        log.debug("Verifico presenza di mittenti...");
        javax.mail.Address[] from = retrieveFromAddresses(mailMessage);

        if (from != null) {
            ArrayList<Address> fromArrayList;

            log.debug("Mittenti presenti, ciclo gli indirizzi FROM");
            fromArrayList = (ArrayList<Address>) saveAndReturnAddresses(from, null);
            // inserisco l'arraylist nella mappa con chiave 'from'
            map.put("from", fromArrayList);
            log.info("mittente presente");
        } else {
            log.info("Mittente non presente");
            throw new StoreManagerExeption("upsertAddresses: Mittente non presente o malformato");
        }

        // Se non ho neanche un destinatario devo lanciare l'errore il mestiere
        if (mailMessage.getTo() == null && mailMessage.getCc() == null && mailMessage.getReply_to() == null) {
            log.info("Destinatari non presenti");
            throw new StoreManagerExeption("upsertAddresses: Destinatari non presenti");
        }

        // se ricevuta di consegna (compreso errore) si va a prendere solo l'indirizzo associato alla ricevuta
        if (mailMessage.getType() == Message.MessageType.RECEPIT) {
            try {
                MimeMessage original = mailMessage.getOriginal();
                String ricevuta = original.getHeader("X-Ricevuta", "");
                switch (ricevuta) {
                    case "preavviso-errore-consegna":
                    case "errore-consegna":
                    case "avvenuta-consegna":
                        ricevutaConsegnaType = true;
                        break;
                    default:
                        log.debug("non riguarda le ricevute di consegna");
                        ricevutaConsegnaType = false;
                }

            } catch (MessagingException ex) {
                log.error("nel messaggio di ricevuta non esiste header X-Ricevuta", ex);
            }
        }

        if (ricevutaConsegnaType) {
            log.debug("Verifico presenza di tag consegna");
            Map<String, Address.RecipientType> destinatarioConsegna = MessageBuilder.getDestinatarioConsegnaType(mailMessage);
            String uuid = null;
            String address = null;
            log.debug("estrazione address...");
            for (String key : destinatarioConsegna.keySet()) {
                address = key;
                log.debug("address estratto: " + address);
            }

            log.debug("calcolo uuid del related");
            try {
                uuid = mailMessage.getOriginal().getHeader("X-Riferimento-Message-ID", "");
                log.debug("uuid del related. " + uuid);
            } catch (MessagingException ex) {
                log.error("X-Riferimento-Message-ID non presente nella ricevuta");
                throw new ShpeckIllegalRecepitException("X-Riferimento-Message-ID non presente nella ricevuta", ex);
            }

            if (uuid != null && address != null) {
                log.debug("inserimento dell'indirizzo nel TO");
                map.put("to", getAddressFromRecepit(uuid, address));
            }

        } else {
            log.info("Verifico presenza di destinatari TO");
            if (mailMessage.getTo() != null) {
                ArrayList<Address> toArrayList = new ArrayList<>();
                javax.mail.Address[] to = mailMessage.getTo();
                log.debug("ciclo gli indirizzi TO e se non presenti li salvo");
                Map<String, Address.RecipientType> destinatariType = MessageBuilder.getDestinatariType(mailMessage);
                toArrayList = (ArrayList<Address>) saveAndReturnAddresses(to, (destinatariType.isEmpty() ? null : destinatariType));
                //log.debug("Aggiungo l'array degli indirizzi to alla mappa con chiave 'to'");
                map.put("to", toArrayList);
            } else {
                log.info("destinatari TO non presenti");
            }
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
            log.debug("ciclo gli indirizzi reply_to e li salvo");
            replyArrayList = (ArrayList<Address>) saveAndReturnAddresses(replyTo, null);
            //log.debug("Aggiungo l'array degli indirizzi reply_to alla mappa con chiave 'replyTo'");
            map.put("replyTo", replyArrayList);
        } else {
            log.info("destinatari Reply_To non presenti");
        }
        log.info("--- Fine upsertAddresses ---");
        return map;
    }

    public ArrayList<Address> getAddressFromRecepit(String uuid, String mailAddress) {

        log.debug("inizio getAddressFromRecepit");
        ArrayList<Address> res = new ArrayList<>();

        log.debug("caricamento idAddress...");
        Integer idAddress = addessRepository.getIdAddressByUidMessageAndMailAddress(uuid, mailAddress);
        log.debug("idAddress: " + idAddress);

        Optional<Address> address = null;

        if (idAddress != null) {
            address = addessRepository.findById(idAddress);
        }

        if (address != null && address.isPresent()) {
            log.debug("inserimento di address nel risultato");
            res.add(address.get());
        } else {
            // controllo se esiste l'indirizzo (indipendentemente dall'associazione con uuidMessage)
            Address tmpAddress = addessRepository.findByMailAddress(mailAddress);

            // se indirizzo non esiste in tabella lo inserisco (vuol dire che non c'è mai stato questo indirizzo)
            if (tmpAddress == null) {
                log.debug("address non presente, viene creato e salvato");
                Address a = new Address();
                a.setMailAddress(mailAddress);
                a.setRecipientType(Address.RecipientType.PEC);
                res.add(addessRepository.save(a));
            } else {
                log.info("address già presente su database, non viene inserito");
                res.add(tmpAddress);
            }
        }

        log.debug("ritorno getAddressFromRecepit con res di dimensione: " + res.size());
        return res;
    }

    @Override
    public RawMessage storeRawMessage(Message message, String raw
    ) {
        // controllo se si deve usare istanza da DB oppure crearne uno nuovo
        RawMessage rm = null;
        RawMessage rawMessage = null;
        try {
            rm = rawMessageRepository.findByIdMessage(message);
            if (rm != null) {
                rawMessage = rm;
            }
        } catch (Throwable e) {
            log.info("non esiste un rawMessage presente in db, lo inserisco");
        }

        if (rm == null) {
            log.info("--- inizio storeRawMessage ---");
            rawMessage = new RawMessage();
            rawMessage.setIdMessage(message);
            rawMessage.setRawData(raw);
            log.debug("salvataggio del rawMessage...");
            rawMessage = rawMessageRepository.save(rawMessage);
            log.debug("rawMessage salvato");
        } else {
            log.info("--- inizio aggiornamento storeRawMessage ---");
            rawMessage.setIdMessage(message);
            rawMessage.setRawData(raw);
            log.debug("salvataggio del rawMessage...");
            rawMessage = rawMessageRepository.save(rawMessage);
            log.debug("rawMessage salvato");
        }
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
    public void insertToUploadQueue(Message message) {
        log.info("insertToUploadQueue: reperisco il raw_message del messaggio " + message.getId());
        RawMessage rawMessage = rawMessageRepository.findByIdMessage(message);
        log.info("RawMessage trovato " + rawMessage.getId());

        UploadQueue uploadQueue = null;
        log.info("... ma non è che ce l'ho già la riga in upload_queue?");
        uploadQueue = uploadQueueRepository.findByIdRawMessage(rawMessage);
        if (uploadQueue != null) {
            log.info("Trovato un upload_queue con id " + uploadQueue.getId());
            log.info("Ahh ma quindi l'avevo già spedito! allora a posto...");
            return;
        }
        log.info("No, quindi devo salvarlo");
        uploadQueue = new UploadQueue();
        log.info("setto l'id_raw_message " + rawMessage.getId());
        uploadQueue.setIdRawMessage(rawMessage);
        log.info("inserimento del rawMessage in upload_queue...");
        uploadQueueRepository.save(uploadQueue);
        log.info("inserimento in upload_queue avvenuto con successo");
        log.info("--- fine metodo storeRawMessageAndUploadQueue ---");
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
