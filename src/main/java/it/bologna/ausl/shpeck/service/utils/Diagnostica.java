package it.bologna.ausl.shpeck.service.utils;

import it.bologna.ausl.model.entities.diagnostica.Report;
import it.bologna.ausl.shpeck.service.repository.ReportRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 *
 * @author spritz
 */
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class Diagnostica {

    private static final Logger log = LoggerFactory.getLogger(Diagnostica.class);

    @Autowired
    ReportRepository reportRepository;

    public void writeInDiagnoticaReport(String tipologiaErrore, JSONObject json) {
        log.debug("writeInDiagnoticaReport");

        boolean isAlreadyInserted = false;

        // guarda che il messaggio non sia stato inserito
        String messageID = (String) json.get("messageID");
        Integer idUploadQueue=null;
        Integer idOutbox=null;
        if (json.get("idOutbox")!=null){
            idOutbox = Integer.parseInt(json.get("idOutbox").toString());
        }
        if (json.get("id_upload_queue")!=null){
            idUploadQueue = Integer.parseInt(json.get("id_upload_queue").toString());
       }
        
        if (messageID != null) {
            List<Report> list = reportRepository.findByTipologiaAndRisoltoIsFalse(tipologiaErrore);
            for (Report report : list) {
                JSONObject additionalData = (JSONObject) JSONValue.parse(report.getAdditionalData());
                String mid = (String) additionalData.get("messageID");
                if (mid != null && MessageBuilder.getClearMessageID(mid).equalsIgnoreCase(messageID)) {
                    log.debug("messaggio di errore già presente su tabella report, non viene inserito nuovamente");
                    isAlreadyInserted = true;
                    break;
                }
            }
        }else if (idOutbox != null) {
            List<Report> list = reportRepository.findByTipologiaAndRisoltoIsFalse(tipologiaErrore);
            for (Report report : list) {
                JSONObject additionalData = (JSONObject) JSONValue.parse(report.getAdditionalData());
                Integer mid=null;
                if (additionalData.get("idOutbox")!=null){
                        mid = Integer.parseInt(additionalData.get("idOutbox").toString());
                        
                }
                if (mid != null && mid.equals(idOutbox)) {
                    log.debug("messaggio di errore già presente su tabella report, non viene inserito nuovamente");
                    isAlreadyInserted = true;
                    break;
                }
            }
        }else if (idUploadQueue != null) {
            List<Report> list = reportRepository.findByTipologiaAndRisoltoIsFalse(tipologiaErrore);
            for (Report report : list) {
                JSONObject additionalData = (JSONObject) JSONValue.parse(report.getAdditionalData());
                //Integer mid = Integer.parseInt(additionalData.get("id_upload_queue").toString());
                Integer mid = null;
                if (additionalData.get("id_upload_queue")!=null){
                        mid = Integer.parseInt(additionalData.get("id_upload_queue").toString());
                        
                }
                if (mid != null && mid.equals(idUploadQueue)) {
                    log.debug("messaggio di errore già presente su tabella report, non viene inserito nuovamente");
                    isAlreadyInserted = true;
                    break;
                }
            }
        }

        if (!isAlreadyInserted) {
            log.debug("!!! inserimento dell'errore nella tabella di report !!!");
            Report report = new Report();
            report.setTipologia(tipologiaErrore);
            report.setDataInserimentoRiga(LocalDateTime.now());
            report.setAdditionalData(json.toJSONString());

            reportRepository.save(report);
        }
    }
}
