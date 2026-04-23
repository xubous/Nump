package project.xubous.Nump.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import project.xubous.Nump.dto.UserDTO.*;
import project.xubous.Nump.model.User;
import project.xubous.Nump.repository.UserRepository;
import project.xubous.Nump.security.JwtService;

import java.util.List;

@Service
public class UserService
{
    private final UserRepository      userRepository;
    private final PasswordEncoder     passwordEncoder;
    private final JwtService          jwtService;
    private final AuthenticationManager authManager;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authManager)
    {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService      = jwtService;
        this.authManager     = authManager;
    }

    // ── Cadastro ─────────────────────────────────────────────────────────────
    public AuthResponse register(RegisterRequest req)
    {
        if (userRepository.existsByEmail(req.email()))
            throw new IllegalArgumentException("Email já cadastrado");

        var user = new User();
        user.setName(req.name());
        user.setEmail(req.email());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setRole(User.Role.USER);

        var saved = userRepository.save(user);
        var token = jwtService.generateToken(saved.getEmail());

        return new AuthResponse(token, UserResponse.from(saved));
    }

    // ── Login ─────────────────────────────────────────────────────────────────
    public AuthResponse login(LoginRequest req)
    {
        authManager.authenticate(
            new UsernamePasswordAuthenticationToken(req.email(), req.password())
        );

        var user  = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));
        var token = jwtService.generateToken(user.getEmail());

        return new AuthResponse(token, UserResponse.from(user));
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────
    public List<UserResponse> getAll()
    {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    public UserResponse getById(Long id)
    {
        return UserResponse.from(findOrThrow(id));
    }

    public UserResponse update(Long id, UpdateRequest req)
    {
        var user = findOrThrow(id);

        // Verifica se o novo email já pertence a outro usuário
        userRepository.findByEmail(req.email()).ifPresent(existing ->
        {
            if (!existing.getId().equals(id))
                throw new IllegalArgumentException("Email já está em uso");
        });

        user.setName(req.name());
        user.setEmail(req.email());

        if (req.password() != null && !req.password().isBlank())
            user.setPassword(passwordEncoder.encode(req.password()));

        if (req.role() != null)
            user.setRole(req.role());

        return UserResponse.from(userRepository.save(user));
    }

    public void delete(Long id)
    {
        findOrThrow(id);
        userRepository.deleteById(id);
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private User findOrThrow(Long id)
    {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: id=" + id));
    }
}