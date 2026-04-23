package project.xubous.Nump.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import project.xubous.Nump.dto.UserDTO.*;
import project.xubous.Nump.service.UserService;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/users")
public class UserController
{
    private final UserService userService;

    public UserController(UserService userService)
    {
        this.userService = userService;
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req)
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req)
    {
        return ResponseEntity.ok(userService.login(req));
    }

    // ── CRUD (requer autenticação) ────────────────────────────────────────────

    @GetMapping                          // ADMIN only (ver SecurityConfig)
    public ResponseEntity<List<UserResponse>> getAll()
    {
        return ResponseEntity.ok(userService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id)
    {
        return ResponseEntity.ok(userService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdateRequest req)
    {
        return ResponseEntity.ok(userService.update(id, req));
    }

    @DeleteMapping("/{id}")              // ADMIN only (ver SecurityConfig)
    public ResponseEntity<Void> delete(@PathVariable Long id)
    {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}