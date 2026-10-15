package project.xubous.Nump.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import project.xubous.Nump.model.UploadLink;

@Repository
public interface UploadLinkRepository extends JpaRepository < UploadLink, Long >
{
    Optional < UploadLink > findByToken ( String token );
    List < UploadLink > findAllByOwnerEmailOrderByCreatedAtDesc ( String ownerEmail );
    boolean existsByIdAndOwnerEmail ( Long id, String ownerEmail );
    void deleteByIdAndOwnerEmail ( Long id, String ownerEmail );
}
