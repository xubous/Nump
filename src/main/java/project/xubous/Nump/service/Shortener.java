package project.xubous.Nump.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class Shortener
{
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public String generateToken ( )
    {
        return UUID.randomUUID ( ).toString ( ).substring ( 0, 8 );
    }

    public String generateDownloadUrl ( String token )
    {
        return baseUrl + "/files/r/" + token;
    }

    // Link de coleta (upload reverso): o cliente abre esta URL para enviar arquivo
    public String generateColetaUrl ( String token )
    {
        return baseUrl + "/files/c/" + token;
    }

    // Link de consentimento de acesso remoto: o cliente abre e autoriza o AnyDesk
    public String generateConsentUrl ( String token )
    {
        return baseUrl + "/acessos/" + token;
    }
}
