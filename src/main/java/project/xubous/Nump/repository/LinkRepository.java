package project.xubous.Nump.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import project.xubous.Nump.model.Link;

@Repository
public interface LinkRepository extends JpaRepository < Link, Long > 
{
        Optional < Link > findByToken ( String token );
}
