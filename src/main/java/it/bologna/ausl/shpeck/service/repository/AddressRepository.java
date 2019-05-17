package it.bologna.ausl.shpeck.service.repository;

import it.bologna.ausl.model.entities.shpeck.Address;
import it.bologna.ausl.model.entities.shpeck.projections.generated.AddressWithPlainFields;
import java.util.List;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * per convenzione nostra, collectionResourceRel e path devono avere lo stesso
 * nome tutto in minuscolo
 */
@RepositoryRestResource(collectionResourceRel = "address", path = "address", exported = false, excerptProjection = AddressWithPlainFields.class)
public interface AddressRepository extends JpaRepository<Address, Integer> {

    Address findByMailAddress(String mailAddress);

    @Query("SELECT a FROM Address a WHERE a.mailAddress in :mailAddresses")
    List<Address> getAddresses(@Param("mailAddresses") List<String> mailAddresses);
}
