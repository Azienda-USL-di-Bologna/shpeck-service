package it.bologna.ausl.shpeck.service.repository;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Message;
import it.bologna.ausl.model.entities.shpeck.projections.generated.MessageWithPlainFields;
import java.util.List;
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

    List<Message> findByUuidMessageAndIdPecAndMessageType(String uuid, Pec pec, String messageType);

    @Modifying
    @Transactional
    @Query(value = "update shpeck.messages set message_status = ?1, update_time = now() where id = ?2", nativeQuery = true)
    void updateMessageStatus(String messageStatus, Integer id);

    @Modifying
    @Transactional
    @Query(value = "update shpeck.messages set uuid_repository = ?1, path_repository = ?2, name = ?3, update_time = now() where id = ?4", nativeQuery = true)
    void updateUuidAndPathMongoAndName(String uuidRepository, String pathRepository, String name, Integer id);

    @Modifying
    @Transactional
    @Query(value = "update shpeck.messages set id_related = ?2, update_time = now() where id = ?1", nativeQuery = true)
    void updateRelatedMessage(Integer id, Integer idRelated);

    @Query(value = "select count(m.id) from shpeck.upload_queue u join shpeck.raw_messages r on u.id_raw_message = r.id join shpeck.messages m on m.id = r.id_message where u.uploaded = true and m.uuid_repository is null", nativeQuery = true)
    Integer getNumberOfMessageUploadedWithNoRepositoryInMessage();

    @Modifying
    @Transactional
    @Query(value = "with xxx as (select m.id as message, r.id as raw_message, q.uuid, m.uuid_repository, q.path "
            + "from shpeck.messages m "
            + "join shpeck.raw_messages r on r.id_message = m.id "
            + "join shpeck.upload_queue q on q.id_raw_message = r.id "
            + "where m.uuid_repository is null "
            + "and q.uploaded = true) "
            + "update shpeck.messages "
            + "set uuid_repository = xxx.uuid, path_repository = xxx.path "
            + "from xxx "
            + "where id = xxx.message", nativeQuery = true)
    void fixNumberOfMessageUploadedWithNoRepositoryInMessage();

    @Query(value = "select count(*) from logs.get_logs(null, ?1, null, null, null, null, null, null)", nativeQuery = true)
    public Integer getRowFromKrint(String id);
}
