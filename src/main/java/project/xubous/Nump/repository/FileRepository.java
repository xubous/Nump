package project.xubous.Nump.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import project.xubous.Nump.model.File;

@Repository
public interface FileRepository extends JpaRepository < File, Long >
{
    Optional < File > findByToken ( String token );
    List < File > findAllByOwnerEmail ( String ownerEmail );
    Optional < File > findByIdAndOwnerEmail ( Long id, String ownerEmail );
    boolean existsByIdAndOwnerEmail ( Long id, String ownerEmail );
    void deleteByIdAndOwnerEmail ( Long id, String ownerEmail );
}