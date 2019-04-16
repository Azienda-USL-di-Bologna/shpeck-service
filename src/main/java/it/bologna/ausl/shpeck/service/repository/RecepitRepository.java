package it.bologna.ausl.shpeck.service.repository;

import it.bologna.ausl.model.entities.shpeck.Recepit;
import it.bologna.ausl.model.entities.shpeck.projections.generated.RecepitWithPlainFields;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 *
 * @author Salo
 */
@RepositoryRestResource(collectionResourceRel = "recepit", path = "recepit", exported = false, excerptProjection = RecepitWithPlainFields.class)
public interface RecepitRepository extends JpaRepository<Recepit, Integer>{
    
}
