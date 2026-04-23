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
}