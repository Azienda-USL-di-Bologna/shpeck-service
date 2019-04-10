package it.bologna.ausl.shpeck.service.transformers;

import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckIllegalRecepitException;
import java.io.IOException;
import java.util.ArrayList;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeMessage;
import org.apache.commons.io.IOUtils;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Nodes;
import nu.xom.ParsingException;
import nu.xom.Text;
import org.apache.commons.text.StringEscapeUtils;

/**
 *
 * @author spritz
 */
public class PecRecepit {
    
    private MailMessage message;
    private String reference;
    private String xRicevuta;

    public PecRecepit(MailMessage m) throws ShpeckServiceException {
        message = m;
        getHeaders(m.original);
    }

    public PecRecepit(MimeMessage m) throws ShpeckServiceException {
        message = new MailMessage(m);
        getHeaders(m);
    }

    private void getHeaders(MimeMessage m) throws ShpeckServiceException {
        try {
            xRicevuta = m.getHeader("X-Ricevuta", "");
            reference = m.getHeader("X-Riferimento-Message-ID", "");
            if (reference == null) {
                try {
                    reference = getReferredMessageIdFromRecepit(m);
                } catch (Exception e) {}
            }
            if (reference == null || xRicevuta == null) {
                throw new ShpeckIllegalRecepitException("Can't find valid recepit headers");
            }
        } catch (MessagingException e) {
            throw new ShpeckServiceException("Error extracting recepit headers", e);
        }
    }

    public MailMessage getMessage() {
        return message;
    }

    public String getReference() {
        return reference;
    }

    public String getxRicevuta() {
        return xRicevuta;
    }
    
    public static boolean isPecRecepit(MimeMessage m) {
        String t;
        try {
            t = m.getHeader("X-Ricevuta", "");
            if (t != null) {
                return true;
            }
        } catch (MessagingException e) {
            return false;
        }

        return false;
    }
    
    /**
     * Dato una ricevuta Pec di accettazione in ingresso restituisce il
     * message-id al quale la ricevuta si riferice.
     *
     * @param recepitMessage
     * @return String
     * @throws ShpeckServiceException
     */
    public static String getReferredMessageIdFromRecepit(MimeMessage recepitMessage) throws ShpeckServiceException {
        
        try {
            ArrayList<Part> recepitParts = getAllParts(recepitMessage);
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
                Nodes nodes = daticertDocument.query("/postacert/dati/msgid");
                if (nodes.size() > 0) {
                    Text msgIdNode = (Text) nodes.get(0).getChild(0);
                    if (msgIdNode != null) {
                        return StringEscapeUtils.unescapeHtml4(msgIdNode.toXML());
                    }
                }
            }
        } catch (IOException | MessagingException | ParsingException e) {
            throw new ShpeckServiceException("Unable to get recipients type", e);
        }
        return null;
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
