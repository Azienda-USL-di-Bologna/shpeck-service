package it.bologna.ausl.shpeck.service.transformers;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.RawMessage;
import it.bologna.ausl.model.entities.shpeck.Recepit;
import it.bologna.ausl.model.entities.shpeck.UploadQueue;

/**
 *
 * @author spritz
 */
public interface StoreInterface {
    public Message createMessageForStorage(final MailMessage m, Pec pec);
    
    public Message storeMessage(Message message);
    
    public Recepit storeRecepit(Recepit recepit);
    
    public boolean isPresent(Message message);
    
    public RawMessage storeRawMessageAndUploadQueue(Message message, String rawMessage);
    
    public void insertToUploadQueue(RawMessage raw);
    
    public void removeFromUploadQueue(UploadQueue uq);
}
