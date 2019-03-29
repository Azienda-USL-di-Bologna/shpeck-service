//package it.bologna.ausl.shpeck.test;
//
//
//import it.bologna.ausl.shpeck.service.exceptions.MailMessageException;
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileNotFoundException;
//import java.io.InputStream;
//import java.util.Date;
//import java.util.Enumeration;
//import java.util.Properties;
//import javax.mail.Address;
//import javax.mail.Header;
//import javax.mail.MessagingException;
//import javax.mail.Session;
//import javax.mail.internet.MimeMessage;
//import it.bologna.ausl.shpeck.service.handlers.EmlHandler;
//import it.bologna.ausl.shpeck.service.objects.MailMessage;
//import javax.activation.DataHandler;
//import javax.mail.Part;
//import javax.mail.internet.MimeMultipart;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
///*
// * To change this license header, choose License Headers in Project Properties.
// * To change this template file, choose Tools | Templates
// * and open the template in the editor.
// */
//
///**
// *
// * @author Salo
// */
//public class SalTestClass {
//    private static String path1 = "C:\\Users\\Utente\\Downloads\\219580_12 Mar 2019 10 38 09 GMT Per conto di  l.salomone@nsi.it posta-certificata@legalmail.it.eml";
//    private static String path2 = "C:\\Users\\Utente\\Downloads\\219579_12 Mar 2019 10 38 06 GMT Salomone Lorenzo - NSI L.Salomone@nsi.it.eml";
//    private static String path3 = "C:\\Users\\Utente\\Downloads\\219582_13 Mar 2019 08 05 37 GMT middleware.pec@pec.ausl.bologna.it.eml";
//    private static String path4 = "C:\\Users\\Utente\\Downloads\\219594_13 Mar 2019 13 43 28 GMT middleware.pec@pec.ausl.bologna.it.eml";
//    private static String[] paths = {path1, path2, path3, path4};
//    private static File file;
//    static Logger log = LoggerFactory.getLogger(SalTestClass.class);
//    
//    
//    public static void printMimeMessage(MimeMessage m) throws MessagingException{
//        System.out.println("getMessageID " + m.getMessageID());
//        System.out.println("getContentID " + m.getContentID());
//        System.out.println("getContentMD5 " + m.getContentMD5());
//        System.out.println("getContentType " + m.getContentType());
//        System.out.println("getDescription " + m.getDescription());
//        System.out.println("getDisposition " + m.getDisposition());
//        System.out.println("getEncoding " + m.getEncoding());
//        System.out.println("getFileName " + m.getFileName());
//        System.out.println("getSubject " + m.getSubject());
//        
//        System.out.println("getAllHeaders " + "...");
//        Enumeration<Header> e1 = m.getAllHeaders();
//        while (e1.hasMoreElements()) {
//            Header e = e1.nextElement();
//            System.out.println("\t" + e.getName() + " => " + e.getValue());
//        }
//        
//        System.out.println("getAllHeaderLines " + "...");
//        Enumeration<String> e2 = m.getAllHeaderLines();
//        while(e2.hasMoreElements())
//            System.out.println("\t" + e2.nextElement());
//        
//        System.out.println("getContentLanguage " + m.getContentLanguage()[0]);
//        System.out.println("getSize " + m.getSize());
//        System.out.println("getReceivedDate " + m.getReceivedDate());
//        //System.out.println("getSender().toString " + m.getSender().toString());
//        System.out.println("getFrom() " + " ... ");
//        for (Address add : m.getFrom()) {
//            System.out.println("\t" + add.toString());
//        }
//    }
//    
//    public static void stampaUnPoStoMessaggio(MailMessage m) throws MailMessageException{
//        Address[] from, to, cc, reply_to;
//        log.info("id " + m.getId());
//        from = m.getFrom();
//        log.info("FROM: " + m.getFrom());
//        for (Address a : from) 
//            log.info("\t" + a.toString());
//        log.info("TO: " + m.getTo());
//        to = m.getTo();
//        for (Address a : to) 
//            log.info("\t" + a.toString());
//
//        log.info("CC: " + m.getCc());
//        cc = m.getCc();
//        if(cc!=null){
//            for (Address a : cc) 
//                log.info("\t" + a.toString());
//        }
//
//        log.info("reply_to: " + m.getReply_to());
//        reply_to = m.getReply_to();
//        if(reply_to!=null){
//            for (Address a : reply_to) 
//                log.info("\t" + a.toString());
//        }
//        log.info("isPec \t" + m.getIsPec());
//        log.info("subject \t" + m.getSubject());
//        log.info("RECEIVE \t" + m.getReceive_date());
//        log.info("SEND_DATE \t" + m.getSend_date());
//        log.info("InReplyTo \t" + m.getInReplyTo());
//        
////        log.info("Leggo il raw message");
////        log.info("***************BEGIN RAW MESSAGE*********************\n" + m.getRaw_message());
////        log.info("***************END OF RAW MESSAGE********************");
//    }
//    
//    public static void metodoDiTest() throws FileNotFoundException, MessagingException{
//        try{
//            log.info("[*][*][*]metodoDiTest()[*][*][*]");
//            for (String path : paths) {
//                log.info("[][][][][][][][][][][][][][][][][][][][][][][][][][][][][][]");
//                log.info("[][][][][][][ verifico " + path + " ][][][][][][]");
//                log.info("[][][][][][][][][][][][][][][][][][][][][][][][][][][][][][]");
//                file = new File(path);
//                EmlHandler eh = new EmlHandler(file);
//                MailMessage m = eh.getMyMailMessage();
//                log.info("EVVIVA!!! Quanti allegati ho?\t" + eh.getAttachmentCount());
//                //stampaUnPoStoMessaggio(m);
//                log.info("[\\][\\][\\][\\][\\][\\][\\][\\][\\][\\][\\][\\][\\][\\][\\][\\]");
//            }
//            log.info("[*][*][*][*][*][*][*][*][*][*]");
//            
//        }
//        catch(Exception e){
//            log.error("!!!! AHIA ECCEZZIONE " + e.toString());
//            e.printStackTrace();
//        }
//    }
//    
//    public static void main(String[] args) throws FileNotFoundException, MessagingException{
//        log.info("Test Partito! -> " + new Date());
//        metodoDiTest();
//        System.exit(0);
//    }
//}
