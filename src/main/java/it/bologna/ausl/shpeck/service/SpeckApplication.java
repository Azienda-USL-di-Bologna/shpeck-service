package it.bologna.ausl.shpeck.service;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.configuration.Applicazione;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.repository.AddressRepository;
import it.bologna.ausl.shpeck.service.repository.ApplicazioneRepository;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import it.bologna.ausl.shpeck.service.worker.IMAPWorker;
import it.bologna.ausl.shpeck.service.worker.IMAPWorkerChecker;
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
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

    @Value("${hour-to-start}")
    Integer hourToStart;

    public static void main(String[] args) {
        SpringApplication.run(SpeckApplication.class, args);
    }

    @Bean
    public CommandLineRunner schedulingRunner() {

        return new CommandLineRunner() {

            @Override
            public void run(String... args) throws ShpeckServiceException {
                log.info(". entrato nel run .");

                log.info("Recupero l'applicazione");
                Applicazione applicazione = applicazioneRepository.findById(idApplicazione);

                faiGliUploadWorker();

                log.info("Recupero le pec attive");
                ArrayList<Pec> pecAttive = pecRepository.findByAttivaTrue();

                log.info("Pec attive #: " + pecAttive.size());

                if (testMode) {
                    log.info("CHECK TEST MODE POSITIVO, uso solo le pec di test");
                    filtraPecAttiveDiProdAndMantieniQuelleDiTest(pecAttive);
                }

                faiGliImapWorkerDiRiconciliazione(pecAttive, applicazione);

                faiGliImapWorker(pecAttive, applicazione);

                faiGliSMTPWorker(pecAttive);

                Runtime.getRuntime().addShutdownHook(shutdownThread);
            }
        };
    }

    private boolean isTestMail(Pec pec, ArrayList<String> list) {
        return list.contains(pec.getIndirizzo());
    }

    private long getInitialDelay() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Rome"));
        ZonedDateTime nextRun = now.withHour(hourToStart).withMinute(0).withSecond(0);
        if (now.compareTo(nextRun) > 0) {
            nextRun = nextRun.plusDays(0);
        }

        Duration duration = Duration.between(now, nextRun);
        return duration.getSeconds();
    }

    public void filtraPecAttiveDiProdAndMantieniQuelleDiTest(ArrayList<Pec> pecAttive) {
        log.info("recupero le testMail...");
        String[] testMailArray = Arrays.stream(testMail.split("\\,")).toArray(String[]::new);
        log.info("... le converto in ArrayList...");
        ArrayList<String> testMailList = new ArrayList<>(Arrays.asList(testMailArray));
        log.info("...rimuovo da pecAttive quelle che non sono di test");
        pecAttive.removeIf(pec -> !isTestMail(pec, testMailList));
        log.info("Pec attive di test restanti#: " + pecAttive.size());
    }

    public void faiGliUploadWorker() {
        log.info("Creo l'uploadWorker");
        UploadWorker uploadWorker = beanFactory.getBean(UploadWorker.class);
        uploadWorker.setThreadName("uploadWorker");
        scheduledThreadPoolExecutor.scheduleWithFixedDelay(uploadWorker, 0, 5, TimeUnit.SECONDS);
        log.info(uploadWorker.getThreadName() + " schedulato correttamente");
    }

    public void faiGliImapWorker(ArrayList<Pec> pecAttive, Applicazione applicazione) {
        log.info("creazione degli IMAPWorker eseguita sulle caselle");
        for (int i = 0; i < pecAttive.size(); i++) {
            IMAPWorker imapWorker = beanFactory.getBean(IMAPWorker.class);
            imapWorker.setThreadName("IMAP_" + pecAttive.get(i).getIndirizzo());
            imapWorker.setIdPec(pecAttive.get(i).getId());
            imapWorker.setApplicazione(applicazione);
            scheduledThreadPoolExecutor.scheduleWithFixedDelay(imapWorker, i + 2, Integer.valueOf(imapDelay), TimeUnit.SECONDS);
            log.info(imapWorker.getThreadName() + " su PEC " + pecAttive.get(i).getIndirizzo() + " schedulato correttamente");
        }
        log.info("creazione degli IMAPWorker eseguita con successo");
    }

    public void faiGliImapWorkerDiRiconciliazione(ArrayList<Pec> pecAttive, Applicazione applicazione) {
        log.info("creazione degli IMAPCheckWorker di check sulle caselle");
        for (int i = 0; i < pecAttive.size(); i++) {
            log.info(pecAttive.get(i).getIndirizzo());
            IMAPWorkerChecker imapWorkerChecker = beanFactory.getBean(IMAPWorkerChecker.class);
            imapWorkerChecker.setThreadName("IMAP_CHECK_" + pecAttive.get(i).getIndirizzo());
            imapWorkerChecker.setIdPec(pecAttive.get(i).getId());
            imapWorkerChecker.setApplicazione(applicazione);
            scheduledThreadPoolExecutor.scheduleAtFixedRate(imapWorkerChecker, getInitialDelay(), TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
            log.info(imapWorkerChecker.getThreadName() + " su PEC " + pecAttive.get(i).getIndirizzo() + " schedulato correttamente");
        }
        log.info("creazione degli IMAPWorker per ogni casella PEC attiva...");
    }

    public void faiGliSMTPWorker(ArrayList<Pec> pecAttive) {
        log.info("creazione degli SMTPWorker per ogni casella PEC attiva...");
        for (int i = 0; i < pecAttive.size(); i++) {
            SMTPWorker smtpWorker = beanFactory.getBean(SMTPWorker.class);
            smtpWorker.setThreadName("SMTP_" + pecAttive.get(i).getIndirizzo());
            smtpWorker.setIdPec(pecAttive.get(i).getId());
            scheduledThreadPoolExecutor.scheduleWithFixedDelay(smtpWorker, i * 2, Integer.valueOf(smtpDelay), TimeUnit.SECONDS);
            log.info(smtpWorker.getThreadName() + " su PEC " + pecAttive.get(i).getIndirizzo() + " schedulato correttamente");
        }
        log.info("creazione degli SMTPWorker eseguita con successo");
    }

}
