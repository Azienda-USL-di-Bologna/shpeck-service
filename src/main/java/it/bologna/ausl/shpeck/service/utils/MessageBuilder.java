package it.bologna.ausl.shpeck.service.utils;

import it.bologna.ausl.model.entities.shpeck.Address.RecipientType;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.transformers.MailMessage;
import it.bologna.ausl.shpeck.service.transformers.PecRecepit;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Nodes;
import nu.xom.ParsingException;
import nu.xom.Text;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringEscapeUtils;

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

    public static Map<String, RecipientType> getDestinatariType(MailMessage mailMessage) throws ShpeckServiceException {
        Map<String, RecipientType> res = new HashMap<>();
        try {
            MimeMessage mimeMessage = mailMessage.getOriginal();
            ArrayList<Part> recepitParts = getAllParts(mimeMessage);
            Part daticert = null;
            for (Part p : recepitParts) {
                if ("daticert.xml".equalsIgnoreCase(p.getFileName())) {
                    daticert = p;
                    break;
                }
            }
            if (daticert != null) {
                String xmlbody = IOUtils.toString(daticert.getInputStream(), "UTF-8");
                Builder parser = new Builder();
                Document daticertDocument = parser.build(xmlbody, null);
                Element destinatario = null;
                Nodes nodes = daticertDocument.query("/postacert/intestazione/destinatari");
                if (nodes.size() > 0) {
                    for (int i = 0; i < nodes.size(); i++) {
                        destinatario = (Element) daticertDocument.query("/postacert/intestazione/destinatari").get(i);
                        String mailAddress = destinatario.getChild(0).toXML();
                        String mailType = destinatario.getAttribute("tipo").getValue();
                        if (mailType != null) {
                            if ("certificato".equalsIgnoreCase(mailType)) {
                                res.put(mailAddress, RecipientType.PEC);
                            } else if ("esterno".equalsIgnoreCase(mailType)) {
                                res.put(mailAddress, RecipientType.REGULAR_EMAIL);
                            } else {
                                res.put(mailAddress, RecipientType.UNKNOWN);
                            }
                        }
                    }
                }

            }
        } catch (Exception e) {
            //throw new ShpeckServiceException("Unable to get recipients type", e);
        }
        return res;
    }

    public static ArrayList<Part> getAllParts(Part in) throws IOException, MessagingException {
        ArrayList<Part> res = new ArrayList<>();
        if (!in.isMimeType("multipart/*")) {
            res.add(in);
            return res;
        } else {
            Multipart mp = (Multipart) in.getContent();
            for (int i = 0, n = mp.getCount(); i < n; i++) {
                Part part = mp.getBodyPart(i);
                if (!part.isMimeType("multipart/*")) {
                    res.add(part);
                } else {
                    res.addAll(getAllParts(part));
                }
            }
            return res;
        }
    }
}
