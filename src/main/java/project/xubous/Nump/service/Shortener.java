package project.xubous.Nump.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class Shortener
{
    private final String baseUrl = "http://localhost:8080/files/r/";

    public String generateToken ( )
    {
        return UUID.randomUUID ( ).toString ( ).substring ( 0, 8 );
    }

    public String generateDownloadUrl ( String token )
    {
        return baseUrl + token;
    }
}