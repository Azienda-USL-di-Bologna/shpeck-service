package it.bologna.ausl.shpeck.service.test;

///*
// * To change this license header, choose License Headers in Project Properties.
// * To change this template file, choose Tools | Templates
// * and open the template in the editor.
// */
//package it.bologna.ausl.shpeck.test;
//
//import it.bologna.ausl.model.entities.baborg.Pec;
//import it.bologna.ausl.model.entities.shpeck.Message;
//import it.bologna.ausl.model.entities.shpeck.Recepit;
//import it.bologna.ausl.model.entities.shpeck.Recepit.RecepitType;
//import it.bologna.ausl.shpeck.service.objects.GenericRepository;
//import java.time.LocalDateTime;
//import java.util.Date;
//import java.util.Locale;
//import javax.persistence.EntityManager;
//import javax.persistence.PersistenceContext;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.format.datetime.joda.LocalDateTimeParser;
//
///**
// *
// * @author Salo
// */
//public class RecepitTest {
//
//    /**
//     * @param args the command line arguments
//     */
//    
//    @PersistenceContext
//    public static EntityManager em;
//    
//    @Autowired
//    public static GenericRepository gr;
//    
//    public static void main(String[] args) {
//        Recepit r = new Recepit();
//        r.setId(123);
//        r.setIdPec(new Pec());
//        // Integer id, String uuidMessage, int idMailConfig, Date createTime, Date updateTime, boolean isPec, int nAttachments, Date receiveDate, String recepitType
//        Recepit r2 = new Recepit(1, "xcijladftgj", new Pec(), null, null, "Soccia", Message.MessageStatus.RECEIVED.toString(),null, 
//                LocalDateTime.parse(new Date().toString()), null, null, true, 4, null, null, null, null,RecepitType.ACCETTAZIONE);
//        r2.equals(r);
//        System.out.println("r " + r.toString());
//        System.out.println("r2 " + r2.toString());
//        System.out.println("r2.equals(r)? " + r2.equals(r));
//        Recepit r3 = new Recepit();
//        r3.setUuidMessage("uiuouauo");
//        r3.setId(1);
//        r3.setSubject("Prova di salvataggio");
//        r3.setInOut(Message.InOut.IN);
//        r3.setCreateTime(LocalDateTime.parse(new Date().toString()));
//        r3.setUpdateTime(LocalDateTime.parse(new Date().toString()));
//        r3.setMessageType(Message.MessageType.RICEVUTA);
//        r3.setIsPec(true);
//        r3.setNAttachments(0);
//        r3.setRecepitType(RecepitType.ACCETTAZIONE);
//        System.out.println("Mi faccio la transazione");
//        em.getTransaction().begin();
//        System.out.println("vedo si salvarlo");
//        em.persist(r3);
//        System.out.println("rollbacko");
//        em.getTransaction().rollback();
//    }
//    
//}
