package it.bologna.ausl.shpeck.service.transformers;

/**
 *
 * @author Salo
 */
import it.bologna.ausl.shpeck.service.exceptions.MailMessageException;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
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

public class MailMessage implements MailIdentity{
    protected Address[] from, to, cc, reply_to;
    protected MimeMessage original;
    protected Boolean ispec = false;
    protected String subject, string_headers, id, raw_message, message = null;
    protected HashMap<String, String> headers;
    protected Date sendDate, receiveDate; 
    
    public MailMessage(MimeMessage m) throws MailMessageException {
        this.original = m;
        try {
            this.from = original.getFrom();
            this.to = original.getRecipients(Message.RecipientType.TO);
            this.cc = original.getRecipients(Message.RecipientType.CC);
            this.reply_to = original.getReplyTo();
            this.subject = original.getSubject();
            this.receiveDate = original.getReceivedDate();
            this.sendDate = original.getSentDate();
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
        String replyArray[] = null;
        try {
            replyArray = original.getHeader("In-Reply-To");
        } catch (MessagingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        if (replyArray != null) {
            return replyArray[0];
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
        
        if(raw_message == null){
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
        }
        return raw_message;
    }

    public Date getReceiveDate() {
        return receiveDate;
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

    public Date getSendDate() {
        return sendDate;
    }

    public String getString_headers() {
        return string_headers;
    }

    public String getSubject() {
        return subject;
    }

    private String getText(Part p) throws MessagingException, IOException {
        if (p.isMimeType("text/*")) {
            String s = (String) p.getContent();
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
    
    public static MimeMessage getPecPayload(Part p) throws ShpeckServiceException {
        try {
            if (p.isMimeType("message/rfc822") && p.getFileName().equals("postacert.eml")) {
                return (MimeMessage) p.getContent();
            }
        } catch (MessagingException e) {
            throw new ShpeckServiceException("non possibile estrarre il payload pec", e);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (p.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) p.getContent();
                MimeMessage res = null;
                for (int i = 0; i < mp.getCount(); i++) {
                    res = getPecPayload(mp.getBodyPart(i));
                    if (res != null) {
                        return res;
                    }
                }
            }
        } catch (MessagingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return (MimeMessage) null;
    }

    @Override
    public void isInDb(MailMessage mailMessage) throws ShpeckServiceException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public it.bologna.ausl.model.entities.shpeck.Message.MessageType getType() throws ShpeckServiceException {
        return it.bologna.ausl.model.entities.shpeck.Message.MessageType.MAIL;
    }

    @Override
    public Object getMail() throws ShpeckServiceException {
        return this;
    }
}
