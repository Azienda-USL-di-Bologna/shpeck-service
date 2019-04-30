package it.bologna.ausl.shpeck.service.transformers;

/**
 *
 * @author spritz
 */
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckPecPayloadNotFoundException;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PecMessage extends MailMessage implements MailIdentity{

    private static final Logger log = LoggerFactory.getLogger(PecMessage.class);
    
    private String xRicevuta, xTrasporto, messageStatus, messageRef = null;
    private MailMessage pecEnvelope = null;
    boolean has_payload = false;
    private Message.MessageType type;

    public PecMessage(MimeMessage m) throws ShpeckServiceException {
        super(getPec(m));
        /**
         * non-accettazione             non acceptance
         * accettazione                 acceptance
         * preavviso-errore-consegna    delivery error advance notice
         * presa-in-carico              take charge
         * rilevazione-virus            virus detection
         * errore-consegna              delivery error
         * avvenuta-consegna            message delivered
         */
        try {
            xRicevuta = m.getHeader("X-Ricevuta", "");
            messageRef = m.getHeader("X-Riferimento-Message-ID", "");
        } catch (MessagingException e) {
            throw new ShpeckServiceException("Problem reading PEC headers", e);
        }
        /**
         * posta-certificata  certified mail
         * errore             error
         */       
        try {
            xTrasporto = m.getHeader("X-Trasporto", "");
            messageRef = m.getHeader("X-Riferimento-Message-ID", "");
        } catch (MessagingException e) {
            throw new ShpeckServiceException("Problem reading PEC headers", e);
        }
    }
    
    public PecMessage(MailMessage m) throws ShpeckServiceException {
        this(m.original);
        pecEnvelope = m;
        pecEnvelope.ispec = true;
        
        if(isPecMessage(pecEnvelope.getOriginal())){
            type = Message.MessageType.PEC;
        } else if(PecMessage.isErrorPec(pecEnvelope.getOriginal())){
            type = Message.MessageType.ERROR;
        } else {
            type = null;
        }
    }
    
    public String getMessageRef() {
        return messageRef;
    }

    public String getMessageStatus() {
        return messageStatus;
    }

    public MailMessage getPecEnvelope() {
        return pecEnvelope;
    }

    public String getxRicevuta() {
        return xRicevuta;
    }

    public String getxTrasporto() {
        return xTrasporto;
    }
    
    private static MimeMessage getPec(MimeMessage m) throws ShpeckServiceException {
        MimeMessage res = null;
        try {
            res = getPecPayload(m);
            if (res == null) {
                throw new ShpeckPecPayloadNotFoundException("Pec payload not found !");
            }
        } catch (ShpeckServiceException e) {
            if (e.getCause() instanceof MessagingException && "Unable to load BODYSTRUCTURE".equalsIgnoreCase(e.getCause().getMessage())) {
                System.out.println("Trying new with new MimeMessage");
                try {
                    res = MailMessage.getPecPayload(new MimeMessage(m));
                } catch (MessagingException ex) {
                    throw new ShpeckServiceException("Unable to create new message from mime message", e);
                }
            } else {
                throw e;
            }
        }
        return res;
    }
    
    public static boolean isPecMessage(MimeMessage m) {
        String t;
        try {
            t = m.getHeader("X-Trasporto", "");
            if (t != null && (t.toLowerCase().equals("posta-certificata") || t.toLowerCase().equals("errore"))) {
                return true;
            }
        } catch (MessagingException e) {
            return false;
        }
        return false;
    }
    
    public static boolean isErrorPec(MimeMessage m) {
        String t;
        try {
            t = m.getHeader("X-Trasporto", "");
            if (t != null && t.toLowerCase().equals("errore")) {
                return true;
            }
        } catch (MessagingException e) {
            return false;
        }
        return false;
    }

    @Override
    public Message.MessageType getType() throws ShpeckServiceException {
       return type;
    }
    

    @Override
    public Object getMail() throws ShpeckServiceException {
        
        PecMessage pecMessage = null;
        
        try {
            pecMessage = new PecMessage(pecEnvelope);
        }catch (ShpeckPecPayloadNotFoundException e) {
            log.error("payload non trovato: ", e);
        }
        return pecMessage;
    }
}
