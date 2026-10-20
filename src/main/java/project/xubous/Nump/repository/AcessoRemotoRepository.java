package project.xubous.Nump.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import project.xubous.Nump.model.AcessoRemoto;

@Repository
public interface AcessoRemotoRepository extends JpaRepository < AcessoRemoto, Long >
{
    Optional < AcessoRemoto > findByToken ( String token );
    Optional < AcessoRemoto > findByFerramentaToken ( String ferramentaToken );
    Optional < AcessoRemoto > findByIdAndOwnerEmail ( Long id, String ownerEmail );
    List < AcessoRemoto > findAllByOwnerEmailOrderByCreatedAtDesc ( String ownerEmail );
    boolean existsByIdAndOwnerEmail ( Long id, String ownerEmail );
    void deleteByIdAndOwnerEmail ( Long id, String ownerEmail );
}
