
package com.cinematch.api;

import java.sql.Date;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final JdbcTemplate jdbc;
    private final SessaoTokenService sessoes;

    public UsuarioController(
            JdbcTemplate jdbc,
            SessaoTokenService sessoes) {
        this.jdbc = jdbc;
        this.sessoes = sessoes;
    }

    // Lista apenas ID e nome dos usuários.
    // Exige uma sessão válida.
    @GetMapping
    public ResponseEntity<?> listarUsuarios(
            @RequestHeader(
                    value = "Authorization",
                    required = false) String autorizacao) {

        Integer usuarioId = sessoes.obterUsuarioId(autorizacao);

        if (usuarioId == null || usuarioId <= 0) {
            return ResponseEntity.status(401)
                    .body(Map.of("erro", "Sessão inválida."));
        }

        List<Map<String, Object>> usuarios =
                jdbc.queryForList(
                        "SELECT id, nome FROM usuario ORDER BY nome");

        return ResponseEntity.ok(usuarios);
    }

    // Consulta o perfil da própria conta autenticada.
    @GetMapping("/{id}")
    public ResponseEntity<?> buscarUsuario(
            @RequestHeader(
                    value = "Authorization",
                    required = false) String autorizacao,
            @PathVariable int id) {

        Integer usuarioId = sessoes.obterUsuarioId(autorizacao);

        if (usuarioId == null || usuarioId <= 0) {
            return ResponseEntity.status(401)
                    .body(Map.of("erro", "Sessão inválida."));
        }

        if (usuarioId != id) {
            return ResponseEntity.status(403)
                    .body(Map.of(
                            "erro",
                            "Você não pode acessar este perfil."));
        }

        Map<String, Object> perfil = consultarPerfil(id);

        if (perfil == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(perfil);
    }

    // Atualiza somente o perfil da conta autenticada.
    @PutMapping("/{id}")
    public ResponseEntity<?> atualizarPerfil(
            @RequestHeader(
                    value = "Authorization",
                    required = false) String autorizacao,
            @PathVariable int id,
            @RequestBody Map<String, Object> dados) {

        Integer usuarioId = sessoes.obterUsuarioId(autorizacao);

        if (usuarioId == null || usuarioId <= 0) {
            return ResponseEntity.status(401)
                    .body(Map.of("erro", "Sessão inválida."));
        }

        if (usuarioId != id) {
            return ResponseEntity.status(403)
                    .body(Map.of(
                            "erro",
                            "Você não pode alterar este perfil."));
        }

        if (dados == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Dados não informados."));
        }

        String nome = texto(dados, "nome").trim();

        if (nome.isBlank() || nome.length() > 255) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Nome inválido."));
        }

        LocalDate nascimento;

        try {
            nascimento = LocalDate.parse(
                    texto(dados, "nascimento"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "erro",
                            "Data de nascimento inválida."));
        }

        int idade = Period.between(
                nascimento, LocalDate.now()).getYears();

        if (idade < 18 || idade > 120) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "erro",
                            "O CineMatch é destinado a maiores de 18 anos."));
        }

        String bio = texto(dados, "bio");
        String intencao = texto(dados, "intencao");
        String identidadeGenero =
                texto(dados, "identidadeGenero");
        String orientacao = texto(dados, "orientacao");
        String interesse = texto(dados, "interesse");
        String generosFavoritos =
                texto(dados, "generosFavoritos");
        String foto = texto(dados, "foto");

        boolean mostrarInformacoes =
                booleano(dados.get("mostrarInformacoes"), false);

        boolean permitirSugestoes =
                booleano(dados.get("permitirSugestoes"), true);

        String sql =
                "UPDATE usuario SET " +
                "nome = ?, idade = ?, nascimento = ?, " +
                "bio = ?, intencao = ?, " +
                "identidade_genero = ?, orientacao = ?, " +
                "interesse = ?, mostrar_informacoes = ?, " +
                "permitir_sugestoes = ?, " +
                "generos_favoritos = ?, foto = ? " +
                "WHERE id = ?";

        int alterados = jdbc.update(
                sql,
                nome,
                idade,
                Date.valueOf(nascimento),
                bio,
                intencao,
                identidadeGenero,
                orientacao,
                interesse,
                mostrarInformacoes,
                permitirSugestoes,
                generosFavoritos,
                foto,
                id
        );

        if (alterados == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(
                Map.of("mensagem", "Perfil atualizado com sucesso!"));
    }

    private Map<String, Object> consultarPerfil(int id) {

        List<Map<String, Object>> resultados =
                jdbc.queryForList(
                        "SELECT id, nome, email, bio, intencao, " +
                        "nascimento, identidade_genero, " +
                        "orientacao, interesse, " +
                        "mostrar_informacoes, permitir_sugestoes, " +
                        "generos_favoritos, foto " +
                        "FROM usuario WHERE id = ?",
                        id
                );

        if (resultados.isEmpty()) {
            return null;
        }

        Map<String, Object> linha = resultados.get(0);
        Map<String, Object> perfil = new LinkedHashMap<>();

        perfil.put("id", linha.get("id"));
        perfil.put("nome", linha.get("nome"));
        perfil.put("email", linha.get("email"));
        perfil.put("bio", linha.get("bio"));
        perfil.put("intencao", linha.get("intencao"));

        Object nascimento = linha.get("nascimento");

        perfil.put(
                "nascimento",
                nascimento == null ? null : nascimento.toString()
        );

        perfil.put(
                "identidadeGenero",
                linha.get("identidade_genero")
        );

        perfil.put("orientacao", linha.get("orientacao"));
        perfil.put("interesse", linha.get("interesse"));

        perfil.put(
                "mostrarInformacoes",
                booleano(linha.get("mostrar_informacoes"), false)
        );

        perfil.put(
                "permitirSugestoes",
                booleano(linha.get("permitir_sugestoes"), true)
        );

        perfil.put(
                "generosFavoritos",
                linha.get("generos_favoritos")
        );

        perfil.put("foto", linha.get("foto"));

        return perfil;
    }

    private String texto(
            Map<String, Object> dados,
            String chave) {

        Object valor = dados.get(chave);
        return valor == null ? "" : valor.toString();
    }

    private boolean booleano(
            Object valor,
            boolean padrao) {

        if (valor == null) {
            return padrao;
        }

        if (valor instanceof Boolean b) {
            return b;
        }

        if (valor instanceof Number n) {
            return n.intValue() != 0;
        }

        String texto = valor.toString().trim();

        if ("1".equals(texto)) {
            return true;
        }

        if ("0".equals(texto)) {
            return false;
        }

        return Boolean.parseBoolean(texto);
    }
}
