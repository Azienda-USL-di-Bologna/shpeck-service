package it.bologna.ausl.shpeck.service.transformers;

import it.bologna.ausl.model.entities.shpeck.MessageInterface;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;

/**
 *
 * @author spritz
 */
public interface MailIdentity {

    public MessageInterface.MessageType getType() throws ShpeckServiceException;

    public Object getMail() throws ShpeckServiceException;
}
