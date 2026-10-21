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
                // frontend estático (shell público — a API continua protegida pelo JWT).
                // Arquivos são todos na raiz: index/login/register + css.
                .requestMatchers ( HttpMethod.GET, "/", "/index.html", "/login.html", "/register.html",
                        "/style.css", "/login.css", "/favicon.ico" ).permitAll ()
                .requestMatchers ( HttpMethod.GET,  "/files/r/**" ).permitAll ()
                // coleta (upload reverso do cliente, sem login) + página da coleta
                .requestMatchers ( HttpMethod.GET,  "/files/c/**", "/coleta.html" ).permitAll ()
                .requestMatchers ( HttpMethod.POST, "/files/c/**" ).permitAll ()
                // consentimento de acesso remoto (cliente, sem login) + página
                // consentimento de acesso remoto (cliente, sem login) + página.
                // ATENÇÃO: "/acessos/**" também casa com "/acessos" (PathPattern),
                // o que liberaria a LISTAGEM anonimamente — por isso os padrões
                // são explícitos aqui. GET /acessos (sem segmento) continua JWT.
                //  - GET  /acessos/{token}            = página de consentimento
                //  - POST /acessos/{token}/consentir  = registra o consentimento
                //  - GET  /acessos/{token}/encerrar.bat    = .bat que revoga o acesso
                //  - GET  /acessos/{token}/desinstalar.bat = .bat que remove o RustDesk
                //  - GET  /acessos/ferramenta/{t}.bat   = ferramenta RustDesk
                //  - GET  /acessos/ferramenta/{t}/script.ps1 = script PowerShell
                //  - POST /acessos/ferramenta/{t}/id    = o script avisa o ID
                //  - GET  /acessos/ferramenta/{f}/senha = senha de uso único
                .requestMatchers ( HttpMethod.GET,  "/acessos/*", "/acessos/*/*.bat",
                        "/acessos/ferramenta/*", "/acessos/ferramenta/*/*",
                        "/consentimento.html" ).permitAll ()
                .requestMatchers ( HttpMethod.POST, "/acessos/*/consentir",
                        "/acessos/ferramenta/*/id" ).permitAll ()
                // admin
                .requestMatchers ( HttpMethod.GET,    "/users" ).hasRole ( "ADMIN" )
                .requestMatchers ( HttpMethod.DELETE, "/users/**" ).hasRole ( "ADMIN" )
                // qualquer outra rota exige autenticação
                .requestMatchers("/actuator/health").permitAll()
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
            "http://localhost:8080",        // testes locais direto no Spring
            "http://localhost:8081"         // porta padrão local do Spring (server.port)
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
