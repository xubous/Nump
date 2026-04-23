package project.xubous.Nump.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import project.xubous.Nump.model.User;

public class UserDTO
{
    // ── Request: criação / cadastro ──────────────────────────────────────────
    public record RegisterRequest(

        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
        String name,

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
        String password
    ) {}

    // ── Request: atualização (senha é opcional) ──────────────────────────────
    public record UpdateRequest(

        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 2, max = 100)
        String name,

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email,

        // null = não alterar a senha
        @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
        String password,

        User.Role role
    ) {}

    // ── Request: login ───────────────────────────────────────────────────────
    public record LoginRequest(

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        String password
    ) {}

    // ── Response: nunca expõe a senha ────────────────────────────────────────
    public record UserResponse(
        Long id,
        String name,
        String email,
        User.Role role
    )
    {
        public static UserResponse from(User user)
        {
            return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
        }
    }

    // ── Response: login com token JWT ────────────────────────────────────────
    public record AuthResponse(
        String token,
        UserResponse user
    ) {}
}