package it.bologna.ausl.shpeck.service.utils;

import com.sun.mail.imap.IMAPStore;
import it.bologna.ausl.model.entities.baborg.Pec;
import java.util.Properties;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.URLName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 *
 * @author spritz
 * 
 * Crea un IMAPStore utilizzando le credenziali presenti nella PEC
 */

@Component
public class ProviderConnectionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(ProviderConnectionHandler.class);
    
    private final Properties properties;

    public ProviderConnectionHandler(
            @Value("${mail.imaps.ssl.trust}") String imaps_ssl_trust,
            @Value("${mail.imaps.timeout}") String imaps_timeout,
            @Value("${mail.imap.timeout}") String imap_timeout,
            @Value("${mail.imap.connectiontimeout}") String imap_connection_timeout,
            @Value("${mail.imaps.connectiontimeout}") String imaps_connection_timeout,
            @Value("${mail.imaps.closefoldersonstorefailure}") String imaps_close_folders_on_store_failure,
            @Value("${mail.imaps.compress.enable}") String imaps_compress_enable
    ) {
        properties = new Properties();
        properties.setProperty("mail.imaps.ssl.trust", imaps_ssl_trust);
        properties.setProperty("mail.imaps.timeout", imaps_timeout);
        properties.setProperty("mail.imap.timeout", imap_timeout);
        properties.setProperty("mail.imap.connectiontimeout", imap_connection_timeout);
        properties.setProperty("mail.imaps.connectiontimeout", imaps_connection_timeout);
        properties.setProperty("mail.imaps.closefoldersonstorefailure", imaps_close_folders_on_store_failure);
        properties.setProperty("mail.imaps.compress.enable", imaps_compress_enable);
    }
        
    private IMAPStore createIMAPStore(String uri) throws NoSuchProviderException {
        return (IMAPStore) Session.getInstance(properties).getStore(new URLName(uri));
    }
    
    private String build_uri(String host, int port, String username, String password, String protocol) {
        String uri;
        if (host == null || port < 0 || port > 65535 || username == null || password == null || protocol == null) {
            throw new IllegalArgumentException("Parameters are not good");
        }
        uri = protocol.toLowerCase() + "://" + username + ":" + password + "@" + host + ":" + Integer.toString(port) + "/INBOX";
        return uri;
    }
    
    public IMAPStore createProviderConnectionHandler(Pec pec) throws NoSuchProviderException{
        String uri = build_uri(pec.getIdPecProvider().getHost(), pec.getIdPecProvider().getPort(), pec.getUsername(), pec.getPassword(), pec.getIdPecProvider().getProtocol());
        log.info("URI: " + uri);
        return createIMAPStore(uri);
    }
}
