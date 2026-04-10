package project.xubous.Nump.service;

import java.util.UUID; // classe do Java que gera identificadores únicos universais (UUID)
import org.springframework.stereotype.Service; // marca essa classe como um serviço gerenciado pelo Spring

@Service // o Spring cria e gerencia uma instância dessa classe automaticamente (injeção de dependência)
public class Shortener 
{
    // URL base da aplicação — prefixo de todos os links curtos gerados
    // está separado em dois: um para links normais, outro para arquivos
    private final String baseLinkUrl = "http://localhost:8080/links/r/";
    private final String baseFileUrl = "http://localhost:8080/files/r/";

    // gera um token aleatório de 8 caracteres
    // UUID.randomUUID() retorna algo como "550e8400-e29b-41d4-a716-446655440000"
    // .toString() converte para String
    // .substring(0, 8) pega só os 8 primeiros caracteres — ex: "550e8400"
    public String generateToken ( )
    {
        return UUID.randomUUID ( ).toString ( ).substring ( 0, 8 );
    }

    // monta a URL curta para um Link
    // ANTES (bug): recebia a URL longa e concatenava no lugar errado → "https://google.com/...http://localhost..."
    // AGORA: recebe o token de 8 chars e monta corretamente → "http://localhost:8080/links/r/a1b2c3d4"
    public String generateLinkUrl ( String token )
    {
        return baseLinkUrl + token; // ex: "http://localhost:8080/links/r/" + "a1b2c3d4"
    }

    // monta a URL curta para um File — funciona igual ao de links, mas aponta para /files/r/
    public String generateFileUrl ( String token )
    {
        return baseFileUrl + token; // ex: "http://localhost:8080/files/r/" + "a1b2c3d4"
    }
}