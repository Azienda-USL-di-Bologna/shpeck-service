/*
 * E' una classe pensata per gestire il file eml sicuramente ben formato
 */
package it.bologna.ausl.shpeck.service.utils;

import it.bologna.ausl.shpeck.service.exceptions.EmlHandlerException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.logging.Level;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Properties;
import javax.mail.Session;

/**
 * Questa classe serve a gestire un file eml: se ne può creare 
 * @author Salo
 */
public class EmlHandler {
    private static Logger log = LoggerFactory.getLogger(EmlHandler.class);
    private static MailMessage mailMessage;
    private static ArrayList<Part> mimeMessageParts;
    
    public EmlHandler(File file) throws EmlHandlerException{
        try {
            this.setMailMessageFromEml(file);
        } catch (EmlHandlerException ex) {
            ex.printStackTrace();
            throw new EmlHandlerException("Il mailMessage non è definito nell'handler", new NullPointerException());
        }
    }
    
    /**
     * Stampa a log i dati del messaggio.
     * @param m è il mime message (si presuppone ben formato)
     */
    public void emlToString(MimeMessage m){
        log.info("#### STAMPA EML INIZIO ####");
        try {
            log.info("getMessageID " + m.getMessageID());
            log.info("getContentID " + m.getContentID());
            log.info("getContentMD5 " + m.getContentMD5());
            log.info("getContentType " + m.getContentType());
            log.info("getDescription " + m.getDescription());
            log.info("getDisposition " + m.getDisposition());
            log.info("getEncoding " + m.getEncoding());
            log.info("getFileName " + m.getFileName());
            log.info("getSubject " + m.getSubject());
            log.info("getAllHeaders " + "...");
            Enumeration<Header> e1 = m.getAllHeaders();
            String s = "\n";
            while (e1.hasMoreElements()) {
                Header e = e1.nextElement();
                s = s + "\t" + e.getName() + " => " + e.getValue() + "\n";
            }
            log.info(s);
            log.info("getAllHeaderLines " + "...");
            Enumeration<String> e2 = m.getAllHeaderLines();
            while(e2.hasMoreElements())
                log.info("\t" + e2.nextElement());

            log.info("getContentLanguage " + m.getContentLanguage()[0]);
            log.info("getSize " + m.getSize());
            log.info("getReceivedDate " + m.getReceivedDate());
            
            // Questa riga qua manda in errore:
            //log.info("getSender().toString " + m.getSender().toString());
            
            log.info("getFrom() " + " ... ");
            for (Address add : m.getFrom()) {
                log.info("\t" + add.toString());
            }
        } catch (Exception ex) {
            log.error("AHIA SONO IN ECCEZIONE! Problema nella stampa del messaggio: " +  ex.toString());
            ex.printStackTrace();
        }
        log.info("#### STAMPA EML FINE ####");
    }
        
    /**
     * Genera una Sessione Imap e la torna
     * Serve in generale per poter leggere il MimeMessage
     * @return Session: una sessione Imap
     */
    private static Session getGenericImapSessionInstance(){
        log.info("getGenericImapSessionInstance: setto le proprietà di connessione per la lettura di MimeMessage");
        Properties props = new Properties();
        props.setProperty("mail.transport.protocol", "imap");
        return Session.getDefaultInstance(props, null);
    }
       
    /**
     * Preso un MimeMessage o una delle sue Part ne restituisce le parti contenute per poterle ciclare
     * @param p il messaggio/parte di esso da ripartire nelle sue sotto parti
     * @return un ArrayList di Part
     */
    public static ArrayList<Part> getPartsArray(Part p) throws MessagingException, IOException, EmlHandlerException{
        ArrayList<Part> parts = new ArrayList<Part>();
        try{
            // se non è multipart allora è già a posto così e posso ritornare
            log.info("entrato in getPartsArray");
            log.info("la parte + " + p.getFileName() + " -- " + p.getContentType() + " -- " + p.getDescription());
            if(!p.isMimeType("multipart/*")){
                log.info("non è multipart: lo aggiungo e ritorno");
                parts.add(p);
            }
            else{
                log.info("un multipart: le ciclo e le aggiungo all'array");
                Multipart mp = (Multipart) p.getContent();
                for (int i = 0, n = mp.getCount(); i < n; i++) {
                    log.info("*" + p.toString() + " part " + i);
                    Part part = mp.getBodyPart(i);
                    //log.info(i + ") " + part.getContentType() + " -- " + part.getDescription());
                    if (!part.isMimeType("multipart/*")) {
                        log.info("Aggiungo la parte all'array...");
                        parts.add(part);
                    } else {
                        log.info("estrapolo le sotto parti...");
                        parts.addAll(getPartsArray(part));
                    }
                }
                log.info("finito di ciclare " + p.getFileName());
            }
            log.info("ritorno l'array");
        }
        catch(Exception ex) {
             ex.printStackTrace();
            throw new EmlHandlerException("Errore nel ciclaggio delle parti --> parte " + p.toString() + " / FileName " + p.getFileName(), ex);
        }
        return parts;
    }
    
    /**
     * Prende un eml e ne torna un MailMessage
     * @param file Il file eml ben formato
     * @return MailMessage: un tipo nostro per gestire il message da salavare sul db
     * @throws FileNotFoundException
     * @throws Exception 
     */
    public MailMessage getMailMessageFromEml(File file) throws FileNotFoundException, Exception{
        log.info("entrato in getMailMessageFromEml()");
        
        MimeMessage m;
        MailMessage mm = null;
        try {
            log.info("creo il mime message con la sessione");
            m = new MimeMessage(getGenericImapSessionInstance(), new FileInputStream(file));
            mimeMessageParts = getPartsArray(m);
            log.info("MimeMessageSize? " + mimeMessageParts.size());
            log.info("Ok, mi creo il MailMessage");
            mm = new MailMessage(m);
        } catch (FileNotFoundException ex) {
            log.info("Entrato nel cathe per FileNotFoundException " + ex.toString());
            ex.printStackTrace();
            throw new EmlHandlerException("Errore nel reperimento del file", ex);
        } catch (Exception ex) {
            log.info("Entrato nel cathe per Exception " + ex.toString());
            ex.printStackTrace();
            throw new EmlHandlerException("Errore con il MailMessage in EmlHandler", ex);
        }
        log.info("RITORNO IL MESSAGGIO -> " + mm.toString());
        return mm;
    }
    
    
    /**
     * Determina gli allegati presenti all'interno di un file eml.
     * @param file un file eml
     * @return un intero corrispondente al numero di allegati
     * @throws EmlHandlerException 
     */
    public static int getAttachmentsCountOfFile(File file) throws EmlHandlerException{
        try{
            
            MimeMessage m;
            m = new MimeMessage(getGenericImapSessionInstance(), new FileInputStream(file));
            return getAttachementCountFromMime(m);
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
            throw new EmlHandlerException("Non sono riuscito a contare gli allegati del file (FileNotFoundException)", ex);
        } catch (MessagingException ex) {
            ex.printStackTrace();
            throw new EmlHandlerException("Non sono riuscito a contare gli allegati del file (MessagingException)", ex);
        } catch (IOException ex) {
            ex.printStackTrace();
            throw new EmlHandlerException("Non sono riuscito a contare gli allegati del file (IOException)", ex);
        }
    }
    
    /**
     * A partire da un MimeMessage, si ciclano le sue parti e ne vengono contate
     * quelle avente un header Content-Disposition contentente 'attachment'.
     * @param m
     * @return
     * @throws EmlHandlerException 
     */
    private static int getAttachementCountFromMime(MimeMessage m) throws EmlHandlerException{
        int attachments = 0;
        try {
            ArrayList<Part> parts = getPartsArray(m);
            if(parts!=null && parts.size()>0){
                log.info("ciclo le parti per ricavarmi gli allegati");
                for (Part part : parts) {
                    log.info("FileName ->\t" + part.getFileName());
                    if(part.getHeader("Content-Disposition")!= null){
                        String[] dispositions = part.getHeader("Content-Disposition");
                        for(int i = 0; i < dispositions.length; i++){
                            if(dispositions[i].contains("attachment"))
                                attachments++;
                        }
                    }
                }
            }
        } catch (MessagingException ex) {
            ex.printStackTrace();
            throw new EmlHandlerException("Errore nel ciclaggio delle parti per contare gli allegati (MessagingException)", ex);
        } catch (IOException ex) {
            ex.printStackTrace();
            throw new EmlHandlerException("Errore nel ciclaggio delle parti per contare gli allegati (IOException)", ex);
        }
        log.info("Trovati " + attachments + " allegati");
        return attachments;
    }
    
    /**
     * Setta la proprietà statica MailMessage dell'EmlHandler ricavandolo dall'eml passato come parametro.
     * @param file
     * @throws EmlHandlerException 
     */
    public void setMailMessageFromEml(File file) throws EmlHandlerException{
        try {
            this.mailMessage = getMailMessageFromEml(file);
        } catch (Exception ex) {
            throw new EmlHandlerException("Errore nel set MailMessage dal file eml", ex);
        }
    }
    
    /**
     * Conta nella proprietà mimeMessageParts le parti avente un header 
     * Content-Disposition contentente 'attachment'. 
     * @return un intero corrispondente al totale delle parti con content diposition 
     * 'attachment', cioè il numero degli allegati
     */
    public int getAttachmentCount() throws EmlHandlerException{
        int attachments = 0;
        try {
            if(mimeMessageParts!=null && mimeMessageParts.size()>0){
                log.info("ciclo le parti per ricavarmi gli allegati");
                for (Part part : mimeMessageParts) {
                    log.info("FileName ->\t" + part.getFileName());
                    if(part.getHeader("Content-Disposition")!= null){
                        String[] dispositions = part.getHeader("Content-Disposition");
                        for(int i = 0; i < dispositions.length; i++){
                            if(dispositions[i].contains("attachment"))
                                attachments++;
                        }
                    }
                }
            }
        } catch (MessagingException ex) {
            ex.printStackTrace();
            throw new EmlHandlerException("Errore nel ciclaggio delle parti per contare gli allegati (MessagingException)", ex);
        }
        log.info("Trovati " + attachments + " allegati");
        return attachments;
    }
    
    /**
     * Ritorna il MailMessage dell'handler istanziato e se questo è null lancia un'eccezione.
     * @return MailMessage: la proprietà statica dell'EmlHandler.
     * @throws EmlHandlerException 
     */
    public MailMessage getMyMailMessage() throws EmlHandlerException{
        if(mailMessage != null)
            return mailMessage;
        else
            throw new EmlHandlerException("Il mailMessage non è definito nell'handler", new NullPointerException());
    }
}
