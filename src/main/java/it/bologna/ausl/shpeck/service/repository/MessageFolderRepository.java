package it.bologna.ausl.shpeck.service.repository;

import it.bologna.ausl.model.entities.shpeck.MessageFolder;
import it.bologna.ausl.model.entities.shpeck.projections.generated.MessageFolderWithPlainFields;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 *
 * @author spritz
 */
@RepositoryRestResource(collectionResourceRel = "messagefolder", path = "messagefolder", exported = false, excerptProjection = MessageFolderWithPlainFields.class)
public interface MessageFolderRepository extends JpaRepository<MessageFolder, Integer> {

}
