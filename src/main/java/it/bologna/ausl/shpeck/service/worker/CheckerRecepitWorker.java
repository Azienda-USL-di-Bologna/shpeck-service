/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.bologna.ausl.shpeck.service.worker;

import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.RecepitRepository;
import it.bologna.ausl.shpeck.service.utils.Diagnostica;
import java.sql.SQLException;
import java.util.ArrayList;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 *
 * @author Matteo Next
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
public class CheckerRecepitWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CleanerWorker.class);

    @Autowired
    Diagnostica diagnostica;

    @Autowired
    MessageRepository messageRepository;

    @Autowired
    RecepitRepository recepitRepository;

    @Value("${test-mode}")
    Boolean testMode;

    public CheckerRecepitWorker() {
    }

    private String threadName;

    private ArrayList<Integer> errorMessageIds;

    private ArrayList<Integer> recepitList;

    public void setThreadName(String s) {
        threadName = s;
    }

    public String getThreadName() {
        return threadName;
    }

    public void doWork() throws SQLException {

        errorMessageIds = messageRepository.getCurrentMessagesError();

        for (int i = 0; i < errorMessageIds.size(); i++) {

            Integer tempId = errorMessageIds.get(i);

            recepitList = messageRepository.getAllRecepitError(tempId);

            if (recepitList.isEmpty()) {

                JSONObject json = new JSONObject();

                log.info("Son entrato nel caso da segnalare");

                json.put("Id messaggio senza ricevuta d'accettazione: ", tempId.toString());

                diagnostica.writeInDiagnoticaReport("SHPECK_ERROR_RECEPIT", json);
            }

            //log.info(recepitList.toString());
        }

        for (int i = 0; i < recepitList.size(); i++) {

            Integer tempId = recepitList.get(i);

            ArrayList<Integer> r = recepitRepository.getRecepitAccettazione(tempId);

            if (r.isEmpty()) {

                JSONObject json = new JSONObject();

                log.info("Son entrato nel caso da segnalare");

                json.put("Id messaggio senza ricevuta d'accettazione: ", tempId.toString());

                diagnostica.writeInDiagnoticaReport("SHPECK_ERROR_RECEPIT", json);
            }
        }
    }

    @Override
    public void run() {
        MDC.put("logFileName", threadName);
        try {
            log.info("Entrato nel run di " + threadName);
            doWork();
        } catch (Throwable e) {
            log.info("ERRORE nel main run di CheckerRecepitWorker: " + e.toString());
            log.error(e.getMessage());
            e.printStackTrace();
        }
        MDC.remove("logFileName");
    }

}
