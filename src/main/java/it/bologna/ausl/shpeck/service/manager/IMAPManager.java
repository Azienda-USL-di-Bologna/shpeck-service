package it.bologna.ausl.shpeck.service.manager;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import com.sun.mail.imap.IMAPStore;
import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import it.bologna.ausl.shpeck.service.repository.ReportRepository;
import it.bologna.ausl.shpeck.service.transformers.MailMessage;
import it.bologna.ausl.shpeck.service.utils.Diagnostica;
import it.bologna.ausl.shpeck.service.utils.MessageBuilder;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.search.AndTerm;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.ReceivedDateTerm;
import javax.mail.search.SearchTerm;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 *
 * @author spritz
 */
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class IMAPManager {

    static Logger log = LoggerFactory.getLogger(IMAPManager.class);

    @Autowired
    PecRepository pecRepository;

    @Autowired
    ReportRepository reportRepository;

    @Autowired
    StoreManager storeManager;

    @Autowired
    Diagnostica diagnostica;

    @Value("${mailbox.backup-folder}")
    String BACKUP_FOLDER_NAME;

    @Value("${mailbox.inbox-folder}")
    String INBOX_FOLDER_NAME;
    @Value("${mailbox.backup-source-folder}")
    String BACKUP_SOURCE_FOLDER;

    @Value("${imap.reset-lastuid-minutes}")
    Integer resetLastuidMinutes;

    private String mailbox;
    private IMAPStore store;
    private long lastUID;
    private long lastUIDToConsider;
    private IMAPFolder inbox;
    IMAPFolder workingFolder = null;

    public IMAPManager() {
        lastUID = 0;
        lastUIDToConsider = Long.MAX_VALUE;

    }

    public String getMailbox() {
        return mailbox;
    }

    public void setMailbox(String mailbox) {
        this.mailbox = mailbox;
    }

    public IMAPManager(IMAPStore store) {
        this.store = store;
    }

    public long getLastUIDToConsider() {
        return lastUIDToConsider;
    }

    public void setLastUIDToConsider(long lastUIDToConsider) {
        this.lastUIDToConsider = lastUIDToConsider;
    }

    public IMAPManager(IMAPStore store, long lastUID) {
        this(store);
        this.lastUID = lastUID;
        this.lastUIDToConsider = Long.MAX_VALUE;
    }

    public IMAPStore getStore() {
        return store;
    }

    public void setStore(IMAPStore store) {
        this.store = store;
    }

    public long getLastUID() {
        return lastUID;
    }

    public void setLastUID(long lastUID) {
        this.lastUID = lastUID;
    }

    public IMAPFolder getInbox() {
        return inbox;
    }

    public void setInbox(IMAPFolder inbox) {
        this.inbox = inbox;
    }

    public FetchProfile getNewFetchProfile() {
        /**
         * FetchProfile elenca gli attributi del messaggio che si desidera
         * precaricare dal server
         */

        log.info("Faccio un get di un nuovo FetchProfile");
        FetchProfile fetchProfile = new FetchProfile();

        // ENVELOPE (=busta) è un insieme di attributi comuni a un messaggio (es. From, To, Cc, Bcc, ReplyTo, Subject and Date...)
        fetchProfile.add(FetchProfile.Item.ENVELOPE);
        fetchProfile.add("X-Trasporto");
        fetchProfile.add("X-Riferimento-Message-ID");
        return fetchProfile;
    }

    /**
     * *
     * Ottiene tutte le cartelle presenti nella casella del provider
     *
     * @return array di Folder
     * @throws ShpeckServiceException
     */
    public Folder[] getFolders() throws ShpeckServiceException {
        try {
            if (store == null || !store.isConnected()) {
                this.store.connect();
            }

            return store.getDefaultFolder().list();

        } catch (Throwable e) {
            log.error("errore durante il recupero delle folder da imap server " + store.getURLName().toString(), e);
            throw new ShpeckServiceException("errore durante il recupero delle folder da imap server ", e);
        }
    }

    /**
     * *
     * Ottiene la cartella di default della casella del provider
     *
     * @return array di Folder
     * @throws ShpeckServiceException
     */
    public Folder getDefaultFolder() throws ShpeckServiceException {
        try {
            if (store == null || !store.isConnected()) {
                this.store.connect();
            }

            return store.getDefaultFolder();

        } catch (Throwable e) {
            log.error("errore durante il recupero della folder di default da imap server " + store.getURLName().toString(), e);
            throw new ShpeckServiceException("errore durante il recupero della folder di default da imap server ", e);
        }
    }

    /**
     * Ottiene i messaggi in INBOX (tutti o a partire da un determinato ID)
     *
     * @return messaggi presenti in inbox
     * @throws ShpeckServiceException
     */
    public ArrayList<MailMessage> getMessages(String folder) throws ShpeckServiceException {
        log.info("Faccio getMessages dal provider... ");
        ArrayList<MailMessage> mailMessages = new ArrayList<>();

        try {
            if (store == null || !store.isConnected()) {
                this.store.connect();
            }

            /**
             * FetchProfile elenca gli attributi del messaggio che si desidera
             * precaricare dal server
             */
            log.info("Getto una nuovo FetchProfile...");
            FetchProfile fetchProfile = getNewFetchProfile();

            log.info("Setto la inbox dello store");
//            IMAPFolder inbox = (IMAPFolder) this.store.getFolder(INBOX_FOLDER_NAME);
            inbox = (IMAPFolder) this.store.getFolder(folder);
            if (inbox == null) {
                log.error("FATAL: no INBOX");
                //TODO: da vedere se va bene System.exit
                System.exit(1);
            }

            // apertura della cartella in lettura/scrittura
            log.info("Apro la folder");
            inbox.open(Folder.READ_WRITE);

            // ottieni i messaggi dal server
            log.info("Prendo i messaggi da " + lastUID + " a " + getLastUIDToConsider());
            Message[] messagesFromInbox = inbox.getMessagesByUID(lastUID + 1, getLastUIDToConsider());

            log.info("Fetching dei messaggi da " + lastUID + " a " + getLastUIDToConsider());
            inbox.fetch(messagesFromInbox, fetchProfile);

            if (messagesFromInbox.length == 1 && lastUID == inbox.getUID(messagesFromInbox[0])) {
                log.info("messaggio con UID " + lastUID + " già trattato");
            } else {
                for (int i = 0; i < messagesFromInbox.length; i++) {
                    String reportFrom = null;
                    String reportTo = null;
                    String reportSubject = null;
                    String reportDate = null;
                    String reportMessageID = null;

                    /**
                     * tratto ogni messaggio in maniera consistente; se si rompe
                     * la coastruzione di un messaggio, passo al successivo in
                     * modo tale da non creare colli di bottiglia
                     */
                    try {

                        MailMessage m = new MailMessage((MimeMessage) messagesFromInbox[i]);
                        reportMessageID = m.getId();
                        m.setProviderUid(inbox.getUID(messagesFromInbox[i]));
                        mailMessages.add(m);
                        if (inbox.getUID(messagesFromInbox[i]) > lastUID) {
                            lastUID = inbox.getUID(messagesFromInbox[i]);
                            log.debug("lastUID: " + lastUID);
                        }
                    } catch (Throwable e) {
                        try {

                            MimeMessage mm = (MimeMessage) messagesFromInbox[i];

                            reportFrom = mm.getFrom()[0].toString();

                            if (mm.getRecipients(Message.RecipientType.TO) != null) {
                                reportTo = mm.getRecipients(Message.RecipientType.TO)[0].toString();
                            }

                            reportSubject = mm.getSubject();
                            reportDate = mm.getSentDate().toString();
                        } catch (Throwable t) {
                            log.error("Attenzione, non sono riuscito a recuperare tutti i dati per il report");
                        }

                        // creazione messaggio di errore
                        JSONObject json = new JSONObject();
                        json.put("Mailbox", this.mailbox);
                        if (reportMessageID != null) {
                            json.put("messageID", reportMessageID);
                        }
                        json.put("From", reportFrom);
                        json.put("To", reportTo);
                        json.put("Subject", reportSubject);
                        json.put("Date", reportDate);
                        json.put("lastUID", String.valueOf(lastUID));
                        json.put("Exception", e.toString());
                        json.put("ExceptionMessage", e.getMessage());

                        diagnostica.writeInDiagnoticaReport("SHPECK_ERROR_BUILD_MESSAGE", json);
                    }
                }
            }

            // chiudi la connessione ma non rimuove i messaggi dal server
            // close();
            return mailMessages;

        } catch (Throwable e) {
            log.error("errore durante il recupero dei messaggi da imap server " + store.getURLName().toString(), e);
            throw new ShpeckServiceException("errore durante il recupero dei messaggi da imap server ", e);
        }
    }

    /**
     * Recupera i messaggi dal provider andando indietro fino ai giorni passati
     * come parametro.
     */
    public ArrayList<MailMessage> getMessagesFromParametrizedDaysAgoToToday(Integer daysAgo) throws ShpeckServiceException, MessagingException {
        log.info("Dentro getMessagesFromParametrizedDaysAgoToToday()");
        ArrayList<MailMessage> mailMessages = new ArrayList<>();
        try {
            if (store == null || !store.isConnected()) {
                this.store.connect();
            }

            log.info("Connesso al provider");
            FetchProfile fetchProfile = getNewFetchProfile();

            log.info("Setto la cartella Inbox");
            IMAPFolder inbox = (IMAPFolder) this.store.getFolder(INBOX_FOLDER_NAME);
            if (inbox == null) {
                log.error("FATAL: no INBOX");
                //TODO: da vedere se va bene System.exit
                System.exit(1);
            }

            // apertura della cartella in lettura/scrittura
            inbox.open(Folder.READ_WRITE);

            log.info("Creo il parametro di ricerca per trovare i messaggi nel range di due settimane");
            SearchTerm olderThan = new ReceivedDateTerm(ComparisonTerm.LT, new Date());
            SearchTerm newerThan = new ReceivedDateTerm(ComparisonTerm.GT, getTheseDaysAgoDate(daysAgo));
            SearchTerm andTerm = new AndTerm(olderThan, newerThan);

            log.info("Lancio la ricerca");
            Message[] messagesFromInbox = inbox.search(andTerm);

            log.info("fetcho...");
            inbox.fetch(messagesFromInbox, fetchProfile);

            log.info("length " + messagesFromInbox.length);
            log.info("Ciclo... ");
            for (int i = 0; i < messagesFromInbox.length; i++) {
                MailMessage m = new MailMessage((MimeMessage) messagesFromInbox[i]);
                m.setProviderUid(inbox.getUID(messagesFromInbox[i]));
                log.info("* \t * \t *");
                log.info("UUID:\t " + m.getProviderUid());
                log.info("SUBJECT:\t " + m.getSubject());
                log.info("getReceiveDate " + m.getReceiveDate());
                log.info("getSendDate " + m.getSendDate());
                log.info("..............................");
                mailMessages.add(m);
            }
            log.info("---FINE CICLAGGIO---");

        } catch (Throwable e) {
            log.error("errore durante il recupero dei messaggi da imap server (2-weeks range) " + store.getURLName().toString(), e);
            throw new ShpeckServiceException("errore durante il recupero dei messaggi da imap server (2-weeks range) ", e);
        }
        // chiudi la connessione ma non rimuove i messaggi dal server
        // close();
        return mailMessages;
    }

    /**
     * Recupera i messaggi dal provider, fino a una certa data
     */
    public ArrayList<MailMessage> getMessagesUntilDateByFolder(Integer daysAgo, String folderName) throws ShpeckServiceException, MessagingException {
        log.info("getMessagesUntilDateByFolder");
        ArrayList<MailMessage> mailMessages = new ArrayList<>();
        try {
            if (store == null || !store.isConnected()) {
                this.store.connect();
            }

            log.info("Connesso al provider");
            FetchProfile fetchProfile = getNewFetchProfile();

            log.info("Setto la cartella " + folderName);
            IMAPFolder inbox = (IMAPFolder) this.store.getFolder(folderName);
            if (inbox == null) {
                log.error("FATAL: no INBOX");
                System.exit(1);
            }

            // apertura della cartella in lettura/scrittura
            inbox.open(Folder.READ_WRITE);

            // calcollo giorni indietro
            LocalDateTime localDate = LocalDateTime.now().minusDays(daysAgo);
            // trasformazione LocalDateTime in Date
            Date untilDate = Date.from(localDate.atZone(ZoneId.systemDefault()).toInstant());

            log.info("Creazione parametro di ricerca");
            SearchTerm olderThan = new ReceivedDateTerm(ComparisonTerm.LT, untilDate);

            log.info("Lancio la ricerca");
            Message[] messagesFromInbox = inbox.search(olderThan);

            log.info("fetch messaggi...");
            inbox.fetch(messagesFromInbox, fetchProfile);

            log.info("length " + messagesFromInbox.length);
            log.info("Ciclo... ");
            for (int i = 0; i < messagesFromInbox.length; i++) {
                MailMessage m = new MailMessage((MimeMessage) messagesFromInbox[i]);
                m.setProviderUid(inbox.getUID(messagesFromInbox[i]));
                log.info("* \t * \t *");
                log.info("UUID:\t " + m.getProviderUid());
                log.info("SUBJECT:\t " + m.getSubject());
                log.info("getReceiveDate " + m.getReceiveDate());
                log.info("getSendDate " + m.getSendDate());
                log.info("..............................");
                mailMessages.add(m);
            }
            log.info("---FINE CICLAGGIO---");

        } catch (Throwable e) {
            log.error("errore durante il recupero dei messaggi da imap server (2-weeks range) " + store.getURLName().toString(), e);
            throw new ShpeckServiceException("errore durante il recupero dei messaggi da imap server (2-weeks range) ", e);
        }
        // chiudi la connessione ma non rimuove i messaggi dal server
        // close();
        return mailMessages;
    }

    /**
     * Mi restituisce la data del numero di giorni fa passato come parametro.
     */
    public Date getTheseDaysAgoDate(Integer numberOfDays) {
        log.info("getTheseDaysAgoDate", numberOfDays);
        log.info("tolgo " + numberOfDays);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        log.info("da: " + calendar.getTime().toString());
        calendar.add(Calendar.DAY_OF_YEAR, -numberOfDays);
        Date date = calendar.getTime();
        log.info("ritorno: " + date.toString());
        return date;
    }

    /**
     * Mi restituisce la data di due settimana fa da ora
     */
    public Date getTwoWeeksAgoDate() {
        log.info("getTwoWeeksAgoDate");
        int noOfDays = 14; //i.e two weeks
        log.info("tolgo " + noOfDays);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        log.info("da: " + calendar.getTime().toString());
        calendar.add(Calendar.DAY_OF_YEAR, -noOfDays);
        Date date = calendar.getTime();
        log.info("ritorno: " + date.toString());
        return date;
    }

    /**
     * Chiusura dello store per connettersi al server
     */
    public void close() {
        try {
            if (store != null && store.isConnected()) {
                store.close();
            }
        } catch (MessagingException ex) {
            log.error("errore nella chiusura di IMAPStore", ex);
        }
    }

    public void printAllFoldersInAccount() throws MessagingException {
        if (store == null || !store.isConnected()) {
            store.connect();
        }
        IMAPFolder[] folders = (IMAPFolder[]) store.getDefaultFolder().list("*");

        log.info("stampa di tutte le cartelle dell'account");
        for (Folder folder : folders) {
            log.info(">> " + folder.getFullName());
        }
        close();
    }

    public void messageMover(ArrayList<MailMessage> mailMessages, String folderName) throws ShpeckServiceException {
        for (MailMessage mailMessage : mailMessages) {
            messageMover(mailMessage.getId(), folderName);
        }
    }

    public void messageMover(String messageId, String folderName) throws ShpeckServiceException {
        messageMover(Arrays.asList(messageId), folderName);
    }

    public void messageMover(List<String> list, String folderName) throws ShpeckServiceException {
        if (folderName == null) {
            folderName = BACKUP_FOLDER_NAME;
        }

        if (list == null) {
            log.debug("lista di messaggi da spostare = null");
            return;
        }
        if (list.isEmpty()) {
            log.debug("nessun messaggio da spostare");
            return;
        }
//        log.debug("Spostamento di " + list.size() + " messaggi");
        try {
            if (!store.isConnected()) {
                store.connect();
            }
            createWorkingFolder(folderName);
            messageMover(store, BACKUP_SOURCE_FOLDER, INBOX_FOLDER_NAME + "/" + folderName, list);
        } catch (Exception e) {
            throw new ShpeckServiceException("Errore nel muovere i messaggi nella cartella di backup", e);
        }
    }

    protected IMAPFolder createWorkingFolder(String folderName) throws ShpeckServiceException {
        IMAPFolder f, srcfolder = null;
        try {
            srcfolder = (IMAPFolder) store.getFolder(INBOX_FOLDER_NAME);
            srcfolder.open(Folder.READ_WRITE);
            f = (IMAPFolder) srcfolder.getFolder(folderName);
            if (!f.exists()) {
                boolean res = f.create(IMAPFolder.HOLDS_MESSAGES);
                if (!res) {
                    throw new ShpeckServiceException("Errore nella creazione della cartella di backup");
                }
            }
            workingFolder = f;

            return f;
        } catch (MessagingException ex) {
            throw new ShpeckServiceException("Errore di setting della directory: ", ex);
        } finally {
            try {
                srcfolder.close(false);
            } catch (MessagingException e) {
                throw new ShpeckServiceException("Errore di chiusura della source folder INBOX della casella: ", e);
            }
        }
    }

    public static void messageMover(IMAPStore store, String sourceFolder, String destFolder, List<String> messageIds) throws MessagingException {
        Set<String> idSet = new HashSet<>(messageIds);
        if (!store.isConnected()) {
            store.connect();
        }
        try (
                 IMAPFolder srcFolder = (IMAPFolder) store.getFolder(sourceFolder);  IMAPFolder dstFolder = (IMAPFolder) store.getFolder(destFolder)) {
            srcFolder.open(IMAPFolder.READ_WRITE);
            List<Message> messageToMove = new ArrayList<>();
            Message[] messages = srcFolder.getMessages();

            for (Message m : messages) {
                MimeMessage tmp = (MimeMessage) m;
                String messageId = MessageBuilder.getClearMessageID(tmp.getMessageID());
                if (messageId == null) {
                    log.info("Non posso usare il messageID del provider, perché è null");
                    messageId = MessageBuilder.defineMessageID(tmp);
                    log.info("Provo a cercare questo: ", messageId);
                }
                if (idSet.contains(messageId)) {
                    messageToMove.add(m);
                    log.debug("messageMover: " + messageId + " selezionato per lo spostamento");
                }
            }
            dstFolder.open(IMAPFolder.READ_WRITE);
//            srcFolder.copyMessages(messageToMove.toArray(new MimeMessage[messageToMove.size()]), dstFolder);
            srcFolder.moveMessages(messageToMove.toArray(new Message[messageToMove.size()]), dstFolder);
//            for (Message m : messages) {
//                MimeMessage tmp = (MimeMessage) m;
//                String messageId = MessageBuilder.getClearMessageID(tmp.getMessageID());
//                if (idSet.contains(messageId)) {
//                    tmp.setFlag(Flags.Flag.DELETED, true);
//                }
//            }
//            srcFolder.expunge();
        }
    }

    public void deleteMessage(ArrayList<MailMessage> mailMessages) throws ShpeckServiceException {
        log.info("entrato in deleteMessage(ArrayList<MailMessage>): ciclo i messages...");
        for (MailMessage mailMessage : mailMessages) {
            log.info("mailMessage.getId(): " + mailMessage.getId());
            deleteMessage(mailMessage.getId());
        }
    }

    public boolean deleteMessage(String message_id) throws ShpeckServiceException {
        log.info("entrato in deleteMessage(String message_id) " + message_id);
        try {
            log.info("Controllo se è connesso lo store.");
            if (!store.isConnected()) {
                log.info("Non è connesso: lo connetto.");
                this.store.connect();
            }
            // apertura cartella
            log.info("Prendo la casella");
            Folder inbox = this.store.getFolder(INBOX_FOLDER_NAME);
            if (inbox == null) {
                log.error("CARTELLA INBOX NON PRESENTE");
                System.exit(1);
            }
            log.info("Apro la casella");
            inbox.open(Folder.READ_WRITE);
            // Get the messages from the server

            log.info("Prendo i messaggi dal server e li metto in un array");
            Message[] tmpmess = inbox.getMessages();
            log.info("Dimensioni array: " + tmpmess.length);
            IMAPMessage mess;
            String messid;
            log.info("Ciclo il contentuto dell'array");
            for (int i = 0; i < tmpmess.length; i++) {
                log.info("Casto in IMAPMessage il messaggio all'indice " + i + "dell'array");
                mess = (IMAPMessage) tmpmess[i];
                log.info("Prendo l'id ");
                messid = MessageBuilder.defineMessageID(mess);
                log.info("Id: " + messid + "... è quello che devo cancellare?");
                if (messid.equals(message_id)) {
                    log.info("Allora lo flaggo da cancellare");
                    tmpmess[i].setFlag(Flags.Flag.DELETED, true);
                    inbox.close(true);
                    return true;
                }
            }
        } catch (Exception e) {
            throw new ShpeckServiceException("errore: ", e);
        }
        return false;
    }

    public void deleteMessageFromFolder(String folderName, int daysAgo) throws ShpeckServiceException {
        try {
            log.info("Controllo se è connesso lo store.");
            if (!store.isConnected()) {
                log.info("Non è connesso: lo connetto.");
                this.store.connect();
            }
            // apertura cartella
            log.info("Prendo la casella");
            IMAPFolder folder = (IMAPFolder) this.store.getFolder(INBOX_FOLDER_NAME + "/" + folderName);
            if (folder == null) {
                log.error("cartella " + folderName + " non presente");
                System.exit(1);
            }
            log.info("Apro la casella");
            folder.open(Folder.READ_WRITE);

            // calcollo giorni indietro
//            Calendar calendar = Calendar.getInstance();
//            calendar.setTime(new Date());
//            calendar.add(Calendar.DAY_OF_YEAR, -daysAgo);
//            Date untilDate = calendar.getTime();
            // Get current date
            Date currentDate = new Date();

            // convert date to localdatetime
            LocalDateTime localDateTime = currentDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

            // plus one
            localDateTime = localDateTime.minusDays(daysAgo);

            // convert LocalDateTime to date
            Date untilDate = Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());

            log.info("Creazione parametro di ricerca");
            SearchTerm olderThan = new ReceivedDateTerm(ComparisonTerm.LT, untilDate);

            log.info("Lancio la ricerca");
            Message[] tmpmess = folder.search(olderThan);
            log.info("Dimensioni array: " + tmpmess.length);

            log.info("Ciclo il contentuto");
            for (int i = 0; i < tmpmess.length; i++) {
                tmpmess[i].setFlag(Flags.Flag.DELETED, true);
            }
            log.info("messaggi impostati per la cancellazione");
            folder.close(true);
        } catch (Exception e) {
            throw new ShpeckServiceException("errore: ", e);
        }
    }

    // aggiorna la pec con campo uid dell'ultima mail analizzata
    public Pec updateLastUID(Pec pec) {
        log.info("salvataggio lastUID nella PEC...");

        if (pec.getResetLastuidTime() == null) {
            // prima volta che fa run e il reset_lastuid_time non è settato, quindi si setta now()
            pec.setResetLastuidTime(ZonedDateTime.now());
            pec.setLastuid(getLastUID());
        } else {
            // calcolo la differenza (in minuti) per capire se riazzerare la sequenza o meno
//            LocalDateTime now = new java.sql.Timestamp(new Date().getTime()).toLocalDateTime();
//            long minutes = pec.getResetLastuidTime().until(now, ChronoUnit.MINUTES);
//            if (minutes > resetLastuidMinutes) {
//                // i minuti passati dall'ultimo azzeramento sono superiori al valore di configurazione quindi azzeriamo
//                pec.setResetLastuidTime(now);
//                pec.setLastuid(0L);
//                log.info("lastUID = " + 0L);
//            } else {
            // non è ancora il momento di azzerare la sequenza, aggiorno solo il lastuid
            pec.setLastuid(getLastUID());
            log.info("lastUID = " + getLastUID());
//            }
        }
        pec = pecRepository.save(pec);
        log.info("salvataggio lastUID -> OK");
        return pec;
    }

    public ArrayList<MailMessage> getMessagesFromFolder(String folder) throws ShpeckServiceException {
        log.info("Faccio getMessages dal provider... ");
        ArrayList<MailMessage> mailMessages = new ArrayList<>();

        try {
            if (store == null || !store.isConnected()) {
                this.store.connect();
            }

            log.info("Get FetchProfile...");
            FetchProfile fetchProfile = getNewFetchProfile();

            log.info("Setto la folder dello store");
            inbox = (IMAPFolder) this.store.getFolder(folder);
            if (inbox == null) {
                log.error("FATAL: no INBOX");
                System.exit(1);
            }

            // apertura della cartella in lettura/scrittura
            log.info("Apro la folder");
            inbox.open(Folder.READ_WRITE);

            // ottieni i messaggi dal server
            log.info("Prendo i messaggi");
            Message[] messagesFromInbox = inbox.getMessagesByUID(1, getLastUIDToConsider());

            log.info("Fetching dei messaggi");
            inbox.fetch(messagesFromInbox, fetchProfile);

            for (int i = 0; i < messagesFromInbox.length; i++) {
                String reportFrom = null;
                String reportTo = null;
                String reportSubject = null;
                String reportDate = null;
                String reportMessageID = null;

                /**
                 * tratto ogni messaggio in maniera consistente; se si rompe la
                 * coastruzione di un messaggio, passo al successivo in modo
                 * tale da non creare colli di bottiglia
                 */
                try {
                    MailMessage m = new MailMessage((MimeMessage) messagesFromInbox[i]);
                    reportMessageID = m.getId();
                    m.setProviderUid(inbox.getUID(messagesFromInbox[i]));
                    mailMessages.add(m);
                    if (inbox.getUID(messagesFromInbox[i]) > lastUID) {
                        lastUID = inbox.getUID(messagesFromInbox[i]);
                        log.debug("lastUID: " + lastUID);
                    }
                } catch (Throwable e) {
                    try {
                        MimeMessage mm = (MimeMessage) messagesFromInbox[i];
                        reportFrom = mm.getFrom()[0].toString();
                        if (mm.getRecipients(Message.RecipientType.TO) != null) {
                            reportTo = mm.getRecipients(Message.RecipientType.TO)[0].toString();
                        }
                        reportSubject = mm.getSubject();
                        reportDate = mm.getSentDate().toString();
                    } catch (Throwable t) {
                        log.error("Attenzione, non sono riuscito a recuperare tutti i dati per il report");
                    }

                    // creazione messaggio di errore
                    JSONObject json = new JSONObject();
                    json.put("Mailbox", this.mailbox);
                    if (reportMessageID != null) {
                        json.put("messageID", reportMessageID);
                    }
                    json.put("From", reportFrom);
                    json.put("To", reportTo);
                    json.put("Subject", reportSubject);
                    json.put("Date", reportDate);
                    json.put("lastUID", String.valueOf(lastUID));
                    json.put("Exception", e.toString());
                    json.put("ExceptionMessage", e.getMessage());

                    diagnostica.writeInDiagnoticaReport("SHPECK_ERROR_BUILD_MESSAGE_FROM_FOLDER", json);
                }
            }

            // chiudi la connessione ma non rimuove i messaggi dal server
            return mailMessages;

        } catch (Throwable e) {
            log.error("errore durante il recupero dei messaggi da imap server " + store.getURLName().toString(), e);
            throw new ShpeckServiceException("errore durante il recupero dei messaggi da imap server ", e);
        }
    }

    public void enqueueForUpload(it.bologna.ausl.model.entities.shpeck.Message message) {
        log.debug("enqueueForUpload -> " + message.getId());
        log.debug("chiamo lo store manager per salvare in uploadQueue");
        storeManager.insertToUploadQueue(message);
    }

    public void closeFolder() throws MessagingException {
        if (inbox != null) {
            inbox.close();
        }
    }

}
