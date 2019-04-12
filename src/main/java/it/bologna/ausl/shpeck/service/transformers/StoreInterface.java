package it.bologna.ausl.shpeck.service.transformers;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;

/**
 *
 * @author spritz
 */
public interface StoreInterface {
    public Message storeMessage(final MailMessage m, Pec pec, boolean isAccettazione);
}
