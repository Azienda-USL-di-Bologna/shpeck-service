package it.bologna.ausl.shpeck.service.repository;

import it.bologna.ausl.model.entities.baborg.Azienda;
import it.bologna.ausl.model.entities.shpeck.UploadQueue;
import it.bologna.ausl.model.entities.shpeck.projections.generated.UploadQueueWithPlainFields;
import java.util.ArrayList;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * per convenzione nostra, collectionResourceRel e path devono avere lo stesso
 * nome tutto in minuscolo
 */
@RepositoryRestResource(collectionResourceRel = "uploadqueue", path = "uploadqueue", exported = false, excerptProjection = UploadQueueWithPlainFields.class)
public interface UploadQueueRepository extends JpaRepository<UploadQueue, Integer> {
    
    @Query("SELECT u FROM UploadQueue u INNER JOIN FETCH u.idRawMessage r INNER JOIN FETCH r.idMessage m INNER JOIN FETCH m.idPec p INNER JOIN FETCH p.idAziendaRepository a WHERE u.uploaded = :uploaded and m.inOut = :in_out")
    ArrayList<UploadQueue> getFromUploadQueue(@Param("uploaded") Boolean uploaded, @Param("in_out") String inOut);
}



