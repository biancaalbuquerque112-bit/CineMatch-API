
package com.cinematch.api;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CadastroController {

    private final JdbcTemplate jdbcTemplate;

    public CadastroController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/cadastro")
    public ResponseEntity<Map<String, String>> cadastrar(
            @RequestBody Map<String, String> dados) {

        String nome = dados.get("nome");
        String email = dados.get("email");
        String senha = dados.get("senha");
        String nascimentoTexto = dados.get("nascimento");

        if (nome == null || nome.isBlank()
                || email == null || email.isBlank()
                || senha == null || senha.length() < 6
                || nascimentoTexto == null) {

            return ResponseEntity.badRequest().body(
                Map.of("erro", "Preencha todos os campos. A senha deve ter pelo menos 6 caracteres.")
            );
        }

        nome = nome.trim();
        email = email.trim().toLowerCase(Locale.ROOT);

        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return ResponseEntity.badRequest().body(
                Map.of("erro", "Informe um e-mail válido.")
            );
        }

        LocalDate nascimento;

        try {
            nascimento = LocalDate.parse(nascimentoTexto);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(
                Map.of("erro", "Nascimento deve estar no formato AAAA-MM-DD.")
            );
        }

        int idade = Period.between(
            nascimento, LocalDate.now()
        ).getYears();

        if (idade < 18 || idade > 110) {
            return ResponseEntity.badRequest().body(
                Map.of("erro", "Cadastro permitido apenas para pessoas de 18 a 110 anos.")
            );
        }

        String sql =
            "INSERT INTO usuario " +
            "(nome, email, senha, idade, bio, intencao, nascimento) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try {
            jdbcTemplate.update(
                sql,
                nome,
                email,
                SenhaSegura.criar(senha),
                idade,
                "",
                "Amizades",
                java.sql.Date.valueOf(nascimento)
            );

            return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of(
                    "mensagem", "Conta criada com sucesso!"
                ));

        } catch (DataIntegrityViolationException e) {
            return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                    "erro", "Não foi possível cadastrar. Verifique os dados ou se o e-mail já está em uso."
                ));
        }
    }
}
