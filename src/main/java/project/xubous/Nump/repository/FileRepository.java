package project.xubous.Nump.repository;

import project.xubous.Nump.model.File;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileRepository extends JpaRepository < File, Long > {
    
    
}