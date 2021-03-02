package it.bologna.ausl.shpeck.service.repository;

import it.bologna.ausl.model.entities.shpeck.Recepit;
import it.bologna.ausl.model.entities.shpeck.projections.generated.RecepitWithPlainFields;
import java.sql.ResultSet;
import java.util.ArrayList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 *
 * @author Salo
 */
@RepositoryRestResource(collectionResourceRel = "recepit", path = "recepit", exported = false, excerptProjection = RecepitWithPlainFields.class)
public interface RecepitRepository extends JpaRepository<Recepit, Integer>{
    
    @Query(value = "select * from shpeck.recepits r, shpeck.messages m2 " +
                    "where r.id = m2.id " +
                    "and r.recepit_type = 'ACCETTAZIONE' " +
                    "and m2.message_type = 'RECEPIT' " +
                    "and m2.id_related = ?1", nativeQuery = true)
    public ResultSet getRecepitAccettazione(int id);
    
}
