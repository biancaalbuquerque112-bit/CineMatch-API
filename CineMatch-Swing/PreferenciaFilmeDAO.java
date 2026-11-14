
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Curtidas e favoritos consultados pela API, sem acesso direto ao MySQL. */
public class PreferenciaFilmeDAO {

    // Usa a mesma URL do login, cadastro e catálogo.
    private static final String URL_API =
            CineMatchApiCliente.obterUrlApi();

    private final HttpClient cliente = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private String ultimoErro;

    public String getUltimoErro() {
        return ultimoErro;
    }

    public boolean marcarCurtido(int usuarioId, int filmeId) {
        return marcar(usuarioId, filmeId, "/api/preferencias/curtir");
    }

    public boolean marcarSalvo(int usuarioId, int filmeId) {
        return marcar(usuarioId, filmeId, "/api/preferencias/salvar");
    }

    private boolean marcar(int usuarioId, int filmeId, String caminho) {
        ultimoErro = null;

        if (!usuarioAtual(usuarioId) || filmeId <= 0) {
            ultimoErro = "Usuário ou filme inválido, ou sessão não iniciada.";
            System.err.println(ultimoErro);
            return false;
        }

        String token = CineMatchApiCliente.obterToken();

        if (token == null || token.isBlank()) {
            ultimoErro = "Sua sessão expirou. Saia da conta e faça login novamente.";
            System.err.println(ultimoErro);
            return false;
        }

        String json = "{\"filmeId\":" + filmeId + "}";

        try {
            HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(URL_API + caminho))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resposta = enviar(requisicao);

            if (resposta.statusCode() == 200) {
                return true;
            }

            informarErro(resposta.statusCode());
            return false;

        } catch (IOException | IllegalArgumentException e) {
            erroConexao("registrar preferência", e);
            return false;
        }
    }

    public int contarCurtidas(int filmeId) {
        ultimoErro = null;

        if (filmeId <= 0) {
            return 0;
        }

        try {
            HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(
                            URL_API
                            + "/api/preferencias/filmes/"
                            + filmeId
                            + "/curtidas"
                    ))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> resposta = enviar(requisicao);

            if (resposta.statusCode() != 200) {
                informarErro(resposta.statusCode());
                return 0;
            }

            Matcher matcher = Pattern.compile(
                    "\"curtidas\"\\s*:\\s*(\\d+)"
            ).matcher(resposta.body());

            if (!matcher.find()) {
                throw new IOException(
                        "A API não retornou a contagem de curtidas."
                );
            }

            return Integer.parseInt(matcher.group(1));

        } catch (IOException | IllegalArgumentException e) {
            erroConexao("contar curtidas", e);
            return 0;
        }
    }

    public Set<Integer> idsCurtidos(int usuarioId) {
        return ids(usuarioId, "/api/preferencias/curtidos");
    }

    public Set<Integer> idsSalvos(int usuarioId) {
        return ids(usuarioId, "/api/preferencias/salvos");
    }

    private Set<Integer> ids(int usuarioId, String caminho) {
        ultimoErro = null;

        Set<Integer> resultado = new HashSet<>();

        if (!usuarioAtual(usuarioId)) {
            ultimoErro = "Entre na sua conta para consultar suas preferências.";
            return resultado;
        }

        String token = CineMatchApiCliente.obterToken();

        if (token == null || token.isBlank()) {
            ultimoErro = "Sua sessão expirou. Saia da conta e faça login novamente.";
            System.err.println(ultimoErro);
            return resultado;
        }

        try {
            HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(URL_API + caminho))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> resposta = enviar(requisicao);

            if (resposta.statusCode() != 200) {
                informarErro(resposta.statusCode());
                return resultado;
            }

            String corpo = resposta.body();

            if (!corpo.matches(
                    "(?s)\\s*\\[\\s*(?:\\d+\\s*(?:,\\s*\\d+\\s*)*)?\\]\\s*"
            )) {
                throw new IOException(
                        "A API retornou uma lista de IDs inválida."
                );
            }

            Matcher matcher = Pattern.compile("\\d+").matcher(corpo);

            while (matcher.find()) {
                resultado.add(Integer.parseInt(matcher.group()));
            }

        } catch (IOException | IllegalArgumentException e) {
            erroConexao("consultar preferências", e);
        }

        return resultado;
    }

    public List<Filme> listarCurtidos(int usuarioId) {
        return listar(usuarioId, idsCurtidos(usuarioId));
    }

    public List<Filme> listarSalvos(int usuarioId) {
        return listar(usuarioId, idsSalvos(usuarioId));
    }

    private List<Filme> listar(int usuarioId, Set<Integer> ids) {
        List<Filme> resultado = new ArrayList<>();

        if (!usuarioAtual(usuarioId) || ids.isEmpty()) {
            return resultado;
        }

        for (Filme filme : new FilmeDAO().listarTodos()) {
            if (ids.contains(filme.getId())) {
                resultado.add(filme);
            }
        }

        return resultado;
    }

    private boolean usuarioAtual(int usuarioId) {
        Usuario usuario = Sessao.getUsuario();

        return usuarioId > 0
                && usuario != null
                && usuario.getId() == usuarioId;
    }

    private HttpResponse<String> enviar(HttpRequest requisicao)
            throws IOException {

        try {
            return cliente.send(
                    requisicao,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IOException(
                    "Requisição interrompida.",
                    e
            );
        }
    }

    private void informarErro(int status) {
        if (status == 401) {
            ultimoErro = "Sessão expirada ou inválida. Faça login novamente.";

        } else if (status == 403) {
            ultimoErro = "A API não autorizou esta operação (HTTP 403).";

        } else {
            ultimoErro = "Erro da API de preferências (HTTP "
                    + status + ").";
        }

        System.err.println(ultimoErro);
    }

    private void erroConexao(String operacao, Exception e) {
        ultimoErro = "Não foi possível conectar à API do CineMatch. "
                + "Verifique sua internet e se o Railway está funcionando.";

        System.err.println(
                "Erro ao " + operacao
                + " usando " + URL_API
                + ": " + e
        );
    }
}
