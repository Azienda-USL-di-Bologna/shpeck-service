/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.bologna.ausl.shpeck.service.controllers;

import it.bologna.ausl.model.entities.shpeck.Inbox;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.shpeck.service.exceptions.EmlHandlerException;
import it.bologna.ausl.shpeck.service.exceptions.MailMessageException;
import it.bologna.ausl.shpeck.service.handlers.EmlHandler;
import it.bologna.ausl.shpeck.service.objects.MailMessage;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.slf4j.LoggerFactory;

/**
 * Gestisce la conversione dei dati di un EmlHandler in un entità Inbox
 * e ne fa la persistenza.
 * @author Salo
 */
public class InboxController {
    static org.slf4j.Logger log = LoggerFactory.getLogger(InboxController.class);
    
//    @PersistenceContext
    EntityManager em;
    
    public void saveInboxEml(File file) throws EmlHandlerException, MailMessageException {
        try {
            log.info("Creo l'EmlHandler dal file");
            EmlHandler eh = new EmlHandler(file);
            log.info("Ricavo il MailMessage dall'EmlHandler");
            MailMessage m = eh.getMyMailMessage();
            
            log.info("Creo il Message");
            Message message = new Message();
            message.setIsPec(m.getIsPec());
            
            log.info("Creo un'entità Inbox");
            Inbox inbox = new Inbox();
            log.info("Setto raw_message ad Inbox");
            inbox.setRawMessage(m.getRaw_message());
            log.info("Setto il message ad Inbox");
            inbox.setIdMessage(message);
            
            
            
        } catch (EmlHandlerException ex) {
            log.info("Entrato nel catch: EmlHandlerException " + ex.getMessage());
            ex.printStackTrace();
            throw ex;
        } catch (MailMessageException ex) {
            log.info("Entrato nel catch: MailMessageException " + ex.getMessage());
            ex.printStackTrace();
            throw ex;
        }
    }
}
