package it.bologna.ausl.shpeck.service.transformers;

import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;

/**
 *
 * @author spritz
 */
public interface MailIdentity {
    
    public void isInDb(MailMessage mailMessage) throws ShpeckServiceException;
    
    public Message.MessageType getType() throws ShpeckServiceException;
    
    public Object getMail() throws ShpeckServiceException;
}
