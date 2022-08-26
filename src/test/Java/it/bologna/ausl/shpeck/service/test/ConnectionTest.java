/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package it.bologna.ausl.shpeck.service.test;

import com.sun.mail.imap.IMAPStore;
import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.baborg.PecProvider;
import it.bologna.ausl.shpeck.service.repository.PecProviderRepository;
import it.bologna.ausl.shpeck.service.repository.PecRepository;
import it.bologna.ausl.shpeck.service.utils.ProviderConnectionHandler;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 *
 * @author a.trashani
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class ConnectionTest {
    
    @Autowired
    ProviderConnectionHandler providerConnectionHandler;
    
    @Autowired
    PecRepository pecRepository;
    
    @Autowired
    PecProviderRepository pecProviderRepository;
    
    
    @Test
    public void ProviderConnectionHandlerPropertyVerifier(){
        
        System.out.println("Property verifier test class");   
                
        //Pec pecForTest = pecRepository.getOne(1503);
        Pec pecForTest = pecRepository.findById(1503).get();
        
        PecProvider pecProvider = pecProviderRepository.findById(pecForTest.getIdPecProvider().getId()).get();
        
        pecForTest.setIdPecProvider(pecProvider);
        
        try {
            IMAPStore imapStore = providerConnectionHandler.createProviderConnectionHandler(pecForTest);
            
            if(imapStore.isConnected()){
                System.out.println("Is Connected");
            }else{
                try {
                    imapStore.connect();
                    System.out.println("Connection IS OK");
                } catch (MessagingException ex) {
                    Logger.getLogger(ConnectionTest.class.getName()).log(Level.SEVERE, null, ex);
                    System.out.println("Connection failed");
                }
               
            }
            
            
        } catch (NoSuchProviderException ex) {
            Logger.getLogger(ConnectionTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(ConnectionTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    
    }
        
}
