package it.bologna.ausl.shpeck.service.repository;

import it.bologna.ausl.model.entities.shpeck.RawMessage;
import it.bologna.ausl.model.entities.shpeck.projections.generated.RawMessageWithPlainFields;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * per convenzione nostra, collectionResourceRel e path devono avere lo stesso
 * nome tutto in minuscolo
 */
@RepositoryRestResource(collectionResourceRel = "rawmessage", path = "rawmessage", exported = false, excerptProjection = RawMessageWithPlainFields.class)
public interface RawMessageRepository extends
        JpaRepository<RawMessage, Integer> {
}
