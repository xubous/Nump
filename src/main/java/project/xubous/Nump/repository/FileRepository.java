package project.xubous.Nump.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import project.xubous.Nump.model.File;

@Repository
public interface FileRepository extends JpaRepository < File, Long >
{
    Optional < File > findByToken ( String token );
}