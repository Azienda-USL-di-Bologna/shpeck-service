package it.bologna.ausl.shpeck.service.repository;

import it.bologna.ausl.model.entities.configuration.Applicazione;
import it.bologna.ausl.model.entities.configuration.projections.generated.ApplicazioneWithPlainFields;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 *
 * @author Salo
 */
@RepositoryRestResource(collectionResourceRel = "applicazione", path = "applicazione", exported = false, excerptProjection = ApplicazioneWithPlainFields.class)
public interface ApplicazioneRepository extends JpaRepository<Applicazione, Integer> {

    Applicazione findById(String idApplicazione);
}
