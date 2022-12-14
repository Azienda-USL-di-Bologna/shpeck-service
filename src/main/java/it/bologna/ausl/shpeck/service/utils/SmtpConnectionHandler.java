package it.bologna.ausl.shpeck.service.utils;

import it.bologna.ausl.shpeck.service.exceptions.SmtpConnectionInitializationException;
import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.baborg.PecProvider;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import it.bologna.ausl.shpeck.service.repository.PecProviderRepository;
import java.util.Properties;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 *
 * @author Salo
 */
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SmtpConnectionHandler {

    private static final Logger log = LoggerFactory.getLogger(SmtpConnectionHandler.class);
    private Properties properties;
    private Transport transport;
    private Session session;

    @Value("${mail.smtptTimeout}")
    Integer smtpTimeOut;

    @Autowired
    PecRepository pecRepository;

    @Autowired
    PecProviderRepository pecProviderRepository;

    public SmtpConnectionHandler() {
        properties = new Properties();
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
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

    private void init(Pec pec) throws SmtpConnectionInitializationException, ShpeckServiceException {
        log.info("init() della sessione e del transport");
        setPropertiesByPec(pec);
        session = Session.getInstance(properties, null);
        try {
            transport = session.getTransport();
            if (pec.getUsername() != null) {
                log.info(pec.getUsername());
                log.info(pec.getPassword());
                if (pec.getIdPecProvider().getDescrizione().equalsIgnoreCase("ARUBA")) {
                    // ARUBA vuole tutto l'indirizzo
                    transport.connect(pec.getIndirizzo(), pec.getPassword());
                } else {
                    transport.connect(pec.getUsername(), pec.getPassword());
                }
            } else {
                transport.connect();
            }
        } catch (NoSuchProviderException e) {
            log.error("Errore nel gettingTransport: ", e);
            throw new SmtpConnectionInitializationException("Errore nel gettingTransport: " + e.getMessage(), e);
        } catch (MessagingException e) {
            log.error("Errore di connessione al provider: ", e);
            throw new SmtpConnectionInitializationException("Errore di connessione al provider: " + e.getMessage(), e);
        }
    }

    public void setPropertiesWithProperties(String host, int port, String username, String password, String protocol, int smtpTimeout) {

        log.debug("host: " + host + " port: " + String.valueOf(port) + " protocol: " + protocol);
        Properties props = new Properties();
        props.setProperty("mail.transport.protocol", protocol.toLowerCase());
        props.setProperty("mail.host", host);
        props.setProperty("mail.port", String.valueOf(port));
        props.setProperty("mail.smtp.timeout", String.valueOf(smtpTimeout * 1000));
        props.setProperty("mail.smtps.timeout", String.valueOf(smtpTimeout * 1000));
        if (protocol.toLowerCase().equals("smtps")) {
            props.setProperty("mail.smtps.host", host);
            props.setProperty("mail.smtps.port", String.valueOf(port));
            if (username != null) {
                props.setProperty("mail.smtps.user", username);
            }
            if (password != null) {
                props.setProperty("mail.smtps.auth", "true");
            }
        } else {
            props.setProperty("mail.smtp.host", host);
            props.setProperty("mail.smtp.port", String.valueOf(port));
            if (username != null) {
                props.setProperty("mail.smtp.user", username);
            }
            if (password != null) {
                props.setProperty("mail.smtp.auth", "true");
            }
        }
        setProperties(props);
    }

    private void setPropertiesByPec(Pec pec) throws ShpeckServiceException {
        log.info("--- inizio setPropertiesByPec ---");
        PecProvider idPecProvider = pecProviderRepository.findById(pec.getIdPecProvider().getId()).get();
        log.info("recuperato provider " + idPecProvider.toString());
        try {
            setPropertiesWithProperties(idPecProvider.getHostOut(), idPecProvider.getPortOut(),
                    pec.getUsername(), pec.getPassword(), idPecProvider.getProtocolOut(), smtpTimeOut);
        } catch (Exception e) {
            log.error("Errore nella configurazione delle propriet?? della sessione SMTP \n"
                    + "pec " + pec.toString() + "\n"
                    + "provider " + idPecProvider.toString() + "\n"
                    + "Rilancio errore " + e.getMessage());
            throw new ShpeckServiceException("errore nella setPropertiesByPec", e);
        }
        log.info("\n######\nconfigurate le properties: \n" + properties.toString() + "\n######\n");
    }

    public void createSmtpSession(Pec pec) throws NoSuchProviderException, SmtpConnectionInitializationException, ShpeckServiceException {
        log.info("entrato in createSmtpSession con pec " + pec.getIndirizzo());
        init(pec);
    }

    public void close() {
        if (transport.isConnected()) {
            try {
                transport.close();
            } catch (MessagingException e) {
                log.error("Problemi nella chiusura della connessione", e);
            }
        }

    }

    public boolean isConnected() {
        return transport.isConnected();
    }

}
