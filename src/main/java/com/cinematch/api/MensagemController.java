
package com.cinematch.api;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
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
@RequestMapping("/api/mensagens")
public class MensagemController {

    private final JdbcTemplate jdbcTemplate;
    private final SessaoTokenService sessoes;

    public MensagemController(
            JdbcTemplate jdbcTemplate,
            SessaoTokenService sessoes
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessoes = sessoes;
    }

    // ENVIAR MENSAGEM
    @PostMapping("/enviar")
    @Transactional
    public ResponseEntity<?> enviar(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String autorizacao,
            @RequestBody(required = false)
            Map<String, Object> dados
    ) {

        Integer remetenteId =
                sessoes.obterUsuarioId(autorizacao);

        if (remetenteId == null) {
            return sessaoInvalida();
        }

        Integer destinatarioId =
                obterDestinatarioId(dados);

        if (destinatarioId == null
                || destinatarioId.equals(remetenteId)) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "erro",
                            "Destinatario invalido."
                    )
            );
        }

        Object valorTexto =
                dados == null ? null : dados.get("texto");

        if (!(valorTexto instanceof String)) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "erro",
                            "Informe o texto da mensagem."
                    )
            );
        }

        String texto = ((String) valorTexto).trim();

        if (texto.isEmpty() || texto.length() > 2000) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "erro",
                            "A mensagem deve ter entre "
                                    + "1 e 2000 caracteres."
                    )
            );
        }

        // Somente contas com conexao aceita
        // podem trocar mensagens.
        if (!estaoConectados(
                remetenteId,
                destinatarioId
        )) {

            return ResponseEntity.status(
                    HttpStatus.FORBIDDEN
            ).body(
                    Map.of(
                            "erro",
                            "E necessario ter uma "
                                    + "conexao aceita para conversar."
                    )
            );
        }

        int alteradas = jdbcTemplate.update(
                """
                INSERT INTO mensagem
                    (remetente_id, destinatario_id, texto)
                VALUES (?, ?, ?)
                """,
                remetenteId,
                destinatarioId,
                texto
        );

        if (alteradas != 1) {

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(
                    Map.of(
                            "erro",
                            "Nao foi possivel salvar a mensagem."
                    )
            );
        }

        return ResponseEntity.ok(
                Map.of(
                        "mensagem",
                        "Mensagem enviada com sucesso!"
                )
        );
    }

    // LISTAR MENSAGENS DE UMA CONVERSA
    @GetMapping("/conversa/{outroId}")
    public ResponseEntity<?> listar(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String autorizacao,
            @PathVariable int outroId
    ) {

        Integer usuarioId =
                sessoes.obterUsuarioId(autorizacao);

        if (usuarioId == null) {
            return sessaoInvalida();
        }

        if (outroId <= 0 || outroId == usuarioId) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "erro",
                            "Usuario da conversa invalido."
                    )
            );
        }

        if (!estaoConectados(usuarioId, outroId)) {

            return ResponseEntity.status(
                    HttpStatus.FORBIDDEN
            ).body(
                    Map.of(
                            "erro",
                            "Voce nao possui uma conexao "
                                    + "aceita com esta pessoa."
                    )
            );
        }

        List<Map<String, Object>> mensagens =
                jdbcTemplate.query(
                        """
                        SELECT
                            id,
                            remetente_id,
                            texto,
                            enviada_em
                        FROM mensagem
                        WHERE
                            (remetente_id = ?
                             AND destinatario_id = ?)
                            OR
                            (remetente_id = ?
                             AND destinatario_id = ?)
                        ORDER BY id ASC
                        """,
                        (rs, linha) -> {

                            Map<String, Object> mensagem =
                                    new LinkedHashMap<>();

                            mensagem.put(
                                    "id",
                                    rs.getInt("id")
                            );

                            mensagem.put(
                                    "remetenteId",
                                    rs.getInt("remetente_id")
                            );

                            mensagem.put(
                                    "texto",
                                    rs.getString("texto")
                            );

                            Timestamp enviadaEm =
                                    rs.getTimestamp("enviada_em");

                            mensagem.put(
                                    "enviadaEm",
                                    enviadaEm == null
                                            ? ""
                                            : enviadaEm.toString()
                            );

                            return mensagem;
                        },
                        usuarioId,
                        outroId,
                        outroId,
                        usuarioId
                );

        return ResponseEntity.ok(mensagens);
    }

    // VERIFICAR SE A CONEXAO FOI ACEITA
    private boolean estaoConectados(
            int usuarioA,
            int usuarioB
    ) {

        List<Integer> resultados =
                jdbcTemplate.queryForList(
                        """
                        SELECT 1
                        FROM conexao
                        WHERE status = 'ACEITA'
                          AND (
                              (solicitante_id = ?
                               AND destinatario_id = ?)
                              OR
                              (solicitante_id = ?
                               AND destinatario_id = ?)
                          )
                        LIMIT 1
                        """,
                        Integer.class,
                        usuarioA,
                        usuarioB,
                        usuarioB,
                        usuarioA
                );

        return !resultados.isEmpty();
    }

    // VALIDAR O ID RECEBIDO NO JSON
    private Integer obterDestinatarioId(
            Map<String, Object> dados
    ) {

        if (dados == null) {
            return null;
        }

        Object valor = dados.get("destinatarioId");

        if (!(valor instanceof Number numero)) {
            return null;
        }

        double id = numero.doubleValue();

        if (!Double.isFinite(id)
                || id != Math.rint(id)
                || id <= 0
                || id > Integer.MAX_VALUE) {

            return null;
        }

        return numero.intValue();
    }

    // RESPOSTA PARA LOGIN INVALIDO
    private ResponseEntity<Map<String, String>>
            sessaoInvalida() {

        return ResponseEntity.status(
                HttpStatus.UNAUTHORIZED
        ).body(
                Map.of(
                        "erro",
                        "Sessao invalida ou expirada. "
                                + "Entre novamente."
                )
        );
    }
}
