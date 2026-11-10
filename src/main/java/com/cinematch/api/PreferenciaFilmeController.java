
package com.cinematch.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/preferencias")
public class PreferenciaFilmeController {

    private final JdbcTemplate jdbcTemplate;
    private final SessaoTokenService sessoes;

    public PreferenciaFilmeController(
            JdbcTemplate jdbcTemplate,
            SessaoTokenService sessoes
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessoes = sessoes;
    }

    // REGISTRAR CURTIDA
    @PostMapping("/curtir")
    @Transactional
    public ResponseEntity<?> curtir(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String autorizacao,
            @RequestBody Map<String, Object> dados
    ) {

        Integer usuarioId =
                sessoes.obterUsuarioId(autorizacao);

        if (usuarioId == null) {
            return sessaoInvalida();
        }

        Integer filmeId = obterFilmeId(dados);

        if (filmeId == null) {
            return filmeInvalido();
        }

        // Bloqueia a linha do filme durante a atualização.
        // Isso ajuda a manter a contagem consistente.
        List<Integer> filmes = jdbcTemplate.queryForList(
                "SELECT id FROM filme WHERE id = ? FOR UPDATE",
                Integer.class,
                filmeId
        );

        if (filmes.isEmpty()) {
            return ResponseEntity.status(
                    HttpStatus.NOT_FOUND
            ).body(
                    Map.of(
                            "erro",
                            "Filme não encontrado."
                    )
            );
        }

        String sql = """
                INSERT INTO preferencia_filme
                    (usuario_id, filme_id, curtiu, salvo)
                VALUES (?, ?, TRUE, FALSE)
                ON DUPLICATE KEY UPDATE
                    curtiu = TRUE
                """;

        jdbcTemplate.update(
                sql,
                usuarioId,
                filmeId
        );

        int total = contarNoBanco(filmeId);

        jdbcTemplate.update(
                "UPDATE filme SET curtidas = ? WHERE id = ?",
                total,
                filmeId
        );

        return ResponseEntity.ok(
                Map.of(
                        "mensagem",
                        "Curtida registrada com sucesso!",
                        "curtidas",
                        total
                )
        );
    }

    // SALVAR COMO FAVORITO
    @PostMapping("/salvar")
    @Transactional
    public ResponseEntity<?> salvar(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String autorizacao,
            @RequestBody Map<String, Object> dados
    ) {

        Integer usuarioId =
                sessoes.obterUsuarioId(autorizacao);

        if (usuarioId == null) {
            return sessaoInvalida();
        }

        Integer filmeId = obterFilmeId(dados);

        if (filmeId == null) {
            return filmeInvalido();
        }

        Integer quantidade = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM filme WHERE id = ?",
                Integer.class,
                filmeId
        );

        if (quantidade == null || quantidade == 0) {
            return ResponseEntity.status(
                    HttpStatus.NOT_FOUND
            ).body(
                    Map.of(
                            "erro",
                            "Filme não encontrado."
                    )
            );
        }

        String sql = """
                INSERT INTO preferencia_filme
                    (usuario_id, filme_id, curtiu, salvo)
                VALUES (?, ?, FALSE, TRUE)
                ON DUPLICATE KEY UPDATE
                    salvo = TRUE
                """;

        jdbcTemplate.update(
                sql,
                usuarioId,
                filmeId
        );

        return ResponseEntity.ok(
                Map.of(
                        "mensagem",
                        "Filme salvo nos favoritos!"
                )
        );
    }

    // IDs DOS FILMES CURTIDOS PELO USUÁRIO LOGADO
    @GetMapping("/curtidos")
    public ResponseEntity<?> idsCurtidos(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String autorizacao
    ) {

        Integer usuarioId =
                sessoes.obterUsuarioId(autorizacao);

        if (usuarioId == null) {
            return sessaoInvalida();
        }

        List<Integer> ids = jdbcTemplate.queryForList(
                """
                SELECT filme_id
                FROM preferencia_filme
                WHERE usuario_id = ?
                  AND curtiu = TRUE
                ORDER BY filme_id
                """,
                Integer.class,
                usuarioId
        );

        return ResponseEntity.ok(ids);
    }

    // IDs DOS FILMES SALVOS PELO USUÁRIO LOGADO
    @GetMapping("/salvos")
    public ResponseEntity<?> idsSalvos(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String autorizacao
    ) {

        Integer usuarioId =
                sessoes.obterUsuarioId(autorizacao);

        if (usuarioId == null) {
            return sessaoInvalida();
        }

        List<Integer> ids = jdbcTemplate.queryForList(
                """
                SELECT filme_id
                FROM preferencia_filme
                WHERE usuario_id = ?
                  AND salvo = TRUE
                ORDER BY filme_id
                """,
                Integer.class,
                usuarioId
        );

        return ResponseEntity.ok(ids);
    }

    // CONTAGEM PÚBLICA DE CURTIDAS DE UM FILME
    @GetMapping("/filmes/{filmeId}/curtidas")
    public ResponseEntity<?> contarCurtidas(
            @PathVariable int filmeId
    ) {

        if (filmeId <= 0) {
            return filmeInvalido();
        }

        int total = contarNoBanco(filmeId);

        return ResponseEntity.ok(
                Map.of("curtidas", total)
        );
    }

    private int contarNoBanco(int filmeId) {

        Integer total = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM preferencia_filme
                WHERE filme_id = ?
                  AND curtiu = TRUE
                """,
                Integer.class,
                filmeId
        );

        return total == null ? 0 : total;
    }

    private Integer obterFilmeId(
            Map<String, Object> dados
    ) {

        if (dados == null) {
            return null;
        }

        Object valor = dados.get("filmeId");

        if (!(valor instanceof Number numero)) {
            return null;
        }

        double valorNumerico = numero.doubleValue();

        if (!Double.isFinite(valorNumerico)
                || valorNumerico != Math.rint(valorNumerico)
                || valorNumerico <= 0
                || valorNumerico > Integer.MAX_VALUE) {
            return null;
        }

        return numero.intValue();
    }

    private ResponseEntity<Map<String, String>>
            sessaoInvalida() {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(
                        Map.of(
                                "erro",
                                "Sessão inválida ou expirada. "
                                        + "Entre novamente."
                        )
                );
    }

    private ResponseEntity<Map<String, String>>
            filmeInvalido() {

        return ResponseEntity
                .badRequest()
                .body(
                        Map.of(
                                "erro",
                                "Informe um filmeId válido."
                        )
                );
    }
}
