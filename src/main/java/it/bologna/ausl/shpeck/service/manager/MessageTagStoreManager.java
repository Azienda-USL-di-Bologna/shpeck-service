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

    public MessageTag createAndSaveErrorMessageTagFromMessage(Message m) {
        MessageTag mt = null;
        try {
            mt = createErrorMessageTagFromMessage(m);
            log.info("Salvo il MessageTag");
            mt = messageTagRepository.save(mt);
        } catch (Exception e) {
            log.error("Errore nel salvataggio del MessageTag: " + e.toString());
            throw e;
        }

        return mt;
    }

    public MessageTag createErrorMessageTagFromMessage(Message message) {
        MessageTag mt = new MessageTag();
        try {
            mt.setIdMessage(message);
            log.info("Carico la pec");
            Pec pec = pecRepository.findById(message.getIdPec().getId()).get();
            log.info("Carico il tag di errore della pec");
            Tag tag = tagRepository.findByNameAndIdPec(Tag.SystemTagName.in_error.toString(), pec);
            mt.setIdTag(tag);
            log.info("Ritorno il MessageTag");
        } catch (Exception e) {
            log.error("Errore nella creazione del MessageTag: " + e.toString());
            throw e;
        }
        return mt;
    }
}