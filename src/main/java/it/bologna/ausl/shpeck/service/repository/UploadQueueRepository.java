package it.bologna.ausl.shpeck.service.repository;

import it.bologna.ausl.model.entities.shpeck.RawMessage;
import it.bologna.ausl.model.entities.shpeck.UploadQueue;
import it.bologna.ausl.model.entities.shpeck.projections.generated.UploadQueueWithPlainFields;
import java.util.ArrayList;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * per convenzione nostra, collectionResourceRel e path devono avere lo stesso
 * nome tutto in minuscolo
 */
@RepositoryRestResource(collectionResourceRel = "uploadqueue", path = "uploadqueue", exported = false, excerptProjection = UploadQueueWithPlainFields.class)
public interface UploadQueueRepository extends JpaRepository<UploadQueue, Integer> {

//    @Query("SELECT u FROM UploadQueue u INNER JOIN FETCH u.idRawMessage r INNER JOIN FETCH r.idMessage m INNER JOIN FETCH m.idPec p INNER JOIN FETCH p.idAziendaRepository a WHERE u.uploaded = :uploaded")
//    ArrayList<UploadQueue> getFromUploadQueue(@Param("uploaded") Boolean uploaded);
    //public ArrayList<UploadQueue> findByUploaded(Boolean uploaded);
    //@Query("select u.id from UploadQueue u where u.uploaded = false")
    @Query(value = "select id from shpeck.upload_queue where uploaded = false", nativeQuery = true)
    public ArrayList<Integer> getIdToUpload();

    @Query(value = "select u.* from shpeck.upload_queue u, shpeck.raw_messages r where r.id = u.id_raw_message and r.id_message = ?1", nativeQuery = true)
    public UploadQueue getIdUploadQueueByIdMessage(Integer idMessage);

    public UploadQueue findByIdRawMessage(RawMessage rawMessage);

    @Query(value = "select id from shpeck.upload_queue where uploaded = true order by id", nativeQuery = true)
    public ArrayList<Integer> getIdToDelete();

    @Query(value = "select m.uuid_repository from shpeck.messages m join shpeck.raw_messages r on m.id = r.id_message join shpeck.upload_queue u on u.id_raw_message = r.id where u.id = ?1", nativeQuery = true)
    public String getUuidRepository(Integer idUpload);

}
