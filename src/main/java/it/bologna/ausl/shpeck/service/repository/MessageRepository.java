package it.bologna.ausl.shpeck.service.repository;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.projections.generated.MessageWithPlainFields;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author Salo
 */
@RepositoryRestResource(collectionResourceRel = "message", path = "message", exported = false, excerptProjection = MessageWithPlainFields.class)
public interface MessageRepository extends JpaRepository<Message, Integer> {

    Message findByUuidMessageAndIsPecFalse(String uuid);

    Message findByUuidMessageAndIdPecAndMessageType(String uuid, Pec pec, String messageType);

    @Modifying
    @Transactional
    @Query(value = "update shpeck.messages set message_status = ?1 where id = ?2", nativeQuery = true)
    void updateMessageStatus(String messageStatus, Integer id);
}
