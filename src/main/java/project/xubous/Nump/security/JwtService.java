package project.xubous.Nump.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService
{
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms:86400000}") // 24h padrão
    private long expirationMs;

    private SecretKey key()
    {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(String email)
    {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key())
                .compact();
    }

    public String extractEmail(String token)
    {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean isValid(String token)
    {
        try
        {
            Jwts.parser().verifyWith(key()).build().parseSignedClaims(token);
            return true;
        }
        catch (JwtException | IllegalArgumentException e)
        {
            return false;
        }
    }
}