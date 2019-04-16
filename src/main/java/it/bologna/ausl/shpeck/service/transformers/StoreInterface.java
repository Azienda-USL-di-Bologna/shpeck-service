package it.bologna.ausl.shpeck.service.transformers;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.Recepit;

/**
 *
 * @author spritz
 */
public interface StoreInterface {
    public Message createMessageForStorage(final MailMessage m, Pec pec, boolean isAccettazione);
    
    public Message storeMessage(Message message);
    
    public Recepit storeRecepit(Recepit recepit);
}
