package it.bologna.ausl.shpeck.service.repository;

import it.bologna.ausl.model.entities.shpeck.Address;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.MessageAddress;
import it.bologna.ausl.model.entities.shpeck.MessageTag;
import it.bologna.ausl.model.entities.shpeck.projections.generated.MessageAddressWithPlainFields;
import it.bologna.ausl.model.entities.shpeck.projections.generated.MessageTagWithPlainFields;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 *
 * @author Salo
 */
@RepositoryRestResource(collectionResourceRel = "messagetag", path = "messagetag", exported = false, excerptProjection = MessageTagWithPlainFields.class)
public interface MessageTagRepository extends JpaRepository<MessageTag, Integer> {

}
