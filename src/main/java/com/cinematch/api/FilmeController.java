
package com.cinematch.api;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/filmes")
public class FilmeController {

    private final JdbcTemplate jdbcTemplate;

    public FilmeController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Lista os filmes cadastrados no MySQL.
    @GetMapping
    public List<Map<String, Object>> listarFilmes() {

        String sql = """
                SELECT id, titulo, genero, ano,
                       capa, diretor, curtidas, nota
                FROM filme
                ORDER BY titulo
                """;

        return jdbcTemplate.queryForList(sql);
    }

    // Pesquisa filmes por título ou gênero.
    // Exemplo: /api/filmes/buscar?termo=comedia
    @GetMapping("/buscar")
    public List<Map<String, Object>> buscarFilmes(
            @RequestParam(defaultValue = "") String termo) {

        String sql = """
                SELECT id, titulo, genero, ano,
                       capa, diretor, curtidas, nota
                FROM filme
                WHERE LOWER(genero) LIKE LOWER(?)
                   OR LOWER(titulo) LIKE LOWER(?)
                ORDER BY titulo
                """;

        String busca = "%" + termo.trim() + "%";

        return jdbcTemplate.queryForList(
                sql,
                busca,
                busca
        );
    }
}
