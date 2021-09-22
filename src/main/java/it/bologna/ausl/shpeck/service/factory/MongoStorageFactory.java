package it.bologna.ausl.shpeck.service.factory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoException;
import it.bologna.ausl.model.entities.baborg.Azienda;
import it.bologna.ausl.model.entities.baborg.AziendaParametriJson;
import it.bologna.ausl.model.entities.configuration.ParametroAziende;
import it.bologna.ausl.mongowrapper.MongoWrapper;
import it.bologna.ausl.mongowrapper.exceptions.MongoWrapperException;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.repository.ParametroAziendeRepository;
import it.bologna.ausl.shpeck.service.storage.MongoStorage;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author Salo
 */
@Component
public class MongoStorageFactory {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ParametroAziendeRepository parametroAziendeRepository;

    private Map<String, Object> getMinIOConfigurationObject(Integer idAzienda) throws IOException, ShpeckServiceException {
        Map<String, Object> minIOConfigMap = null;
        ArrayList<ParametroAziende> minIOParametroAziendeListByIdAzienda = parametroAziendeRepository.getMinIOParametroAziendeListByIdAzienda(idAzienda);
        if (minIOParametroAziendeListByIdAzienda.size() == 1) {
            for (ParametroAziende parametroAziende : minIOParametroAziendeListByIdAzienda) {
                minIOConfigMap = objectMapper.readValue(parametroAziende.getValore(), new TypeReference<Map<String, Object>>() {
                });
            }
        } else if (minIOParametroAziendeListByIdAzienda.size() > 1) {
            throw new ShpeckServiceException("Parametro minIOConfig doppio per l'azienda " + idAzienda);
        }
        return minIOConfigMap;
    }

    private Boolean getMinIOActive(Integer idAzienda) throws IOException, ShpeckServiceException {
        Boolean res = null;
        ArrayList<ParametroAziende> minIOParametroAziendeListByIdAzienda = parametroAziendeRepository.getMinIOActiveByIdAzienda(idAzienda);
        if (minIOParametroAziendeListByIdAzienda.size() == 1) {
            for (ParametroAziende parametroAziende : minIOParametroAziendeListByIdAzienda) {
                res = objectMapper.readValue(parametroAziende.getValore(), Boolean.class);
            }
        } else if (minIOParametroAziendeListByIdAzienda.size() > 1) {
            throw new ShpeckServiceException("Parametro mongoMinIOActive doppio per l'azienda " + idAzienda);
        }
        return res;
    }

    private MongoStorage getMinIOMongoWrapper(Azienda azienda, AziendaParametriJson.MongoParams mongoParams,
            Map<String, Object> minIOConfigurationObject, boolean mongoAndMinIOActive) throws IOException,
            ShpeckServiceException, UnknownHostException, MongoException, MongoWrapperException {
        MongoWrapper wrapper = MongoWrapper.getWrapper(mongoAndMinIOActive,
                mongoParams.getConnectionString(), (String) minIOConfigurationObject.get("DBDriver"),
                (String) minIOConfigurationObject.get("DBUrl"), (String) minIOConfigurationObject.get("DBUsername"),
                (String) minIOConfigurationObject.get("DBPassword"), azienda.getCodice(),
                (Integer) minIOConfigurationObject.get("maxPoolSize"), objectMapper);
        MongoStorage mongoStorage = new MongoStorage();
        mongoStorage.setMongo(wrapper);
        mongoStorage.setFolderPath(mongoParams.getRoot());
        return mongoStorage;
    }

    private AziendaParametriJson.MongoParams getMongoParams(Azienda azienda) throws IOException {
        AziendaParametriJson aziendaParams = AziendaParametriJson.parse(objectMapper, azienda.getParametri());
        return aziendaParams.getMongoParams();
    }

    public MongoStorage getMongoStorageByAzienda(Azienda azienda) throws IOException, ShpeckServiceException, UnknownHostException, MongoWrapperException {
        MongoStorage mongoStorage;
        AziendaParametriJson.MongoParams mongoParams = getMongoParams(azienda);
        Map<String, Object> minIOConfigurationObject = getMinIOConfigurationObject(azienda.getId());
        Boolean mongoAndMinIOActive = getMinIOActive(azienda.getId());
        if (minIOConfigurationObject != null) {
            mongoStorage = getMinIOMongoWrapper(azienda, mongoParams, minIOConfigurationObject, mongoAndMinIOActive);
        } else {
            mongoStorage = new MongoStorage(mongoParams.getConnectionString(), mongoParams.getRoot());
        }
        return mongoStorage;
    }

}
