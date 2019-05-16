package it.bologna.ausl.shpeck.service.repository;

import it.bologna.ausl.model.entities.shpeck.Address;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.MessageAddress;
import it.bologna.ausl.model.entities.shpeck.projections.generated.MessageAddressWithPlainFields;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 *
 * @author Salo
 */
@RepositoryRestResource(collectionResourceRel = "messageaddress", path = "messageaddress", exported = false, excerptProjection = MessageAddressWithPlainFields.class)
public interface MessageAddressRepository extends JpaRepository<MessageAddress, Integer> {

    public MessageAddress findByIdMessageAndIdAddressAndAddressRole(Message message, Address address, String addressRoleType);
}
