/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.bologna.ausl.shpeck.service.manager;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.shpeck.service.repository.PecProviderRepository;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import it.bologna.ausl.shpeck.service.utils.SmtpConnectionHandler;
import javax.mail.Session;
import javax.mail.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 *
 * @author Salo
 */
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SMTPManager {
    private static final Logger log = LoggerFactory.getLogger(SMTPManager.class);
    private Transport transport;
    private Session session;
    
    @Autowired
    SmtpConnectionHandler smtpConnectionHandler;
    
    @Autowired
    PecRepository pecRepository;
    
    @Autowired
    PecProviderRepository pecProviderRepository;

    public SMTPManager() {
    }

    public Transport getTransport() {
        return transport;
    }

    public void setTransport(Transport transport) {
        this.transport = transport;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }
        
    public void buildSmtpManagerFromPec(Pec pec) throws Exception{
        log.info("buildSmtpManagerFromPec " + pec.toString());
        try{
            log.info("Creo un SmtpConnectionHandler");
            smtpConnectionHandler.createSmtpSession(pec);
            Session s = smtpConnectionHandler.getSession();
            log.info("setto la session");
            setSession(s);
            log.info("setto il transport che prendo dalla session");
            Transport t = smtpConnectionHandler.getTransport();
            setTransport(t);
        }catch(Exception e){
            log.error("Errore: " + e.getMessage() + "\n"
                    + "Non posso creare l'SMTPManager per pec " + pec.toString() + "\n"
                    + "Rilancio errore");
            throw e;
        }
    }
}
