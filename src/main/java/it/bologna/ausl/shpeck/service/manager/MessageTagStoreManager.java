package it.bologna.ausl.shpeck.service.manager;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.MessageTag;
import it.bologna.ausl.model.entities.shpeck.Tag;
import it.bologna.ausl.shpeck.service.repository.MessageTagRepository;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import it.bologna.ausl.shpeck.service.repository.TagRepository;
import it.bologna.ausl.shpeck.service.transformers.PecMessage;
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
public class MessageTagStoreManager extends StoreManager {

    @Autowired
    MessageTagRepository messageTagRepository;

    @Autowired
    TagRepository tagRepository;

    @Autowired
    PecRepository pecRepository;

    private static final Logger log = LoggerFactory.getLogger(MessageTagStoreManager.class);

    private PecMessage pecMessage;
    private Pec pec;
    private Tag tag;

    public MessageTagStoreManager() {
    }

    public MessageTagRepository getMessageTagRepository() {
        return messageTagRepository;
    }

    public void setMessageTagRepository(MessageTagRepository messageTagRepository) {
        this.messageTagRepository = messageTagRepository;
    }

    public TagRepository getTagRepository() {
        return tagRepository;
    }

    public void setTagRepository(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    public PecMessage getPecMessage() {
        return pecMessage;
    }

    public void setPecMessage(PecMessage pecMessage) {
        this.pecMessage = pecMessage;
    }

    public Pec getPec() {
        return pec;
    }

    public void setPec(Pec pec) {
        this.pec = pec;
    }

    public Tag getTag() {
        return tag;
    }

    public void setTag(Tag tag) {
        this.tag = tag;
    }

    public MessageTag createAndSaveErrorMessageTagFromMessage(Message m, Tag.SystemTagName tagName) {
        log.info("createAndSaveErrorMessageTagFromMessage...");
        MessageTag mt = null;
        try {
            mt = createErrorMessageTagFromMessage(m, tagName);
            log.info("Non è che ce l'ho già?");
            MessageTag mesTagTemp = messageTagRepository.findByIdMessageAndIdTag(m, mt.getIdTag());
            if (mesTagTemp != null) {
                log.info("Sì, trovato " + mesTagTemp.toString());
                mt = mesTagTemp;
            } else {
                log.info("No, quindi salvo il MessageTag");
                mt = messageTagRepository.save(mt);
                log.info("Salvato " + mt.toString());
            }

        } catch (Exception e) {
            log.error("Errore nel salvataggio del MessageTag: " + e.toString());
            throw e;
        }

        return mt;
    }

    public MessageTag createErrorMessageTagFromMessage(Message message, Tag.SystemTagName tagName) {
        MessageTag mt = new MessageTag();
        try {
            mt.setIdMessage(message);
            log.info("Carico la pec");
            Pec pec = pecRepository.findById(message.getIdPec().getId()).get();
            log.info("Carico il tag " + tagName.toString() + " della pec");
            Tag tag = tagRepository.findByNameAndIdPec(tagName.toString(), pec);
            mt.setIdTag(tag);
            log.info("Ritorno il MessageTag");
        } catch (Exception e) {
            log.error("Errore nella creazione del MessageTag: " + e.toString());
            throw e;
        }
        return mt;
    }

    public String removeErrorMessageTagIfExists(Message message, Tag.SystemTagName tagName) {
        log.info("Provo a rimuovere dal messaggio il tag " + tagName.toString());
        String risposta = "...";
        try {
            log.info("Carico la pec");
            Pec pec = pecRepository.findById(message.getIdPec().getId()).get();
            log.info("Carico il tag " + tagName.toString() + " della pec");
            Tag tag = tagRepository.findByNameAndIdPec(tagName.toString(), pec);

            MessageTag mt = messageTagRepository.findByIdMessageAndIdTag(message, tag);
            if (mt != null) {
                log.info("Message tag trovato: ora lo elimino");
                messageTagRepository.delete(mt);
                risposta = "Message tag eliminato";
            } else {
                log.info("Nessun message_tag di errore trovato...");
                risposta = "Non c'è nessun message tag di technical_error!";
            }
        } catch (Throwable e) {
            log.error("*** Errore nel rimuovere il messageTag di errore dalla mail ", e);
            risposta = "ERRORE: Ho avuto un problema...";
        }
        return risposta;
    }
}
