
package com.cinematch.api;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comentarios")
public class ComentarioController {

    private final JdbcTemplate jdbc;
    private final SessaoTokenService sessoes;

    public ComentarioController(
            JdbcTemplate jdbc,
            SessaoTokenService sessoes) {
        this.jdbc = jdbc;
        this.sessoes = sessoes;
    }

    // LISTAR COMENTARIOS DE UM FILME
    @GetMapping("/filmes/{filmeId}")
    public ResponseEntity<?> listar(
            @RequestHeader(
                    value = "Authorization",
                    required = false) String autorizacao,
            @PathVariable int filmeId) {

        if (sessoes.obterUsuarioId(autorizacao) == null) {
            return sessaoInvalida();
        }

        if (filmeId <= 0) {
            return erro("Filme invalido.");
        }

        String sql = """
                SELECT id, filme_id, resposta_a, curtidas,
                       titulo_filme, autor, texto, autor_id
                FROM comentario
                WHERE filme_id = ?
                ORDER BY id ASC
                """;

        List<Map<String, Object>> comentarios = jdbc.query(
                sql,
                (rs, numeroLinha) -> {
                    Map<String, Object> item =
                            new LinkedHashMap<>();

                    item.put("id", rs.getInt("id"));
                    item.put("filmeId",
                            rs.getInt("filme_id"));
                    item.put("respostaA",
                            rs.getInt("resposta_a"));
                    item.put("curtidas",
                            rs.getInt("curtidas"));
                    item.put("tituloFilme",
                            rs.getString("titulo_filme"));
                    item.put("autor",
                            rs.getString("autor"));
                    item.put("texto",
                            rs.getString("texto"));
                    item.put("autorId",
                            rs.getInt("autor_id"));

                    return item;
                },
                filmeId
        );

        return ResponseEntity.ok(comentarios);
    }

    // PUBLICAR COMENTARIO OU RESPOSTA
    @PostMapping
    @Transactional
    public ResponseEntity<?> inserir(
            @RequestHeader(
                    value = "Authorization",
                    required = false) String autorizacao,
            @RequestBody Map<String, Object> dados) {

        Integer usuarioId =
                sessoes.obterUsuarioId(autorizacao);

        if (usuarioId == null) {
            return sessaoInvalida();
        }

        if (dados == null) {
            return erro("Dados do comentario ausentes.");
        }

        Integer filmeId = inteiro(dados.get("filmeId"));

        Integer respostaA = dados.containsKey("respostaA")
                ? inteiro(dados.get("respostaA"))
                : 0;

        String texto = texto(dados.get("texto"));
        String tituloFilme =
                texto(dados.get("tituloFilme"));

        if (filmeId == null || filmeId <= 0) {
            return erro("Filme invalido.");
        }

        if (respostaA == null || respostaA < 0) {
            return erro("Resposta invalida.");
        }

        if (texto == null || texto.isBlank()
                || texto.length() > 2000) {
            return erro(
                    "O comentario deve ter entre 1 e 2000 caracteres.");
        }

        if (tituloFilme == null) {
            tituloFilme = "";
        }

        if (tituloFilme.length() > 255) {
            return erro("Titulo do filme muito longo.");
        }

        List<Integer> filmes = jdbc.queryForList(
                "SELECT id FROM filme WHERE id = ?",
                Integer.class,
                filmeId
        );

        if (filmes.isEmpty()) {
            return ResponseEntity.status(
                    HttpStatus.NOT_FOUND
            ).body(Map.of(
                    "erro", "Filme nao encontrado."
            ));
        }

        if (respostaA > 0) {
            List<Integer> comentarios = jdbc.queryForList(
                    """
                    SELECT id FROM comentario
                    WHERE id = ? AND filme_id = ?
                    """,
                    Integer.class,
                    respostaA,
                    filmeId
            );

            if (comentarios.isEmpty()) {
                return erro(
                        "Comentario original nao encontrado.");
            }
        }

        List<String> nomes = jdbc.queryForList(
                "SELECT nome FROM usuario WHERE id = ?",
                String.class,
                usuarioId
        );

        if (nomes.isEmpty()) {
            return sessaoInvalida();
        }

        String autor = nomes.get(0);

        if (autor == null || autor.isBlank()) {
            autor = "Usuario";
        }

        jdbc.update(
                """
                INSERT INTO comentario
                    (filme_id, resposta_a, curtidas,
                     titulo_filme, autor, texto, autor_id)
                VALUES (?, ?, 0, ?, ?, ?, ?)
                """,
                filmeId,
                respostaA,
                tituloFilme,
                autor,
                texto.trim(),
                usuarioId
        );

        return ResponseEntity.status(
                HttpStatus.CREATED
        ).body(Map.of(
                "mensagem", "Comentario publicado!"
        ));
    }

    // CURTIR COMENTARIO UMA UNICA VEZ POR CONTA
    @PostMapping("/{comentarioId}/curtir")
    @Transactional
    public ResponseEntity<?> curtir(
            @RequestHeader(
                    value = "Authorization",
                    required = false) String autorizacao,
            @PathVariable int comentarioId) {

        Integer usuarioId =
                sessoes.obterUsuarioId(autorizacao);

        if (usuarioId == null) {
            return sessaoInvalida();
        }

        if (comentarioId <= 0) {
            return erro("Comentario invalido.");
        }

        // Bloqueia o comentario durante a atualizacao.
        List<Integer> comentarios = jdbc.queryForList(
                """
                SELECT id FROM comentario
                WHERE id = ? FOR UPDATE
                """,
                Integer.class,
                comentarioId
        );

        if (comentarios.isEmpty()) {
            return ResponseEntity.status(
                    HttpStatus.NOT_FOUND
            ).body(Map.of(
                    "erro", "Comentario nao encontrado."
            ));
        }

        List<Integer> curtidasExistentes =
                jdbc.queryForList(
                        """
                        SELECT 1 FROM curtida_comentario
                        WHERE usuario_id = ?
                          AND comentario_id = ?
                        LIMIT 1
                        """,
                        Integer.class,
                        usuarioId,
                        comentarioId
                );

        if (curtidasExistentes.isEmpty()) {
            jdbc.update(
                    """
                    INSERT INTO curtida_comentario
                        (usuario_id, comentario_id)
                    VALUES (?, ?)
                    """,
                    usuarioId,
                    comentarioId
            );
        }

        Integer total = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM curtida_comentario
                WHERE comentario_id = ?
                """,
                Integer.class,
                comentarioId
        );

        int quantidade = total == null ? 0 : total;

        jdbc.update(
                """
                UPDATE comentario
                SET curtidas = ?
                WHERE id = ?
                """,
                quantidade,
                comentarioId
        );

        return ResponseEntity.ok(Map.of(
                "mensagem", "Curtida registrada!",
                "curtidas", quantidade
        ));
    }

    // APAGAR MEUS COMENTARIOS DE UM FILME
    @DeleteMapping("/filmes/{filmeId}/meus")
    @Transactional
    public ResponseEntity<?> limparMeus(
            @RequestHeader(
                    value = "Authorization",
                    required = false) String autorizacao,
            @PathVariable int filmeId) {

        Integer usuarioId =
                sessoes.obterUsuarioId(autorizacao);

        if (usuarioId == null) {
            return sessaoInvalida();
        }

        if (filmeId <= 0) {
            return erro("Filme invalido.");
        }

        List<Map<String, Object>> linhas =
                jdbc.queryForList(
                        """
                        SELECT id, resposta_a, autor_id
                        FROM comentario
                        WHERE filme_id = ?
                        """,
                        filmeId
                );

        Map<Integer, List<Integer>> filhos =
                new HashMap<>();

        Deque<Integer> fila = new ArrayDeque<>();

        for (Map<String, Object> linha : linhas) {
            int id = ((Number) linha.get("id"))
                    .intValue();

            Object valorPai = linha.get("resposta_a");

            int pai = valorPai instanceof Number
                    ? ((Number) valorPai).intValue()
                    : 0;

            Object valorAutor = linha.get("autor_id");

            if (valorAutor instanceof Number
                    && ((Number) valorAutor).intValue()
                    == usuarioId) {
                fila.add(id);
            }

            filhos.computeIfAbsent(
                    pai, chave -> new ArrayList<>()
            ).add(id);
        }

        // Inclui respostas aos comentarios apagados,
        // evitando deixar respostas sem comentario pai.
        Set<Integer> excluir = new LinkedHashSet<>();

        while (!fila.isEmpty()) {
            int id = fila.removeFirst();

            if (excluir.add(id)) {
                fila.addAll(
                        filhos.getOrDefault(id, List.of())
                );
            }
        }

        List<Integer> ordem = new ArrayList<>(excluir);

        // Apaga as respostas antes dos comentarios pais.
        Collections.reverse(ordem);

        for (int id : ordem) {
            jdbc.update(
                    """
                    DELETE FROM curtida_comentario
                    WHERE comentario_id = ?
                    """,
                    id
            );
        }

        for (int id : ordem) {
            jdbc.update(
                    """
                    DELETE FROM comentario
                    WHERE id = ? AND filme_id = ?
                    """,
                    id,
                    filmeId
            );
        }

        return ResponseEntity.ok(Map.of(
                "mensagem", "Comentarios removidos.",
                "removidos", ordem.size()
        ));
    }

    private Integer inteiro(Object valor) {
        if (!(valor instanceof Number numero)) {
            return null;
        }

        double n = numero.doubleValue();

        if (!Double.isFinite(n)
                || n != Math.rint(n)
                || n < 0
                || n > Integer.MAX_VALUE) {
            return null;
        }

        return numero.intValue();
    }

    private String texto(Object valor) {
        return valor instanceof String
                ? (String) valor
                : null;
    }

    private ResponseEntity<?> erro(String mensagem) {
        return ResponseEntity.badRequest().body(
                Map.of("erro", mensagem)
        );
    }

    private ResponseEntity<?> sessaoInvalida() {
        return ResponseEntity.status(
                HttpStatus.UNAUTHORIZED
        ).body(Map.of(
                "erro",
                "Sessao invalida ou expirada. Entre novamente."
        ));
    }
}
