package it.bologna.ausl.shpeck.service.repository;

/**
 *
 * @author spritz
 */
import it.bologna.ausl.model.entities.diagnostica.Report;
import it.bologna.ausl.model.entities.diagnostica.projections.generated.ReportWithPlainFields;
import java.util.List;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * per convenzione nostra, collectionResourceRel e path devono avere lo stesso
 * nome tutto in minuscolo
 */
@RepositoryRestResource(collectionResourceRel = "report", path = "report", exported = false, excerptProjection = ReportWithPlainFields.class)
public interface ReportRepository extends
        JpaRepository<Report, Integer> {

    public List<Report> findByTipologia(String tipologia);
}
