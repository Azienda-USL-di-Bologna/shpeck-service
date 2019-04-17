package it.bologna.ausl.shpeck.service.transformers;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.Recepit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author Salo
 */
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RecepitMessageStoreManager extends StoreManager {
    private static final Logger log = LoggerFactory.getLogger(RecepitMessageStoreManager.class);
    
    private PecRecepit pecRecepit;
    private Pec pec;

    public RecepitMessageStoreManager() {
    }

    public PecRecepit getPecRecepit() {
        return pecRecepit;
    }

    public void setPecRecepit(PecRecepit pecRecepit) {
        this.pecRecepit = pecRecepit;
    }

    public Pec getPec() {
        return pec;
    }

    public void setPec(Pec pec) {
        this.pec = pec;
    }
        
    @Transactional
    public Map<String, MailMessage> store(){
        Map<String, MailMessage> res = new HashMap<>();
        log.info("Entrato in RecepitMessageStoreManager.store()");
        Message messaggioDiRicevuta = createMessageForStorage((MailMessage) pecRecepit, pec, false);
        messaggioDiRicevuta.setMessageType(Message.MessageType.RECEPIT);
        messaggioDiRicevuta.setIsPec(Boolean.TRUE);
        Message relatedMessage = messageRepository.findByUuidMessageAndIsPec(pecRecepit.getReference(), false);
        
        if(relatedMessage==null){
            log.error("La ricevuta è orfana! Si riferisce a " + pecRecepit.getReference());
            res.put("orphan", pecRecepit);
            return res;
        }
        
        messaggioDiRicevuta.setIdRelated(relatedMessage);
        if(isPresent(messaggioDiRicevuta)){
            res.put("ok", pecRecepit);
            log.info("Il messaggio è già presente: esco");
            return res;
        }
        
        storeMessage(messaggioDiRicevuta);
        
        Recepit recepit = new Recepit();
        recepit.setIdMessage(messaggioDiRicevuta);
        switch(pecRecepit.getxRicevuta()){ 
            case "accettazione":
                recepit.setRecepitType(Recepit.RecepitType.ACCETTAZIONE);
                break;
            case "preavviso-errore-consegna":
                recepit.setRecepitType(Recepit.RecepitType.PREAVVISO_ERRORE_CONSEGNA);
                break;
            case "presa-in-carico":
                recepit.setRecepitType(Recepit.RecepitType.PRESA_IN_CARICO);
                break;
            case "non-accettazione":
                recepit.setRecepitType(Recepit.RecepitType.NON_ACCETTAZIONE);
                break;
            case "rilevazione-virus":
                recepit.setRecepitType(Recepit.RecepitType.RILEVAZIONE_VIRUS);
                break;
            case "errore-consegna":
                recepit.setRecepitType(Recepit.RecepitType.ERRORE_CONSEGNA);
                break;
            case "avvenuta-consegna":
                recepit.setRecepitType(Recepit.RecepitType.CONSEGNA);
                break;
            default:
                log.error("X-RICEVUTA UNKNOWN!!!! (boh)");
                break;
        }
        
        messaggioDiRicevuta.setIdRecepit(recepit);
        storeMessage(messaggioDiRicevuta);
        res.put("ok", pecRecepit);
        return res;
    }
    
}
