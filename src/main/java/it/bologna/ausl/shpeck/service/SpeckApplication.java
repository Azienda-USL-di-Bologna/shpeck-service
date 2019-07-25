package it.bologna.ausl.shpeck.service;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.configuration.Applicazione;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.repository.AddressRepository;
import it.bologna.ausl.shpeck.service.repository.ApplicazioneRepository;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import it.bologna.ausl.shpeck.service.worker.IMAPWorker;
import it.bologna.ausl.shpeck.service.worker.SMTPWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import it.bologna.ausl.shpeck.service.worker.ShutdownThread;
import it.bologna.ausl.shpeck.service.worker.UploadWorker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;

/**
 *
 * @author Salo
 */
@SpringBootApplication(scanBasePackages = "it.bologna.ausl.shpeck")
@EnableJpaRepositories({"it.bologna.ausl.shpeck.service.repository"})
@EntityScan("it.bologna.ausl.model.entities")
public class SpeckApplication {

    /**
     * Punto di partenza dell'applicazione
     */
    private static final Logger log = LoggerFactory.getLogger(SpeckApplication.class);

    @Autowired
    ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    @Autowired
    ShutdownThread shutdownThread;

    @Autowired
    ApplicationContext context;

    @Autowired
    BeanFactory beanFactory;

//    @Autowired
//    UploadWorker uploadWorker;
    @Autowired
    PecRepository pecRepository;

    @Autowired
    AddressRepository addressRepository;

    @Autowired
    ApplicazioneRepository applicazioneRepository;

    @Value("${shpeck.threads.smtp-delay}")
    String smtpDelay;

    @Value("${shpeck.threads.imap-delay}")
    String imapDelay;

    @Value("${test-mode}")
    Boolean testMode;

    @Value("${shpeck.test-mail}")
    String testMail;

    @Value("${id-applicazione}")
    String idApplicazione;

    public static void main(String[] args) {
        SpringApplication.run(SpeckApplication.class, args);
    }

    @Bean
    public CommandLineRunner schedulingRunner() {

        return new CommandLineRunner() {

            @Override
            // @Transactional(rollbackFor = Throwable.class, noRollbackFor = ShpeckServiceException.class, propagation = Propagation.REQUIRED)
            public void run(String... args) throws ShpeckServiceException {

                Applicazione applicazione = applicazioneRepository.findById(idApplicazione);

                // avvio del thread di UploadWorker
                UploadWorker uploadWorker = beanFactory.getBean(UploadWorker.class);
                uploadWorker.setThreadName("uploadWorker");
                scheduledThreadPoolExecutor.scheduleWithFixedDelay(uploadWorker, 0, 5, TimeUnit.SECONDS);

                // recupera le mail attive
                ArrayList<Pec> pecAttive = pecRepository.findByAttivaTrue();

                if (testMode) {
                    String[] testMailArray = Arrays.stream(testMail.split("\\,")).toArray(String[]::new);
                    ArrayList<String> testMailList = new ArrayList<>(Arrays.asList(testMailArray));
                    pecAttive.removeIf(pec -> !isTestMail(pec, testMailList));
                }

                // lancio di IMAPWorker per ogni casella PEC attiva
                log.info("creazione degli IMAPWorker per ogni casella PEC attiva...");
                for (int i = 0; i < pecAttive.size(); i++) {
                    IMAPWorker imapWorker = beanFactory.getBean(IMAPWorker.class);
                    imapWorker.setThreadName("IMAP_" + pecAttive.get(i).getIndirizzo());
                    imapWorker.setIdPec(pecAttive.get(i).getId());
                    imapWorker.setApplicazione(applicazione);
                    scheduledThreadPoolExecutor.scheduleWithFixedDelay(imapWorker, i + 2, Integer.valueOf(imapDelay), TimeUnit.SECONDS);
                    log.info("IMAPWorker_su PEC " + pecAttive.get(i).getIndirizzo() + " schedulato correttamente");
                }
                log.info("creazione degli IMAPWorker eseguita con successo");

                // creo e lancio l'SMTPWorker per ogni casella PEC attiva
                log.info("creazione degli SMTPWorker per ogni casella PEC attiva...");
                for (int i = 0; i < pecAttive.size(); i++) {
                    SMTPWorker smtpWorker = beanFactory.getBean(SMTPWorker.class);
                    smtpWorker.setThreadName("SMTP_" + pecAttive.get(i).getIndirizzo());
                    smtpWorker.setIdPec(pecAttive.get(i).getId());
                    scheduledThreadPoolExecutor.scheduleWithFixedDelay(smtpWorker, i * 3 + 2, Integer.valueOf(smtpDelay), TimeUnit.SECONDS);
                    log.info(smtpWorker.getThreadName() + " su PEC " + pecAttive.get(i).getIndirizzo() + " schedulato correttamente");
                }
                log.info("creazione degli SMTPWorker eseguita con successo");
                Runtime.getRuntime().addShutdownHook(shutdownThread);
            }
        };
    }

    private boolean isTestMail(Pec pec, ArrayList<String> list) {
        return list.contains(pec.getIndirizzo());
    }

}
