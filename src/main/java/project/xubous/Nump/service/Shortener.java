package project.xubous.Nump.service;

import java.security.SecureRandom;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class Shortener
{
    // sem 0/O e 1/I uma vez que o token pode ser digitado à mão
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SecureRandom random = new SecureRandom ( );

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public String generateToken ( )
    {
        return UUID.randomUUID ( ).toString ( ).substring ( 0, 8 );
    }

    // Token do link de acesso remoto (F2): ele dá acesso à MÁQUINA do cliente,
    // então é longo e aleatório (SecureRandom) — 24 chars ~ 2^120 combinações,
    // inviável de adivinhar. Os tokens de arquivo/coleta continuam curtos (8).
    public String generateTokenSeguro ( )
    {
        StringBuilder token = new StringBuilder ( );

        for ( int i = 0; i < 24; i++ )
        {
            token.append ( ALFABETO.charAt ( random.nextInt ( ALFABETO.length ( ) ) ) );
        }

        return token.toString ( );
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

    // Link de consentimento de acesso remoto: o cliente abre e autoriza o RustDesk
    public String generateConsentUrl ( String token )
    {
        return baseUrl + "/acessos/" + token;
    }
}
