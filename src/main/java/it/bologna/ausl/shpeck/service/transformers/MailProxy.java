package it.bologna.ausl.shpeck.service.transformers;

import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author spritz
 */
public class MailProxy implements MailIdentity{

    private static final Logger log = LoggerFactory.getLogger(MailProxy.class);
    
    private final MailIdentity mailIdentity;

        
    public MailProxy(MailMessage mailMessage) throws ShpeckServiceException{
    
        if(PecMessage.isPecMessage(mailMessage.getOriginal())){
            mailIdentity = new PecMessage(mailMessage);
        } else if(PecRecepit.isPecRecepit(mailMessage.getOriginal())){
             mailIdentity = new PecRecepit(mailMessage);
        } else{
            mailIdentity = new MailMessage(mailMessage.getOriginal());
        }
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
