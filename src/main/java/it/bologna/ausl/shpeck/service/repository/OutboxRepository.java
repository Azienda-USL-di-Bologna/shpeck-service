package it.bologna.ausl.shpeck.service.repository;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.Outbox;
import it.bologna.ausl.model.entities.shpeck.projections.generated.OutboxWithPlainFields;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 *
 * @author Salo
 */
@RepositoryRestResource(collectionResourceRel = "outbox", path = "outbox", exported = false, excerptProjection = OutboxWithPlainFields.class)
public interface OutboxRepository extends JpaRepository<Outbox, Integer> {
    public List<Outbox> findByIdPec(Pec pec);
}
