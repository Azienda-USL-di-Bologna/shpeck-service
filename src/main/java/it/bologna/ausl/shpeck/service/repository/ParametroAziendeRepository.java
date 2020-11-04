/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.bologna.ausl.shpeck.service.repository;

import it.bologna.ausl.model.entities.configuration.ParametroAziende;
import it.bologna.ausl.model.entities.configuration.QParametroAziende;
import it.bologna.ausl.model.entities.configuration.projections.generated.ParametroAziendeWithPlainFields;
import java.util.ArrayList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 *
 * @author Salo
 */
@RepositoryRestResource(collectionResourceRel = "parametroaziende", path = "parametroaziende", exported = false, excerptProjection = ParametroAziendeWithPlainFields.class)
public interface ParametroAziendeRepository extends JpaRepository<ParametroAziende, Integer> {

    @Query(value = "select * from configurazione.parametri_aziende where nome = 'minIOConfig' and id_aziende @> ARRAY[?1]", nativeQuery = true)
    public ArrayList<ParametroAziende> getMinIOParametroAziendeListByIdAzienda(Integer idAzienda);

}
