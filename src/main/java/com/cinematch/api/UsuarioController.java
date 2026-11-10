
package com.cinematch.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final JdbcTemplate jdbcTemplate;

    public UsuarioController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Lista os usuários cadastrados no MySQL
    @GetMapping
    public List<Map<String, Object>> listarUsuarios() {
        return jdbcTemplate.queryForList(
            "SELECT id, nome FROM usuario ORDER BY nome"
        );
    }

    // Busca os dados de um usuário pelo ID
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> buscarUsuario(
            @PathVariable int id) {

        var usuarios = jdbcTemplate.queryForList(
            "SELECT id, nome, email, bio, intencao, nascimento, " +
            "identidade_genero, orientacao, interesse, " +
            "mostrar_informacoes, permitir_sugestoes, " +
            "generos_favoritos, foto " +
            "FROM usuario WHERE id = ?",
            id
        );

        if (usuarios.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(usuarios.get(0));
    }
}
