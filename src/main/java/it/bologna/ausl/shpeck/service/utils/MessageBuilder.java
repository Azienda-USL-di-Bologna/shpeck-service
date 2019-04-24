package it.bologna.ausl.shpeck.service.utils;

import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

/**
 *
 * @author spritz
 */
public class MessageBuilder {
    
    public static MimeMessage buildMailMessageFromString(String s) throws ShpeckServiceException {
        ByteArrayInputStream bais = null;
        try {
            bais = new ByteArrayInputStream(s.getBytes("utf8"));
            return buildMailMessageFromIS(bais);
        } catch (UnsupportedEncodingException e) {
            throw new ShpeckServiceException("conversione da string a inputstream non riuscita", e);
        }
    }
    
    public static MimeMessage buildMailMessageFromIS(InputStream mimeInputStream) throws ShpeckServiceException {
        MimeMessage m;
        try {
            m = new MimeMessage(getGenericSmtpSession(), mimeInputStream);
            return m;
        } catch (MessagingException e) {
            throw new ShpeckServiceException("conversione da string a MimeMessage non riuscita", e);
        }
    }
    
    
    public static Session getGenericSmtpSession() {
        Properties props = new Properties();
        props.setProperty("mail.transport.protocol", "smtp");
        return Session.getDefaultInstance(props, null);
    }
}
