package it.bologna.ausl.shpeck.service.repository;

import it.bologna.ausl.model.entities.shpeck.MessageExtension;
import it.bologna.ausl.model.entities.shpeck.projections.generated.MessageExtensionWithPlainFields;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 *
 * @author Salo
 */
@RepositoryRestResource(collectionResourceRel = "messageextension",
        path = "messageextension", exported = false,
        excerptProjection = MessageExtensionWithPlainFields.class)
public interface MessageExtensionRepository extends JpaRepository<MessageExtension, Integer> {

}
