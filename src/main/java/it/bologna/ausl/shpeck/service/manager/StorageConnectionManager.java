//package it.bologna.ausl.shpeck.service.manager;
//
///**
// *
// * @author spritz
// */
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.mongodb.MongoException;
//import it.bologna.ausl.model.entities.baborg.Azienda;
//import it.bologna.ausl.model.entities.baborg.AziendaParametriJson;
//import it.bologna.ausl.mongowrapper.MongoWrapper;
//import it.bologna.ausl.mongowrapper.exceptions.MongoWrapperException;
//import it.bologna.ausl.shpeck.service.repository.AziendaRepository;
//import java.io.IOException;
//import java.net.UnknownHostException;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//@Service
//public final class StorageConnectionManager {
//    
//    @Autowired
//    AziendaRepository aziendaRepository;
//    
//    @Autowired
//    ObjectMapper objectMapper;
//    
//    private final Map<String, MongoWrapper> storageConnectionMap;    
//
//    public StorageConnectionManager() throws IOException, UnknownHostException, MongoException, MongoWrapperException {
//        
//        storageConnectionMap = new HashMap<>();
//        
//        List<Azienda> aziende = aziendaRepository.findAll();
//        for (Azienda azienda : aziende) {
//            AziendaParametriJson aziendaConnParams = AziendaParametriJson.parse(objectMapper, aziendaRepository.getOne(azienda.getId()).getParametri());
//            MongoWrapper mongoWrapper = new MongoWrapper(aziendaConnParams.getMongoConnectionString());
//            storageConnectionMap.put(azienda.getCodice(), mongoWrapper);
//        }
//    }
//    
//    
//    public MongoWrapper getStorageConnection(String codiceAzienda){
//        MongoWrapper mongoWrapper = storageConnectionMap.get(codiceAzienda);
//        return mongoWrapper;
//    }
//}
