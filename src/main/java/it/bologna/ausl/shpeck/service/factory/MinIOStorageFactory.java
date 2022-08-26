package it.bologna.ausl.shpeck.service.factory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoException;
import it.bologna.ausl.minio.manager.MinIOWrapper;
import it.bologna.ausl.model.entities.configurazione.ParametroAziende;
import it.bologna.ausl.mongowrapper.exceptions.MongoWrapperException;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.repository.ParametroAziendeRepository;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author Salo
 */
@Component
public class MinIOStorageFactory {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ParametroAziendeRepository parametroAziendeRepository;

    private Map<String, Object> getMinIOConfigurationObject(Integer idAzienda) throws IOException, ShpeckServiceException {
        Map<String, Object> minIOConfigMap = null;
        List<ParametroAziende> minIOParametroAziendeListByIdAzienda = parametroAziendeRepository.getMinIOParametroAziendeListByIdAzienda(idAzienda);
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

    public MinIOWrapper getMinIOWrapper(Integer idAzienda) throws IOException,
            ShpeckServiceException, UnknownHostException, MongoException, MongoWrapperException {

        Map<String, Object> minIOConfigurationObject = getMinIOConfigurationObject(idAzienda);
        return getMinIOWrapper(minIOConfigurationObject);
    }

    private MinIOWrapper getMinIOWrapper(Map<String, Object> minIOConfigurationObject) throws IOException,
            ShpeckServiceException, UnknownHostException, MongoException, MongoWrapperException {
        MinIOWrapper wrapper = new MinIOWrapper((String) minIOConfigurationObject.get("DBDriver"),
                (String) minIOConfigurationObject.get("DBUrl"), (String) minIOConfigurationObject.get("DBUsername"),
                (String) minIOConfigurationObject.get("DBPassword"),
                (Integer) minIOConfigurationObject.get("maxPoolSize"), objectMapper);

        return wrapper;
    }
}
