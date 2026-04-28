package project.xubous.Nump.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig
{
    private final JwtAuthFilter      jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    public SecurityConfig ( JwtAuthFilter jwtAuthFilter, UserDetailsService userDetailsService )
    {
        this.jwtAuthFilter      = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain filterChain ( HttpSecurity http ) throws Exception
    {
        http
            .csrf ( AbstractHttpConfigurer::disable )
            .cors ( cors -> cors.configurationSource ( corsConfig () ) )
            .sessionManagement ( s -> s.sessionCreationPolicy ( SessionCreationPolicy.STATELESS ) )
            .authorizeHttpRequests ( auth -> auth
                // preflight OPTIONS sempre liberado
                .requestMatchers ( HttpMethod.OPTIONS, "/**" ).permitAll ()
                // rotas públicas
                .requestMatchers ( HttpMethod.POST, "/users/register", "/users/login" ).permitAll ()
                .requestMatchers ( HttpMethod.GET,  "/files/r/**" ).permitAll ()
                // admin
                .requestMatchers ( HttpMethod.GET,    "/users" ).hasRole ( "ADMIN" )
                .requestMatchers ( HttpMethod.DELETE, "/users/**" ).hasRole ( "ADMIN" )
                // qualquer outra rota exige autenticação
                .anyRequest ().authenticated ()
            )
            .addFilterBefore ( jwtAuthFilter, UsernamePasswordAuthenticationFilter.class );

        return http.build ();
    }

    @Bean
    public PasswordEncoder passwordEncoder ()
    {
        return new BCryptPasswordEncoder ();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider ()
    {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider ( userDetailsService );
        provider.setPasswordEncoder ( passwordEncoder () );
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager ( AuthenticationConfiguration config ) throws Exception
    {
        return config.getAuthenticationManager ();
    }

    @Bean
    public CorsConfigurationSource corsConfig ()
    {
        var config = new CorsConfiguration ();

        config.setAllowedOrigins ( List.of (
            "https://numpfm.vercel.app",   // produção no Vercel
            "http://localhost:5500",        // Live Server VSCode
            "http://127.0.0.1:5500",       // Live Server VSCode (variante)
            "http://localhost:3000",        // dev local genérico
            "http://localhost:8080"         // testes locais direto no Spring
        ) );

        config.setAllowedMethods ( List.of ( "GET", "POST", "PUT", "DELETE", "OPTIONS" ) );
        config.setAllowedHeaders ( List.of ( "*" ) );

        // allowCredentials(false) — o frontend usa Authorization: Bearer (header),
        // não cookies. Com false, origens específicas + wildcard headers funcionam sem conflito
        config.setAllowCredentials ( false );

        var source = new UrlBasedCorsConfigurationSource ();
        source.registerCorsConfiguration ( "/**", config );
        return source;
    }
}
