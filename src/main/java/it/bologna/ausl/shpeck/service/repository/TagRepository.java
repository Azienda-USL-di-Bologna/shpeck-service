package it.bologna.ausl.shpeck.service.repository;

import it.bologna.ausl.model.entities.baborg.Azienda;
import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.baborg.projections.generated.AziendaWithPlainFields;
import it.bologna.ausl.model.entities.shpeck.Tag;
import it.bologna.ausl.model.entities.shpeck.projections.generated.TagWithPlainFields;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * per convenzione nostra, collectionResourceRel e path devono avere lo stesso
 * nome tutto in minuscolo
 */
@RepositoryRestResource(collectionResourceRel = "tag", path = "tag", exported = false, excerptProjection = TagWithPlainFields.class)
public interface TagRepository extends JpaRepository<Tag, Integer> {

    public Tag findByNameAndIdPec(String name, Pec pec);
}
