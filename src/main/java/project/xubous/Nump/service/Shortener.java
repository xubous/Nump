package project.xubous.Nump.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class Shortener 
{
    private final String url = "http://localhost:8080/links/";

    public String generateToken (  )
    {
        return UUID.randomUUID ( ).toString ( ).substring ( 0, 8 );
    }

    public String generateReducedLink ( String urlFull )
    {
        return urlFull + url;
    }
}