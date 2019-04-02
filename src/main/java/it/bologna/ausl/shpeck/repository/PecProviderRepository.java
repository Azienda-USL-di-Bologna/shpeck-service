package it.bologna.ausl.shpeck.repository;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.baborg.PecProvider;
import it.bologna.ausl.model.entities.baborg.QPecProvider;
import it.bologna.ausl.model.entities.baborg.projections.generated.PecProviderWithPlainFields;
import it.bologna.ausl.model.entities.baborg.projections.generated.PecWithPlainFields;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 *
 * @author Salo
 */
@RepositoryRestResource(collectionResourceRel = "pecprovider", path = "pecprovider", exported = false, excerptProjection = PecProviderWithPlainFields.class)
public interface PecProviderRepository extends JpaRepository<PecProvider, Integer>{
    
}
