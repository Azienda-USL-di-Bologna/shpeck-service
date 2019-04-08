package it.bologna.ausl.shpeck.service.repository;

import it.bologna.ausl.model.entities.baborg.Azienda;
import it.bologna.ausl.model.entities.baborg.projections.generated.AziendaWithPlainFields;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * per convenzione nostra, collectionResourceRel e path devono avere lo stesso
 * nome tutto in minuscolo
 */
@RepositoryRestResource(collectionResourceRel = "azienda", path = "azienda", exported = false, excerptProjection = AziendaWithPlainFields.class)
public interface AziendaRepository extends
        JpaRepository<Azienda, Integer> {
}
