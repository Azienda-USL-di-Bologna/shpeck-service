package it.bologna.ausl.shpeck.service;

import it.bologna.ausl.model.entities.baborg.Azienda;
import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.configurazione.Applicazione;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.repository.AddressRepository;
import it.bologna.ausl.shpeck.service.repository.ApplicazioneRepository;
import it.bologna.ausl.shpeck.service.repository.AziendaRepository;
import it.bologna.ausl.shpeck.service.repository.MessageRepository;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import it.bologna.ausl.shpeck.service.worker.CheckUploadedRepositoryWorker;
import it.bologna.ausl.shpeck.service.worker.CheckerRecepitWorker;
import it.bologna.ausl.shpeck.service.worker.CleanerBackupWorker;
import it.bologna.ausl.shpeck.service.worker.CleanerWorker;
import it.bologna.ausl.shpeck.service.worker.IMAPWorker;
import it.bologna.ausl.shpeck.service.worker.ImportWorker;
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
import java.util.Calendar;
import java.util.Date;
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

    @Value("${cleaner-attivo}")
    Boolean cleanerAttivo;

    @Value("${hour-to-start}")
    Integer hourToStart;

    @Value("${days-back-spazzino}")
    Integer daysBackSpazzino;

    @Value("${mail.upload.number-of-threads}")
    Integer numberOfThreads;

    @Autowired
    MessageRepository messageRepository;

    @Autowired
    AziendaRepository aziendaRepository;

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

                log.info("Creo e schedulo l'Upload Worker");
                faiGliUploadWorker();

                log.info("Recupero le pec attive");
                ArrayList<Pec> pecAttive = pecRepository.findByAttivaTrueAndIdAziendaRepositoryNotNull();

//                filtraPecDiParmaProd(pecAttive);
                //               --- PER DEBUG ---
//                ArrayList<Pec> pecAttive = new ArrayList<>();
                //pecAttive.add(pecRepository.findById(1502).get());
                log.info("Pec attive #: " + pecAttive.size());
                if (testMode) {
                    log.info("CHECK TEST MODE POSITIVO, uso solo le pec di test");
                    filtraPecAttiveDiProdAndMantieniQuelleDiTest(pecAttive);
                }

                if (cleanerAttivo) {
                    log.info("Schedulo e accodo il CleanerWorker");
                    accodaCleanerWorker();
                    log.info("Schedulo e accodo il CleanerBackupWorker");
                    accodaCleanerBackupWorker();
                }

                faiGliImapWorker(pecAttive, applicazione);
                faiGliSMTPWorker(pecAttive);
                accodaCheckerRecepitWorker();

                avviaImportWorker();

                Runtime.getRuntime().addShutdownHook(shutdownThread);
            }

            /**
             * SOLO PER TEST IN LOCALE
             */
//            public void run(String... args) throws ShpeckServiceException {
//
//                log.info(". entrato nel run .");
//
//                log.info("Recupero l'applicazione");
//                Applicazione applicazione = applicazioneRepository.findById(idApplicazione);
//
//                log.info("Creo e schedulo l'Upload Worker");
//                //faiGliUploadWorker();
//
////                filtraPecDiParmaProd(pecAttive);
//                //               --- PER DEBUG ---
//                ArrayList<Pec> pecAttive = new ArrayList<>();
//                pecAttive.add(pecRepository.findById(478).get());
////                log.info("Pec attive #: " + pecAttive.size());
////                if (testMode) {
////                    log.info("CHECK TEST MODE POSITIVO, uso solo le pec di test");
////                    filtraPecAttiveDiProdAndMantieniQuelleDiTest(pecAttive);
////                }
////                if (cleanerAttivo) {
////                    log.info("Schedulo e accodo il CleanerWorker");
////                    accodaCleanerWorker();
////                    log.info("Schedulo e accodo il CleanerBackupWorker");
////                    accodaCleanerBackupWorker();
////                }
//                faiGliImapWorker(pecAttive, applicazione);
////                faiGliSMTPWorker(pecAttive);
////                accodaCheckerRecepitWorker();
//
////                avviaImportWorker();
//                Runtime.getRuntime().addShutdownHook(shutdownThread);
//            }
        };
    }

    private boolean isTestMail(Pec pec, ArrayList<String> list) {
        return list.contains(pec.getIndirizzo());
    }

    /**
     * Mi restituisce la data di due settimana fa da ora
     */
    public Date getTheseDaysAgoDate(Integer numberOfDays) {
        log.info("getTheseDaysAgoDate");
        log.info("tolgo " + numberOfDays);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        log.info("da: " + calendar.getTime().toString());
        calendar.add(Calendar.DAY_OF_YEAR, -numberOfDays);
        Date date = calendar.getTime();
        log.info("ritorno: " + date.toString());
        return date;
    }

    /**
     * Mi restituisce la data di due settimana fa da ora
     */
    public Date getTwoWeeksAgoDate() {
        log.info("getTwoWeeksAgoDate");
        int noOfDays = 14; //i.e two weeks
        log.info("tolgo " + noOfDays);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        log.info("da: " + calendar.getTime().toString());
        calendar.add(Calendar.DAY_OF_YEAR, -noOfDays);
        Date date = calendar.getTime();
        log.info("ritorno: " + date.toString());
        return date;
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

    public void filtraPecDiParmaProd(ArrayList<Pec> pecAttive) {
        log.info("Filtro via le pec relative alle aziende di Parma");
        Azienda auslParma = aziendaRepository.findByCodice("102");
        Azienda aospParma = aziendaRepository.findByCodice("902");
        for (Pec pec : pecAttive) {
            if (pec.getIdAziendaRepository().getId().equals(auslParma.getId())
                    || pec.getIdAziendaRepository().getId().equals(aospParma.getId())) {
                log.info("(tolgo " + pec.getIndirizzo() + " perch?? ?? di Parma");
                pecAttive.remove(pec);
            }
        }
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

        for (int i = 0; i < numberOfThreads; i++) {
            UploadWorker uploadWorker = beanFactory.getBean(UploadWorker.class);
            uploadWorker.setThreadName("uploadWorker" + i);
            uploadWorker.setIdentifier(i);
            scheduledThreadPoolExecutor.scheduleWithFixedDelay(uploadWorker, i + 3, 5, TimeUnit.SECONDS);
        }
        log.info("creazione degli UploadWorker eseguita con successo");

        log.info("Creo CheckUploadedRepositoryWorker");
        CheckUploadedRepositoryWorker c = beanFactory.getBean(CheckUploadedRepositoryWorker.class);
        c.setThreadName("checkUploadedRepositoryWorker");
        scheduledThreadPoolExecutor.scheduleWithFixedDelay(c, 0, 1, TimeUnit.HOURS);
        log.info(c.getThreadName() + " schedulato correttamente");
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

    public void accodaCleanerWorker() {
        log.info("Creazione e schedulazione del worker di pulizia (CleanerWorker)");
        CleanerWorker cleanerWorker = beanFactory.getBean(CleanerWorker.class);
        cleanerWorker.setThreadName("cleanerWorker");
        cleanerWorker.setEndTime(getTheseDaysAgoDate(daysBackSpazzino));
        scheduledThreadPoolExecutor.scheduleAtFixedRate(cleanerWorker, getInitialDelay(), TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
        log.info(cleanerWorker.getThreadName() + " schedulato correttamente");
    }

    public void accodaCheckerRecepitWorker() {
        log.info("Creazione e schedulazione del worker di checker delle ricevute (CheckerRecepitWorker)");
        CheckerRecepitWorker checkerWorker = beanFactory.getBean(CheckerRecepitWorker.class);
        checkerWorker.setThreadName("checkerRecepitWorker");
        scheduledThreadPoolExecutor.scheduleAtFixedRate(checkerWorker, getInitialDelay(), TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
        log.info(checkerWorker.getThreadName() + " schedulato correttamente");
    }

    public void accodaCleanerBackupWorker() {
        log.info("Creazione e schedulazione del worker di pulizia della cartella di Backup");
        CleanerBackupWorker cleanerBackupWorker = beanFactory.getBean(CleanerBackupWorker.class);
        cleanerBackupWorker.setThreadName("cleanerBackupWorker");
        scheduledThreadPoolExecutor.scheduleAtFixedRate(cleanerBackupWorker, 0, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
        log.info(cleanerBackupWorker.getThreadName() + " schedulato correttamente");
    }

    public void avviaImportWorker() {
        log.info("creazione degli ImportWorker sulle caselle che necessitano importazione");
        // prendi le caselle attive che devono essere importate
        ArrayList<Pec> list = pecRepository.findByAttivaTrueAndIdAziendaRepositoryNotNullAndImportaCasellaTrue();
//        DEBUG:
//        ArrayList<Pec> list = new ArrayList<>();
//        list.add(pecRepository.findById(752).get());
        for (int i = 0; i < list.size(); i++) {
            ImportWorker importWorker = beanFactory.getBean(ImportWorker.class);
            importWorker.setThreadName("IMPORT_" + list.get(i).getIndirizzo());
            importWorker.setIdPec(list.get(i).getId());
            scheduledThreadPoolExecutor.schedule(importWorker, i + 5, TimeUnit.SECONDS);
            log.info(importWorker.getThreadName() + " su PEC " + list.get(i).getIndirizzo() + " schedulato correttamente");
        }
        log.info("creazione degli ImportWorker eseguita con successo");
    }

}
