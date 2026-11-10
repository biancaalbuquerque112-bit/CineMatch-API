
package com.cinematch.api;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LoginController {

    private final JdbcTemplate jdbcTemplate;
    private final SessaoTokenService sessoes;

    public LoginController(
            JdbcTemplate jdbcTemplate,
            SessaoTokenService sessoes
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessoes = sessoes;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody Map<String, String> dados
    ) {

        if (dados == null) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "erro",
                            "Informe email e senha."
                    )
            );
        }

        String email = dados.get("email");
        String senha = dados.get("senha");

        if (email == null
                || senha == null
                || email.isBlank()
                || senha.isBlank()) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "erro",
                            "Informe email e senha."
                    )
            );
        }

        String sql = """
                SELECT id, nome, email, senha, bio,
                       intencao, nascimento,
                       identidade_genero, orientacao,
                       interesse, mostrar_informacoes,
                       permitir_sugestoes,
                       generos_favoritos, foto
                FROM usuario
                WHERE LOWER(email) = LOWER(?)
                """;

        var usuarios = jdbcTemplate.query(
                sql,
                (rs, numero) -> lerUsuario(rs),
                email.trim()
        );

        if (usuarios.isEmpty()) {
            return credenciaisInvalidas();
        }

        Map<String, Object> usuario =
                usuarios.get(0);

        String senhaArmazenada =
                (String) usuario.remove("senha");

        if (!SenhaSegura.verificar(
                senha,
                senhaArmazenada
        )) {
            return credenciaisInvalidas();
        }

        int usuarioId =
                (Integer) usuario.get("id");

        // Token criado somente após validar a senha.
        String token =
                sessoes.criarToken(usuarioId);

        usuario.put(
                "mensagem",
                "Login realizado com sucesso!"
        );

        usuario.put("token", token);

        return ResponseEntity.ok(usuario);
    }

    private ResponseEntity<Map<String, Object>>
            credenciaisInvalidas() {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(
                        Map.of(
                                "erro",
                                "Email ou senha incorretos."
                        )
                );
    }

    private Map<String, Object> lerUsuario(
            ResultSet rs
    ) throws SQLException {

        Map<String, Object> usuario =
                new LinkedHashMap<>();

        usuario.put(
                "id",
                rs.getInt("id")
        );

        usuario.put(
                "nome",
                rs.getString("nome")
        );

        usuario.put(
                "email",
                rs.getString("email")
        );

        usuario.put(
                "senha",
                rs.getString("senha")
        );

        usuario.put(
                "bio",
                rs.getString("bio")
        );

        usuario.put(
                "intencao",
                rs.getString("intencao")
        );

        java.sql.Date nascimento =
                rs.getDate("nascimento");

        usuario.put(
                "nascimento",
                nascimento == null
                        ? null
                        : nascimento.toLocalDate().toString()
        );

        usuario.put(
                "identidadeGenero",
                rs.getString("identidade_genero")
        );

        usuario.put(
                "orientacao",
                rs.getString("orientacao")
        );

        usuario.put(
                "interesse",
                rs.getString("interesse")
        );

        usuario.put(
                "mostrarInformacoes",
                rs.getBoolean("mostrar_informacoes")
        );

        Object sugestoes =
                rs.getObject("permitir_sugestoes");

        usuario.put(
                "permitirSugestoes",
                sugestoes == null
                        || rs.getBoolean(
                                "permitir_sugestoes"
                        )
        );

        usuario.put(
                "generosFavoritos",
                rs.getString("generos_favoritos")
        );

        usuario.put(
                "foto",
                rs.getString("foto")
        );

        return usuario;
    }
}
