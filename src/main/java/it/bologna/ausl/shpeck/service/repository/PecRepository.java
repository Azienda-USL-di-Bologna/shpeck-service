package it.bologna.ausl.shpeck.service.repository;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.baborg.projections.generated.PecWithPlainFields;
import java.util.ArrayList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 *
 * @author Salo
 */
@RepositoryRestResource(collectionResourceRel = "pec", path = "pec", exported = false, excerptProjection = PecWithPlainFields.class)
public interface PecRepository extends JpaRepository<Pec, Integer>{
    
    ArrayList<Pec> findByAttivaTrueAndIdAziendaRepositoryNotNull();
    
}
