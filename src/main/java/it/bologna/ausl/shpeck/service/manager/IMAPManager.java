package it.bologna.ausl.shpeck.service.manager;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.utils.MailMessage;
import java.util.ArrayList;
import javax.mail.FetchProfile;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author spritz
 */
public class IMAPManager {
    
    static Logger log = LoggerFactory.getLogger(IMAPManager.class);
    
    private IMAPStore store;
    private long lastUID = 0;

    public IMAPManager(IMAPStore store) {
        this.store = store;
    }
    
    public IMAPManager(IMAPStore store, long lastUID) {
        this(store);
        this.lastUID = lastUID;
    }
    
    /**
     * Ottiene i messaggi in INBOX (tutti o a partire da un determinato ID)
     * @return messaggi presenti in inbox
     * @throws ShpeckServiceException 
     */
    public ArrayList<MailMessage> getMessages() throws ShpeckServiceException {
        try {
            this.store.connect();

       
            /**
             * FetchProfile elenca gli attributi del messaggio che si 
             * desidera precaricare dal provider
             */
            FetchProfile fetchProfile = new FetchProfile();
            
            // ENVELOPE è un insieme di attributi comuni a un messaggio (es. From, To, Cc, Bcc, ReplyTo, Subject and Date...)
            fetchProfile.add(FetchProfile.Item.ENVELOPE);
            fetchProfile.add("X-Trasporto");
            fetchProfile.add("X-Riferimento-Message-ID");
            
            IMAPFolder inbox = (IMAPFolder) this.store.getFolder("INBOX");
            if (inbox == null) {
                log.error("FATAL: no INBOX");
                System.exit(1);
            }
            
            // apertura della cartella in lettura/scrittura
            inbox.open(Folder.READ_WRITE);

            // ottieni i messaggi dal server
            log.debug("Fetching messages from " + lastUID + " to " + IMAPFolder.LASTUID);
            Message[] messagesFromInbox = inbox.getMessagesByUID(lastUID + 1, IMAPFolder.LASTUID);
            
            inbox.fetch(messagesFromInbox, fetchProfile);
            
            ArrayList<MailMessage> mailMessages = new ArrayList<>();
            
            for (int i = 0; i < messagesFromInbox.length; i++) {
                mailMessages.add(new MailMessage((MimeMessage) messagesFromInbox[i]));
                if (inbox.getUID(messagesFromInbox[i]) > lastUID) {
                    lastUID = inbox.getUID(messagesFromInbox[i]);
                }
            }

            // chiudi la connessione ma non rimuove i messaggi dal server
            close();
            return mailMessages;

        } catch (Exception e) {
            log.error("error getting messages from imap server " + store.getURLName().toString(), e);
            throw new ShpeckServiceException("error getting messages from imap server", e);
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
        } catch (MessagingException e) {}
    }
    
}
