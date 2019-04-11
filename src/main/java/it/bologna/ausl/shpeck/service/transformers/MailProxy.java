package it.bologna.ausl.shpeck.service.transformers;

import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckPecPayloadNotFoundException;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author spritz
 */
public class MailProxy implements MailIdentity{

    private static final Logger log = LoggerFactory.getLogger(MailProxy.class);
    
    private MailIdentity mailIdentity;
    private Message.MessageType type;
    private MailMessage mailMessage;
    
    public MailProxy(MailMessage mailMessage) throws ShpeckServiceException{

        this.mailMessage = mailMessage;
        
        if(PecMessage.isPecMessage(mailMessage.getOriginal())){
            mailIdentity = new PecMessage(mailMessage);
        } else if(PecRecepit.isPecRecepit(mailMessage.getOriginal())){
             mailIdentity = new PecRecepit(mailMessage);
        } else{
            mailIdentity = new MailMessage(mailMessage.getOriginal());
        }
        
        
    }

    @Override
    public void isInDb(MailMessage mailMessage) throws ShpeckServiceException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Message.MessageType getType() throws ShpeckServiceException {
        return mailIdentity.getType();
    }

    @Override
    public Object getMail() throws ShpeckServiceException {
       return mailIdentity.getMail();
    }
    

    
}
