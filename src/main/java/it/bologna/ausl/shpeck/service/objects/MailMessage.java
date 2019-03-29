/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.bologna.ausl.shpeck.service.objects;

/**
 *
 * @author Salo
 */
import it.bologna.ausl.shpeck.service.exceptions.MailMessageException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeMessage;

public class MailMessage {
    protected Address[] from, to, cc, reply_to;
    protected MimeMessage original;
    protected Boolean ispec = false;
    protected String subject, string_headers, id, raw_message, message = null;
    protected HashMap<String, String> headers;
    protected Date send_date, receive_date; 
    
    public MailMessage(MimeMessage m) throws MailMessageException {
        this.original = m;
        try {
            this.from = original.getFrom();
            this.to = original.getRecipients(Message.RecipientType.TO);
            this.cc = original.getRecipients(Message.RecipientType.CC);
            this.reply_to = original.getReplyTo();
            this.subject = original.getSubject();
            this.receive_date = original.getReceivedDate();
            this.send_date = original.getSentDate();
            this.id = original.getMessageID();

        } catch (Exception e) {
            e.printStackTrace();
            throw new MailMessageException("Errore nella creazione del MailMessage", e);
        }
    }
    
    public Address[] getCc() {
        return cc;
    }

    public Address[] getFrom() {
        return from;
    }

    public HashMap<String, String> getHeaders() {
        return headers;
    }

    public String getId() {
        return id;
    }

    public String getInReplyTo() {
        String r[] = null;
        try {
            r = original.getHeader("In-Reply-To");
        } catch (MessagingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        if (r != null) {
            return r[0];
        }
        return null;

    }

    public Boolean getIsPec() {
        return ispec;
    }

    public String getMessage() throws MailMessageException {
        if (message != null) {
            return message;
        }
        try {
            message = getText(original);
        } catch (MessagingException e) {
            throw new MailMessageException("errore nell'estrazione del testo del messaggio (MessagingException)", e);
        } catch (IOException e) {
            throw new MailMessageException("errore nell'estrazione del testo del messaggio (IOException)", e);
        }
        return message;

    }

    public MimeMessage getOriginal() {
        return original;
    }

    public String getRaw_message() throws MailMessageException {
        if (raw_message != null) {
            return raw_message;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            original.writeTo(baos);
            raw_message = baos.toString("utf8");
            baos.reset();

        } catch (MessagingException e) {
            throw new MailMessageException("errore nell'estrazione del testo del messaggio (MessagingException)", e);
        } catch (IOException e) {
            throw new MailMessageException("errore nell'estrazione del testo del messaggio (IOException)", e);
        }

        return raw_message;
    }

    public Date getReceive_date() {
        return receive_date;
    }

    public String[] getReferences() {
        try {
            return original.getHeader("References");
        } catch (MessagingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    public Address[] getReply_to() {
        return reply_to;
    }

    public Date getSend_date() {
        return send_date;
    }

    public String getString_headers() {
        return string_headers;
    }

    public String getSubject() {
        return subject;
    }

    private String getText(Part p) throws
            MessagingException, IOException {
        if (p.isMimeType("text/*")) {
            String s = (String) p.getContent();
            //	this.textIsHtml = p.isMimeType("text/html");
            return s;
        }

        if (p.isMimeType("multipart/alternative")) {
            // prefer html text over plain text
            Multipart mp = (Multipart) p.getContent();
            String text = null;
            for (int i = 0; i < mp.getCount(); i++) {
                Part bp = mp.getBodyPart(i);
                if (bp.isMimeType("text/plain")) {
                    if (text == null) {
                        text = getText(bp);
                    }
                    continue;
                } else if (bp.isMimeType("text/html")) {
                    String s = getText(bp);
                    if (s != null) {
                        return s;
                    }
                } else {
                    return getText(bp);
                }
            }
            return text;
        } else if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String s = getText(mp.getBodyPart(i));
                if (s != null) {
                    return s;
                }
            }
        }

        return null;
    }

    public Address[] getTo() {
        return to;
    }

    public void setIsPec(Boolean ispec) {
        this.ispec = ispec;
    }
}
