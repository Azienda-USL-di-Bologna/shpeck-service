/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.bologna.ausl.shpeck.service.utils;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import com.sun.mail.imap.IMAPStore;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.URLName;
import javax.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Salo (scopiazzando)
 */
public class ImapManager {
    static Logger log = LoggerFactory.getLogger(ImapManager.class);
    protected IMAPStore store;
    IMAPFolder workingFolder = null;
    long lastuid = 0;
    String uri;

    public static String BACKUP_FOLDER_NAME = "PecGWBackup";

    private static String build_uri(String host, int port, String username, String password, String protocol) {
        String uri;
        if (host == null || port < 0 || port > 65535 || username == null || password == null || protocol == null) {
            throw new IllegalArgumentException("Parameters are not good");

        }
        uri = protocol.toLowerCase() + "://" + username + ":" + password + "@" + host + ":" + Integer.toString(port) + "/INBOX";
        return uri;

    }

    public ImapManager(String uri) throws ShpeckServiceException {

        this.uri = uri;

//        props.setProperty("mail.imap.partialfetch", "false");
//        props.setProperty("mail.imap.fetchsize", "819200");
//	     String host = "pop.aosp.bo.it";
//	     String username = "eharold";
//	     String password = "mypassword";
//	     String provider = "pop3";
        try {

            //Session session = Session.getDefaultInstance(props, null);
            //Session session = Session.getInstance(props);
            this.store = getStore(uri);

        } catch (Exception e) {
            log.error("error initializing imap account: " + uri, e);
            throw new ShpeckServiceException("error initializing imap account", e);
        }
    }

    public ImapManager(String uri, long lastuid) throws ShpeckServiceException {

        this.uri = uri;
        this.lastuid = lastuid;

//        props.setProperty("mail.imap.partialfetch", "false");
//        props.setProperty("mail.imap.fetchsize", "819200");
//	     String host = "pop.aosp.bo.it";
//	     String username = "eharold";
//	     String password = "mypassword";
//	     String provider = "pop3";
        try {

            //Session session = Session.getDefaultInstance(props, null);
            // Session session = Session.getInstance(props);
            this.store = getStore(uri);

        } catch (Exception e) {
            log.error("error initializing imap account: " + uri, e);
            throw new ShpeckServiceException("error initializing imap account", e);
        }
    }

    public static IMAPStore getStore(String uri) throws NoSuchProviderException {
        Properties props = new Properties();
        props.setProperty("mail.imaps.ssl.trust", "*");
        props.setProperty("mail.imaps.timeout", "300000");
        props.setProperty("mail.imap.timeout", "300000");
        props.setProperty("mail.imap.connectiontimeout", "30000");
        props.setProperty("mail.imaps.connectiontimeout", "30000");
        props.setProperty("mail.imaps.closefoldersonstorefailure", "false");
        props.setProperty("mail.imaps.compress.enable", "true");

        Session session = Session.getInstance(props);
        return (IMAPStore) Session.getInstance(props).getStore(new URLName(uri));
    }

    public ImapManager(String host, Integer port, String username, String password, String protocol) throws ShpeckServiceException {
        //pop3s://atex:baobao01@zukka.net:995/INBOX
        this(build_uri(host, port, username, password, protocol));

    }

    public ImapManager(String host, Integer port, String username, String password, String protocol, long lastuid) throws ShpeckServiceException {
        //pop3s://atex:baobao01@zukka.net:995/INBOX
        this(build_uri(host, port, username, password, protocol), lastuid);

    }

    public boolean deleteMessage(String message_id) {

        try {
            if (!store.isConnected()) {
                this.store.connect();
            }

            // Open the folder
            Folder inbox = this.store.getFolder("INBOX");
            if (inbox == null) {
                log.error("FATAL No INBOX");
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
            e.printStackTrace();
        }
        return false;
    }

    public void close() {

        try {
            if (store != null && store.isConnected()) {
                store.close();
            }
        } catch (MessagingException e) {

        }
    }

    public ArrayList<MailMessage> getMessages() throws ShpeckServiceException {

        try {

            this.store.connect();

            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.ENVELOPE);
            fp.add("X-Trasporto");
            fp.add("X-Riferimento-Message-ID");
            fp.add("X-it-bologna-ausl-pecgw");
            IMAPFolder inbox = (IMAPFolder) this.store.getFolder("INBOX");
            if (inbox == null) {
                log.error("FATAL No INBOX");
                System.exit(1);
            }
            inbox.open(Folder.READ_WRITE);

            // Get the messages from the server
            //Message[] tmpmess= inbox.getMessages();
            log.debug("Fetching messages from " + lastuid + " to " + IMAPFolder.LASTUID);
            Message[] tmpmess = inbox.getMessagesByUID(lastuid + 1, IMAPFolder.LASTUID);
            inbox.fetch(tmpmess, fp);
            ArrayList<MailMessage> mess = new ArrayList<>();
            for (int i = 0; i < tmpmess.length; i++) {
                mess.add(new MailMessage((MimeMessage) tmpmess[i]));
                if (inbox.getUID(tmpmess[i]) > lastuid) {
                    lastuid = inbox.getUID(tmpmess[i]);
                }
            }

            // Close the connection
            // but don't remove the messages from the server
            return mess;

        } catch (Exception e) {
            log.error("error getting messages from imap server " + uri, e);
            throw new ShpeckServiceException("error getting messages from imap server", e);
        }

    }

    public long getLastuid() {
        return lastuid;
    }

    public MimeMessage setTreatedFlag(MimeMessage m) {
        try {
            if (getTreatedFlag(m)) {
                return m;
            }
            IMAPFolder f = (IMAPFolder) m.getFolder();
            MimeMessage newmsg = new MimeMessage(m);
            newmsg.addHeader("X-it-bologna-ausl-pecgw", "treated");
            newmsg.saveChanges();
            f.appendMessages(new Message[]{newmsg});
            m.setFlag(Flags.Flag.DELETED, true);
            f.expunge();
            return newmsg;
        } catch (MessagingException e) {
            log.warn("Unable to set Treated flag", e);
        }
        return null;
    }

    public boolean getTreatedFlag(MimeMessage m) {
        String[] tmp;
        try {
            tmp = m.getHeader("X-it-bologna-ausl-pecgw");
            if (tmp != null && tmp[0].equals("treated")) {
                return true;
            }
        } catch (MessagingException e) {
            log.warn("Error getting Treated flag");
        }
        return false;
    }

    public void backupMessage(MailMessage m) throws ShpeckServiceException {
        IMAPFolder srcfolder = null;
        try {
            Message tmp = (Message) m.getOriginal();
            // System.out.println(tmp.getFolder().toString()+" : "+String.valueOf(tmp.getMessageNumber()));
            if (!store.isConnected()) {
                store.connect();
            }
            createWorkingFolder(store, BACKUP_FOLDER_NAME);
            srcfolder = (IMAPFolder) store.getFolder("INBOX");
            srcfolder.open(Folder.READ_WRITE);
            workingFolder.open(Folder.READ_WRITE);
            //if (workingFolder.isOpen()==true) System.out.println("Folder Aperta!!");
            srcfolder.copyMessages(new Message[]{tmp}, workingFolder);
            //workingFolder.appendMessages(new Message[]{tmp});
            tmp.setFlag(Flags.Flag.DELETED, true);
            srcfolder.expunge();
            workingFolder.close(true);
            srcfolder.close(true);
        } catch (MessagingException e) {
            throw new ShpeckServiceException("Error moving message to backup folder", e);
        }

    }

    public void messageMover(String messageId) throws ShpeckServiceException {
        messageMover(Arrays.asList(messageId));
    }

    public void messageMover(List<String> messageIds) throws ShpeckServiceException {
        if (messageIds == null) {
            log.warn("Null messageIds for moving");
            return;
        }
        if (messageIds.size() == 0) {
            return;
        }
        log.debug("Going to move " + messageIds.size() + " messages");
        try {
            IMAPStore store = getStore(this.uri);
            if (!store.isConnected()) {
                store.connect();
            }
            createWorkingFolder(store, BACKUP_FOLDER_NAME);
            messageMover(store, "INBOX", "INBOX/" + BACKUP_FOLDER_NAME, messageIds);
        } catch (Exception e) {
            throw new ShpeckServiceException("Error moving messages to backup folder", e);
        }
        log.debug("Messages moved");
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
                    log.debug("message: " + messageId + " selected for moving");
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

    protected IMAPFolder createWorkingFolder(IMAPStore s, String folderName) throws ShpeckServiceException {
        IMAPFolder f, srcfolder = null;
        try {
            srcfolder = (IMAPFolder) s.getFolder("INBOX");
            srcfolder.open(Folder.READ_WRITE);
            f = (IMAPFolder) srcfolder.getFolder(folderName);
            if (!f.exists()) {
                boolean res = f.create(IMAPFolder.HOLDS_MESSAGES);
                if (!res) {
                    throw new ShpeckServiceException("Error creating pecGW working folder on store");
                }
            }
            workingFolder = f;

            return f;
        } catch (MessagingException e) {
            throw new ShpeckServiceException("Error settingUp pecGW working folder", e);

        } finally {
            try {
                srcfolder.close(false);
            } catch (MessagingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    public void finalize() {
        if (store.isConnected()) {
            try {
                store.close();
            } catch (MessagingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        try {

            //ImapManager p=new ImapManager("imaps://atex:baobao01@zukka.net");
            ImapManager p = new ImapManager("imaps://KQ000129:WX0FEFYX@mbox.cert.legalmail.it:993/INBOX", 355L);
            ArrayList<MailMessage> messages = p.getMessages();
            Iterator<MailMessage> e = messages.iterator();
            MailMessage m;
            int i = 0;
            while (e.hasNext()) {
                m = e.next();
                System.out.println("------------ Message " + (i + 1)
                        + " ------------");
                //messages[i].writeTo(System.out);
                i++;
                System.out.print(m.getRaw_message());
                //  p.backupMessage(m);
                //p.deleteMessage(m.getId());
                System.out.println("LastUID: " + p.getLastuid());
            }
            //ArrayList<MailMessage> mess= m.getMessages();
            /*	m.store.connect();
             IMAPFolder f=(IMAPFolder)m.store.getFolder("INBOX");
             f.open(Folder.READ_WRITE);
             Message[] messages= f.getMessages();
             IMAPMessage im=(IMAPMessage)messages[1];
             MailMessage mm=new MailMessage((MimeMessage)im);
             m.backupMessage(mm);
             f.close(true);
             */
            //System.out.println(im.getFrom()[0]+im.getSubject()+im.getSend_date());
        } catch (ShpeckServiceException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
}
