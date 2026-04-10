package project.xubous.Nump.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import project.xubous.Nump.model.File;

@Repository // marca como componente de repositório — o Spring a detecta e gerencia automaticamente
// JpaRepository<File, Long> já fornece findAll(), findById(), save(), deleteById() gratuitamente
public interface FileRepository extends JpaRepository < File, Long >
{
    // o Spring Data JPA gera a query automaticamente a partir do nome do método
    // findByToken → SELECT * FROM table_file WHERE token = ?
    // retorna Optional porque o token pode não existir no banco
    Optional < File > findByToken ( String token );
}