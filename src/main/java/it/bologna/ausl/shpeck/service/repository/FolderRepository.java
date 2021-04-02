package it.bologna.ausl.shpeck.service.repository;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Folder;
import it.bologna.ausl.model.entities.shpeck.projections.generated.FolderWithPlainFields;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 *
 * @author spritz
 */
@RepositoryRestResource(collectionResourceRel = "folder", path = "folder", exported = false, excerptProjection = FolderWithPlainFields.class)
public interface FolderRepository extends JpaRepository<Folder, Integer> {

    List<Folder> findAllByIdPec(Pec pec);

    Folder findByIdPecAndType(Pec pec, String type);

    Folder findByIdPecAndFullnameInProvider(Pec pec, String name);

    Folder findByIdPecAndTypeAndName(Pec pec, String type, String name);
}
