package it.bologna.ausl.shpeck.service.manager;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import com.sun.mail.imap.IMAPStore;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.transformers.MailMessage;
import it.bologna.ausl.shpeck.service.worker.IMAPWorker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    static Logger log = LoggerFactory.getLogger(IMAPWorker.class);

    @Value("${mailbox.backup-forlder}")
    String BACKUP_FOLDER_NAME;

    @Value("${mailbox.inbox-forlder}")
    String INBOX_FOLDER_NAME;

    @Value("${mailbox.backup-source-forlder}")
    String BACKUP_SOURCE_FOLDER;

    private IMAPStore store;
    private long lastUID;
    IMAPFolder workingFolder = null;

    public IMAPManager() {
        lastUID = 0;
    }

    public IMAPManager(IMAPStore store) {
        this.store = store;
    }

    public IMAPManager(IMAPStore store, long lastUID) {
        this(store);
        this.lastUID = lastUID;
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

    /**
     * Ottiene i messaggi in INBOX (tutti o a partire da un determinato ID)
     *
     * @return messaggi presenti in inbox
     * @throws ShpeckServiceException
     */
    public ArrayList<MailMessage> getMessages() throws ShpeckServiceException {

        ArrayList<MailMessage> mailMessages = new ArrayList<>();

        try {
            if (store == null || !store.isConnected()) {
                this.store.connect();
            }

            /**
             * FetchProfile elenca gli attributi del messaggio che si desidera
             * precaricare dal server
             */
            FetchProfile fetchProfile = new FetchProfile();

            // ENVELOPE (=busta) è un insieme di attributi comuni a un messaggio (es. From, To, Cc, Bcc, ReplyTo, Subject and Date...)
            fetchProfile.add(FetchProfile.Item.ENVELOPE);
            fetchProfile.add("X-Trasporto");
            fetchProfile.add("X-Riferimento-Message-ID");

            IMAPFolder inbox = (IMAPFolder) this.store.getFolder(INBOX_FOLDER_NAME);
            if (inbox == null) {
                log.error("FATAL: no INBOX");
                //TODO: da vedere se va bene System.exit
                System.exit(1);
            }

            // apertura della cartella in lettura/scrittura
            inbox.open(Folder.READ_WRITE);

            // ottieni i messaggi dal server
            log.info("Fetching messages from " + lastUID + " to " + IMAPFolder.LASTUID);
            Message[] messagesFromInbox = inbox.getMessagesByUID(lastUID + 1, Long.MAX_VALUE);

            inbox.fetch(messagesFromInbox, fetchProfile);

            for (int i = 0; i < messagesFromInbox.length; i++) {
                mailMessages.add(new MailMessage((MimeMessage) messagesFromInbox[i]));
                if (inbox.getUID(messagesFromInbox[i]) > lastUID) {
                    lastUID = inbox.getUID(messagesFromInbox[i]);
                    log.info("lastUID: " + lastUID);
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

    public void messageMover(ArrayList<MailMessage> mailMessages) throws ShpeckServiceException {
        for (MailMessage mailMessage : mailMessages) {
            messageMover(mailMessage.getId());
        }
    }

    public void messageMover(String messageId) throws ShpeckServiceException {
        messageMover(Arrays.asList(messageId));
    }

    public void messageMover(List<String> list) throws ShpeckServiceException {
        if (list == null) {
            log.warn("lista di messaggi da spostare è null");
            return;
        }
        if (list.isEmpty()) {
            log.warn("nessun messaggio da spostare");
            return;
        }
        log.debug("Spostamento di " + list.size() + " messaggi");
        try {
            if (!store.isConnected()) {
                store.connect();
            }
            createWorkingFolder(BACKUP_FOLDER_NAME);
            messageMover(store, BACKUP_SOURCE_FOLDER, INBOX_FOLDER_NAME + "/" + BACKUP_FOLDER_NAME, list);
        } catch (Exception e) {
            throw new ShpeckServiceException("Errore nel muovere i messaggi nella cartella di backup", e);
        }
        log.debug("messaggi spostati");
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
                IMAPFolder srcFolder = (IMAPFolder) store.getFolder(sourceFolder);
                IMAPFolder dstFolder = (IMAPFolder) store.getFolder(destFolder)) {
            srcFolder.open(IMAPFolder.READ_WRITE);
            List<MimeMessage> messageToMove = new ArrayList<>(100);
            Message[] messages = srcFolder.getMessages();

            for (Message m : messages) {
                MimeMessage tmp = (MimeMessage) m;
                String messageId = tmp.getMessageID();
                if (idSet.contains(messageId)) {
                    messageToMove.add(tmp);
                    log.debug("messaggio: " + messageId + " selezionato per lo spostamento");
                }
            }
            dstFolder.open(IMAPFolder.READ_WRITE);
            srcFolder.copyMessages(messageToMove.toArray(new MimeMessage[messageToMove.size()]), dstFolder);
            for (Message m : messages) {
                MimeMessage tmp = (MimeMessage) m;
                String messageId = tmp.getMessageID();
                if (idSet.contains(messageId)) {
                    tmp.setFlag(Flags.Flag.DELETED, true);
                }
            }
            srcFolder.expunge();
        }
        store.close();
    }

    public void deleteMessage(ArrayList<MailMessage> mailMessages) throws ShpeckServiceException {
        for (MailMessage mailMessage : mailMessages) {
            deleteMessage(mailMessage.getId());
        }
    }

    public boolean deleteMessage(String message_id) throws ShpeckServiceException {
        try {
            if (!store.isConnected()) {
                this.store.connect();
            }
            // apertura cartella
            Folder inbox = this.store.getFolder(INBOX_FOLDER_NAME);
            if (inbox == null) {
                log.error("CARTELLA INBOX NON PRESENTE");
                System.exit(1);
            }
            inbox.open(Folder.READ_WRITE);
            // Get the messages from the server
            Message[] tmpmess = inbox.getMessages();
            IMAPMessage mess;
            String messid;
            for (int i = 0; i < tmpmess.length; i++) {
                mess = (IMAPMessage) tmpmess[i];
                messid = mess.getMessageID();
                if (messid.equals(message_id)) {
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
}
