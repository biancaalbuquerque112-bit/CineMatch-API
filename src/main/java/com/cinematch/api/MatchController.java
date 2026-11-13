
package com.cinematch.api;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

/**
 * Controla os matches e as conexoes entre usuarios.
 *
 * As consultas sao feitas no MySQL do Railway.
 * As curtidas de outras pessoas nao sao expostas
 * diretamente ao aplicativo Swing.
 */
@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final JdbcTemplate jdbc;
    private final SessaoTokenService sessoes;

    public MatchController(
            JdbcTemplate jdbc,
            SessaoTokenService sessoes
    ) {
        this.jdbc = jdbc;
        this.sessoes = sessoes;
    }

    // =========================================
    // BUSCAR PESSOAS QUE CURTIRAM O MESMO FILME
    // =========================================

    @GetMapping("/filmes/{filmeId}")
    public ResponseEntity<?> sugerir(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String autorizacao,
            @PathVariable int filmeId
    ) {

        Integer usuarioId =
                sessoes.obterUsuarioId(autorizacao);

        if (usuarioId == null) {
            return semSessao();
        }

        if (filmeId <= 0) {
            return erro(
                    HttpStatus.BAD_REQUEST,
                    "Filme invalido."
            );
        }

        Perfil atual = buscarPerfil(usuarioId);

        if (atual == null
                || !adulto(atual)
                || !atual.permitirSugestoes()) {

            return ResponseEntity.ok(List.of());
        }

        Set<Integer> meusFilmes =
                filmesCurtidos(usuarioId);

        if (!meusFilmes.contains(filmeId)) {
            return ResponseEntity.ok(List.of());
        }

        // Busca pessoas que curtiram o mesmo filme.
        List<Map<String, Object>> candidatos =
                jdbc.queryForList(
                        """
                        SELECT DISTINCT u.*
                        FROM usuario u
                        JOIN preferencia_filme p
                          ON p.usuario_id = u.id
                        WHERE p.filme_id = ?
                          AND p.curtiu = TRUE
                          AND u.id <> ?
                        """,
                        filmeId,
                        usuarioId
                );

        List<Map<String, Object>> resultados =
                new ArrayList<>();

        for (Map<String, Object> linha : candidatos) {

            Perfil pessoa = converterPerfil(linha);

            if (pessoa == null) {
                continue;
            }

            Set<Integer> filmesPessoa =
                    filmesCurtidos(pessoa.id());

            Pontuacao pontuacao = calcular(
                    atual,
                    pessoa,
                    meusFilmes,
                    filmesPessoa
            );

            if (pontuacao == null) {
                continue;
            }

            Map<String, Object> resultado =
                    new LinkedHashMap<>();

            resultado.put(
                    "usuario",
                    perfilPublico(pessoa)
            );

            resultado.put(
                    "percentual",
                    pontuacao.percentual()
            );

            resultado.put(
                    "filmesEmComum",
                    pontuacao.filmesEmComum()
            );

            resultado.put(
                    "generosEmComum",
                    pontuacao.generosEmComum()
            );

            resultado.put(
                    "motivo",
                    pontuacao.motivo()
            );

            resultados.add(resultado);
        }

        // Pessoas mais compativeis aparecem primeiro.
        resultados.sort(
                Comparator.comparingInt(
                        r -> -((Number)
                                r.get("percentual")
                        ).intValue()
                )
        );

        return ResponseEntity.ok(resultados);
    }

    // =========================================
    // ENVIAR SOLICITACAO DE CONEXAO
    // =========================================

    @PostMapping("/solicitar")
    @Transactional
    public ResponseEntity<?> solicitar(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String autorizacao,
            @RequestBody Map<String, Object> dados
    ) {

        Integer origem =
                sessoes.obterUsuarioId(autorizacao);

        if (origem == null) {
            return semSessao();
        }

        Integer destino = inteiroPositivo(
                dados == null
                        ? null
                        : dados.get("destinoId")
        );

        if (destino == null
                || origem.equals(destino)) {

            return erro(
                    HttpStatus.BAD_REQUEST,
                    "Destino invalido."
            );
        }

        Perfil a = buscarPerfil(origem);
        Perfil b = buscarPerfil(destino);

        if (a == null
                || b == null
                || !adulto(a)
                || !adulto(b)
                || !a.permitirSugestoes()
                || !b.permitirSugestoes()) {

            return erro(
                    HttpStatus.FORBIDDEN,
                    "Conexao nao permitida para esses perfis."
            );
        }

        if (!temFilmeComum(origem, destino)) {

            return erro(
                    HttpStatus.BAD_REQUEST,
                    "Vocês ainda nao curtiram um filme em comum."
            );
        }

        Integer existentes = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM conexao
                WHERE
                    (solicitante_id = ?
                     AND destinatario_id = ?)
                OR
                    (solicitante_id = ?
                     AND destinatario_id = ?)
                """,
                Integer.class,
                origem,
                destino,
                destino,
                origem
        );

        if (existentes != null && existentes > 0) {

            return erro(
                    HttpStatus.CONFLICT,
                    "Ja existe uma conexao entre essas contas."
            );
        }

        jdbc.update(
                """
                INSERT INTO conexao
                    (solicitante_id, destinatario_id, status)
                VALUES (?, ?, 'PENDENTE')
                """,
                origem,
                destino
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        Map.of(
                                "mensagem",
                                "Solicitacao enviada!"
                        )
                );
    }

    // =========================================
    // CONSULTAR STATUS ENTRE DUAS CONTAS
    // =========================================

    @GetMapping("/status/{outroId}")
    public ResponseEntity<?> statusEntre(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String autorizacao,
            @PathVariable int outroId
    ) {

        Integer usuarioId =
                sessoes.obterUsuarioId(autorizacao);

        if (usuarioId == null) {
            return semSessao();
        }

        if (outroId <= 0) {

            return erro(
                    HttpStatus.BAD_REQUEST,
                    "Usuario invalido."
            );
        }

        List<Map<String, Object>> linhas =
                jdbc.queryForList(
                        """
                        SELECT status, solicitante_id
                        FROM conexao
                        WHERE
                            (solicitante_id = ?
                             AND destinatario_id = ?)
                        OR
                            (solicitante_id = ?
                             AND destinatario_id = ?)
                        ORDER BY id DESC
                        LIMIT 1
                        """,
                        usuarioId,
                        outroId,
                        outroId,
                        usuarioId
                );

        if (linhas.isEmpty()) {

            return ResponseEntity.ok(
                    Map.of("status", "")
            );
        }

        Map<String, Object> linha = linhas.get(0);

        int solicitante = ((Number)
                linha.get("solicitante_id")
        ).intValue();

        return ResponseEntity.ok(
                Map.of(
                        "status",
                        String.valueOf(
                                linha.get("status")
                        ),
                        "direcao",
                        solicitante == usuarioId
                                ? "enviada"
                                : "recebida"
                )
        );
    }

    // =========================================
    // LISTAR CONEXOES DA CONTA LOGADA
    // =========================================

    @GetMapping("/conexoes")
    public ResponseEntity<?> listar(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String autorizacao
    ) {

        Integer usuarioId =
                sessoes.obterUsuarioId(autorizacao);

        if (usuarioId == null) {
            return semSessao();
        }

        List<Map<String, Object>> conexoes =
                jdbc.query(
                        """
                        SELECT
                            c.id,
                            c.solicitante_id,
                            c.destinatario_id,
                            c.status,
                            u1.nome AS solicitante,
                            u2.nome AS destinatario
                        FROM conexao c
                        JOIN usuario u1
                          ON u1.id = c.solicitante_id
                        JOIN usuario u2
                          ON u2.id = c.destinatario_id
                        WHERE c.solicitante_id = ?
                           OR c.destinatario_id = ?
                        ORDER BY c.id DESC
                        """,
                        (rs, numero) -> {

                            boolean recebida =
                                    rs.getInt(
                                            "destinatario_id"
                                    ) == usuarioId;

                            return Map.<String, Object>of(
                                    "id",
                                    rs.getInt("id"),
                                    "outroId",
                                    rs.getInt(
                                            recebida
                                                    ? "solicitante_id"
                                                    : "destinatario_id"
                                    ),
                                    "nome",
                                    rs.getString(
                                            recebida
                                                    ? "solicitante"
                                                    : "destinatario"
                                    ),
                                    "status",
                                    rs.getString("status"),
                                    "recebida",
                                    recebida
                            );
                        },
                        usuarioId,
                        usuarioId
                );

        return ResponseEntity.ok(conexoes);
    }

    // =========================================
    // ACEITAR OU RECUSAR CONEXAO
    // =========================================

    @PostMapping("/conexoes/{conexaoId}/responder")
    @Transactional
    public ResponseEntity<?> responder(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String autorizacao,
            @PathVariable int conexaoId,
            @RequestBody Map<String, Object> dados
    ) {

        Integer usuarioId =
                sessoes.obterUsuarioId(autorizacao);

        if (usuarioId == null) {
            return semSessao();
        }

        if (conexaoId <= 0
                || dados == null
                || !(dados.get("aceitar")
                        instanceof Boolean aceitar)) {

            return erro(
                    HttpStatus.BAD_REQUEST,
                    "Informe aceitar: true ou false."
            );
        }

        int alteradas = jdbc.update(
                """
                UPDATE conexao
                SET status = ?
                WHERE id = ?
                  AND destinatario_id = ?
                  AND status = 'PENDENTE'
                """,
                aceitar
                        ? "ACEITA"
                        : "RECUSADA",
                conexaoId,
                usuarioId
        );

        if (alteradas == 0) {

            return erro(
                    HttpStatus.NOT_FOUND,
                    "Solicitacao pendente nao encontrada."
            );
        }

        return ResponseEntity.ok(
                Map.of(
                        "mensagem",
                        "Resposta registrada!"
                )
        );
    }

    // =========================================
    // CONSULTAR PERFIL NO BANCO
    // =========================================

    private Perfil buscarPerfil(int id) {

        List<Map<String, Object>> linhas =
                jdbc.queryForList(
                        "SELECT * FROM usuario WHERE id = ?",
                        id
                );

        return linhas.isEmpty()
                ? null
                : converterPerfil(linhas.get(0));
    }

    private Perfil converterPerfil(
            Map<String, Object> linha
    ) {

        Integer id = inteiroPositivo(
                valor(linha, "id")
        );

        if (id == null) {
            return null;
        }

        return new Perfil(
                id,
                texto(linha, "nome"),
                nascimento(
                        valor(
                                linha,
                                "nascimento",
                                "data_nascimento"
                        )
                ),
                texto(linha, "bio"),
                texto(linha, "foto"),
                booleano(
                        valor(
                                linha,
                                "permitir_sugestoes",
                                "permitirSugestoes"
                        )
                ),
                texto(linha, "intencao"),
                texto(linha, "interesse"),
                texto(
                        linha,
                        "identidade_genero",
                        "identidadeGenero"
                ),
                texto(
                        linha,
                        "generos_favoritos",
                        "generosFavoritos"
                ),
                booleano(
                        valor(
                                linha,
                                "mostrar_informacoes",
                                "mostrarInformacoes"
                        )
                )
        );
    }

    // =========================================
    // DADOS PUBLICOS DO PERFIL
    // =========================================

    private Map<String, Object> perfilPublico(
            Perfil p
    ) {

        Map<String, Object> dados =
                new LinkedHashMap<>();

        dados.put("id", p.id());
        dados.put("nome", p.nome());

        dados.put(
                "idade",
                Period.between(
                        p.nascimento(),
                        LocalDate.now()
                ).getYears()
        );

        // Nao expomos a data exata de nascimento.
        if (p.mostrarInformacoes()) {

            dados.put("bio", p.bio());
            dados.put("foto", p.foto());

            dados.put(
                    "generosFavoritos",
                    p.generosFavoritos()
            );
        }

        return dados;
    }

    // =========================================
    // FILMES CURTIDOS DE UMA CONTA
    // =========================================

    private Set<Integer> filmesCurtidos(
            int usuarioId
    ) {

        return new HashSet<>(
                jdbc.queryForList(
                        """
                        SELECT filme_id
                        FROM preferencia_filme
                        WHERE usuario_id = ?
                          AND curtiu = TRUE
                        """,
                        Integer.class,
                        usuarioId
                )
        );
    }

    // =========================================
    // VERIFICAR FILME CURTIDO EM COMUM
    // =========================================

    private boolean temFilmeComum(
            int a,
            int b
    ) {

        Integer total = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM preferencia_filme x
                JOIN preferencia_filme y
                  ON y.filme_id = x.filme_id
                WHERE x.usuario_id = ?
                  AND y.usuario_id = ?
                  AND x.curtiu = TRUE
                  AND y.curtiu = TRUE
                """,
                Integer.class,
                a,
                b
        );

        return total != null && total > 0;
    }

    // =========================================
    // CALCULO ORIGINAL DE COMPATIBILIDADE
    // =========================================

    private Pontuacao calcular(
            Perfil a,
            Perfil b,
            Set<Integer> filmesA,
            Set<Integer> filmesB
    ) {

        if (a.id() == b.id()
                || !adulto(a)
                || !adulto(b)
                || !a.permitirSugestoes()
                || !b.permitirSugestoes()) {

            return null;
        }

        boolean amizade =
                incluiAmizade(a.intencao())
                        && incluiAmizade(b.intencao());

        boolean namoro =
                incluiRelacionamento(a.intencao())
                        && incluiRelacionamento(b.intencao())
                        && interesseCompativel(a, b)
                        && interesseCompativel(b, a);

        if (!amizade && !namoro) {
            return null;
        }

        Set<Integer> comuns =
                new HashSet<>(filmesA);

        comuns.retainAll(filmesB);

        if (comuns.isEmpty()) {
            return null;
        }

        Set<Integer> uniao =
                new HashSet<>(filmesA);

        uniao.addAll(filmesB);

        Set<String> genA =
                generos(a.generosFavoritos());

        Set<String> genB =
                generos(b.generosFavoritos());

        Set<String> genComuns =
                new HashSet<>(genA);

        genComuns.retainAll(genB);

        Set<String> genUniao =
                new HashSet<>(genA);

        genUniao.addAll(genB);

        double filmes =
                100.0 * comuns.size()
                        / Math.max(1, uniao.size());

        double generos =
                genUniao.isEmpty()
                        ? 0
                        : 100.0 * genComuns.size()
                        / genUniao.size();

        int percentual = (int) Math.round(
                0.70 * filmes
                        + 0.30 * generos
        );

        String motivo =
                amizade && namoro
                        ? "Amizade ou relacionamento"
                        : amizade
                                ? "Amizade"
                                : "Relacionamento";

        return new Pontuacao(
                percentual,
                comuns.size(),
                genComuns.size(),
                motivo
        );
    }

    // =========================================
    // VERIFICAR MAIORIDADE
    // =========================================

    private boolean adulto(Perfil p) {

        return p.nascimento() != null
                && !p.nascimento().isAfter(
                        LocalDate.now()
                )
                && Period.between(
                        p.nascimento(),
                        LocalDate.now()
                ).getYears() >= 18;
    }

    // =========================================
    // INTENCOES COMPATIVEIS
    // =========================================

    private boolean incluiAmizade(String v) {

        return v == null
                || v.contains("Amizade");
    }

    private boolean incluiRelacionamento(String v) {

        return v != null
                && (
                        v.contains("relacionamento")
                        || "Relacionamento".equals(v)
                );
    }

    // =========================================
    // GENEROS FAVORITOS
    // =========================================

    private Set<String> generos(String valor) {

        Set<String> saida = new HashSet<>();

        if (valor != null) {

            for (String genero : valor.split("\\|")) {

                if (!genero.isBlank()) {

                    saida.add(
                            genero.trim()
                                    .toLowerCase(Locale.ROOT)
                    );
                }
            }
        }

        return saida;
    }

    // =========================================
    // INTERESSE RECIPROCO
    // =========================================

    private boolean interesseCompativel(
            Perfil interessado,
            Perfil candidato
    ) {

        String preferencia =
                interessado.interesse();

        if (preferencia == null
                || preferencia.equals("Sem preferência")
                || preferencia.equals("Todos os gêneros")
                || preferencia.equals("Prefiro não informar")) {

            return true;
        }

        String identidade =
                candidato.identidadeGenero();

        if (identidade == null
                || identidade.equals("Prefiro não informar")
                || identidade.equals("Outra identidade")) {

            return false;
        }

        if (preferencia.equals("Homens")) {
            return identidade.equals("Homem");
        }

        if (preferencia.equals("Mulheres")) {
            return identidade.equals("Mulher");
        }

        if (preferencia.equals("Homens e mulheres")) {

            return identidade.equals("Homem")
                    || identidade.equals("Mulher");
        }

        return true;
    }

    // =========================================
    // LEITURA DOS CAMPOS DO BANCO
    // =========================================

    private Object valor(
            Map<String, Object> linha,
            String... nomes
    ) {

        for (String nome : nomes) {

            for (Map.Entry<String, Object> entrada
                    : linha.entrySet()) {

                String coluna =
                        entrada.getKey()
                                .replace("_", "");

                String procurado =
                        nome.replace("_", "");

                if (coluna.equalsIgnoreCase(procurado)) {

                    return entrada.getValue();
                }
            }
        }

        return null;
    }

    private String texto(
            Map<String, Object> linha,
            String... nomes
    ) {

        Object v = valor(linha, nomes);

        return v == null
                ? null
                : String.valueOf(v);
    }

    private LocalDate nascimento(Object valor) {

        if (valor instanceof LocalDate data) {
            return data;
        }

        if (valor instanceof Date data) {
            return data.toLocalDate();
        }

        if (valor instanceof Timestamp data) {

            return data.toLocalDateTime()
                    .toLocalDate();
        }

        if (valor instanceof LocalDateTime data) {
            return data.toLocalDate();
        }

        if (valor == null) {
            return null;
        }

        try {

            String s = String.valueOf(valor);

            return LocalDate.parse(
                    s.length() >= 10
                            ? s.substring(0, 10)
                            : s
            );

        } catch (RuntimeException e) {

            return null;
        }
    }

    private boolean booleano(Object valor) {

        if (valor instanceof Boolean b) {
            return b;
        }

        if (valor instanceof Number n) {
            return n.intValue() != 0;
        }

        return valor != null
                && (
                        "true".equalsIgnoreCase(
                                String.valueOf(valor)
                        )
                        || "1".equals(
                                String.valueOf(valor)
                        )
                );
    }

    private Integer inteiroPositivo(Object valor) {

        if (!(valor instanceof Number n)) {
            return null;
        }

        double d = n.doubleValue();

        if (!Double.isFinite(d)
                || d < 1
                || d > Integer.MAX_VALUE
                || d != Math.rint(d)) {

            return null;
        }

        return n.intValue();
    }

    // =========================================
    // RESPOSTAS DE ERRO
    // =========================================

    private ResponseEntity<Map<String, String>>
            semSessao() {

        return erro(
                HttpStatus.UNAUTHORIZED,
                "Sessao invalida. Entre novamente."
        );
    }

    private ResponseEntity<Map<String, String>>
            erro(
                    HttpStatus status,
                    String mensagem
            ) {

        return ResponseEntity
                .status(status)
                .body(
                        Map.of(
                                "erro",
                                mensagem
                        )
                );
    }

    // =========================================
    // DADOS INTERNOS DO ALGORITMO
    // =========================================

    private record Perfil(
            int id,
            String nome,
            LocalDate nascimento,
            String bio,
            String foto,
            boolean permitirSugestoes,
            String intencao,
            String interesse,
            String identidadeGenero,
            String generosFavoritos,
            boolean mostrarInformacoes
    ) {
    }

    private record Pontuacao(
            int percentual,
            int filmesEmComum,
            int generosEmComum,
            String motivo
    ) {
    }
}
