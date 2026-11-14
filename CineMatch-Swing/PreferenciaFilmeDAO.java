
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

/**
 * Gerencia curtidas e favoritos pela API CineMatch.
 *
 * O aplicativo Swing NÃO acessa diretamente o MySQL
 * para registrar ou consultar preferências.
 *
 * A API Spring Boot recebe as solicitações e salva
 * os dados no MySQL hospedado no Railway.
 */
public class PreferenciaFilmeDAO {

    private static final String URL_API =
            System.getenv().getOrDefault(
                    "CINEMATCH_API_URL",
                    "http://localhost:8081"
            ).replaceAll("/+$", "");

    private final HttpClient cliente = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private String ultimoErro = "";

    /**
     * Retorna o último erro ocorrido nesta instância.
     * Pode ser utilizado pelas telas para mostrar
     * uma mensagem mais clara ao usuário.
     */
    public String getUltimoErro() {
        return ultimoErro;
    }

    // =========================================
    // CURTIR FILME
    // =========================================

    public boolean marcarCurtido(int usuarioId, int filmeId) {
        return marcar(
                usuarioId,
                filmeId,
                "/api/preferencias/curtir"
        );
    }

    // =========================================
    // SALVAR FILME NOS FAVORITOS
    // =========================================

    public boolean marcarSalvo(int usuarioId, int filmeId) {
        return marcar(
                usuarioId,
                filmeId,
                "/api/preferencias/salvar"
        );
    }

    // =========================================
    // ENVIA CURTIDA OU FAVORITO PARA A API
    // =========================================

    private boolean marcar(
            int usuarioId,
            int filmeId,
            String caminho
    ) {

        ultimoErro = "";

        if (usuarioId <= 0 || filmeId <= 0) {
            ultimoErro = "Usuário ou filme inválido.";
            System.err.println(ultimoErro);
            return false;
        }

        if (!CineMatchApiCliente.tokenPertenceAoUsuario(
                usuarioId
        )) {
            ultimoErro = "Sua sessão não está autenticada. "
                    + "Entre novamente na sua conta.";

            System.err.println(ultimoErro);
            return false;
        }

        String dados = "{\"filmeId\":" + filmeId + "}";

        try {

            HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(URL_API + caminho))
                    .timeout(Duration.ofSeconds(12))
                    .header(
                            "Content-Type",
                            "application/json; charset=UTF-8"
                    )
                    .header(
                            "Authorization",
                            "Bearer "
                                    + CineMatchApiCliente.obterToken()
                    )
                    .POST(
                            HttpRequest.BodyPublishers.ofString(
                                    dados,
                                    StandardCharsets.UTF_8
                            )
                    )
                    .build();

            HttpResponse<String> resposta = cliente.send(
                    requisicao,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );

            if (resposta.statusCode() == 200) {
                return true;
            }

            ultimoErro = mensagemErroHttp(
                    resposta.statusCode()
            );

            System.err.println(
                    "Erro ao registrar preferência: "
                            + ultimoErro
            );

            return false;

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            ultimoErro = "A operação foi interrompida.";
            System.err.println(ultimoErro);

            return false;

        } catch (IOException | IllegalArgumentException e) {

            ultimoErro = "Não foi possível conectar à API "
                    + "do CineMatch. Verifique sua internet "
                    + "e se o Railway está funcionando.";

            System.err.println(
                    "Erro ao registrar preferência: "
                            + e.getMessage()
            );

            return false;
        }
    }

    // =========================================
    // CONTAR CURTIDAS DE UM FILME
    // =========================================

    public int contarCurtidas(int filmeId) {

        ultimoErro = "";

        if (filmeId <= 0) {
            ultimoErro = "Filme inválido.";
            return 0;
        }

        try {

            String caminho =
                    "/api/preferencias/filmes/"
                            + filmeId
                            + "/curtidas";

            HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(URL_API + caminho))
                    .timeout(Duration.ofSeconds(12))
                    .GET()
                    .build();

            HttpResponse<String> resposta = cliente.send(
                    requisicao,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );

            if (resposta.statusCode() != 200) {
                ultimoErro = mensagemErroHttp(
                        resposta.statusCode()
                );

                System.err.println(ultimoErro);
                return 0;
            }

            Pattern padrao = Pattern.compile(
                    "\"curtidas\"\\s*:\\s*(\\d+)"
            );

            Matcher matcher = padrao.matcher(
                    resposta.body()
            );

            if (!matcher.find()) {
                ultimoErro = "A API retornou uma "
                        + "contagem de curtidas inválida.";

                System.err.println(ultimoErro);
                return 0;
            }

            return Integer.parseInt(
                    matcher.group(1)
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            ultimoErro = "A consulta foi interrompida.";
            System.err.println(ultimoErro);

            return 0;

        } catch (IOException
                 | IllegalArgumentException e) {

            ultimoErro = "Não foi possível consultar "
                    + "as curtidas pela API.";

            System.err.println(
                    ultimoErro + " " + e.getMessage()
            );

            return 0;
        }
    }

    // =========================================
    // CONSULTAR IDs DOS FILMES CURTIDOS
    // =========================================

    public Set<Integer> idsCurtidos(int usuarioId) {
        return consultarIds(
                usuarioId,
                "/api/preferencias/curtidos"
        );
    }

    // =========================================
    // CONSULTAR IDs DOS FILMES SALVOS
    // =========================================

    public Set<Integer> idsSalvos(int usuarioId) {
        return consultarIds(
                usuarioId,
                "/api/preferencias/salvos"
        );
    }

    // =========================================
    // CONSULTA AS PREFERÊNCIAS NA API
    // =========================================

    private Set<Integer> consultarIds(
            int usuarioId,
            String caminho
    ) {

        ultimoErro = "";

        Set<Integer> ids = new HashSet<>();

        if (!CineMatchApiCliente.tokenPertenceAoUsuario(
                usuarioId
        )) {

            ultimoErro = "Entre na sua conta "
                    + "para consultar seus filmes.";

            System.err.println(ultimoErro);
            return ids;
        }

        try {

            HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(URL_API + caminho))
                    .timeout(Duration.ofSeconds(12))
                    .header(
                            "Authorization",
                            "Bearer "
                                    + CineMatchApiCliente.obterToken()
                    )
                    .GET()
                    .build();

            HttpResponse<String> resposta = cliente.send(
                    requisicao,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );

            if (resposta.statusCode() != 200) {

                ultimoErro = mensagemErroHttp(
                        resposta.statusCode()
                );

                System.err.println(ultimoErro);
                return ids;
            }

            String json = resposta.body().trim();

            if (!json.startsWith("[")
                    || !json.endsWith("]")) {

                throw new IOException(
                        "A API retornou uma lista inválida."
                );
            }

            String conteudo = json.substring(
                    1,
                    json.length() - 1
            ).trim();

            if (conteudo.isEmpty()) {
                return ids;
            }

            String[] valores = conteudo.split(",");

            for (String valor : valores) {

                String texto = valor.trim();

                if (!texto.matches("\\d+")) {
                    throw new IOException(
                            "ID de filme inválido na resposta."
                    );
                }

                ids.add(
                        Integer.parseInt(texto)
                );
            }

            return ids;

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            ultimoErro = "A consulta foi interrompida.";
            System.err.println(ultimoErro);

            return new HashSet<>();

        } catch (IOException
                 | IllegalArgumentException e) {

            ultimoErro = "Não foi possível consultar "
                    + "suas preferências na API.";

            System.err.println(
                    ultimoErro + " " + e.getMessage()
            );

            return new HashSet<>();
        }
    }

    // =========================================
    // LISTAR FILMES CURTIDOS
    // =========================================

    public List<Filme> listarCurtidos(int usuarioId) {
        return listar(
                usuarioId,
                true
        );
    }

    // =========================================
    // LISTAR FILMES SALVOS
    // =========================================

    public List<Filme> listarSalvos(int usuarioId) {
        return listar(
                usuarioId,
                false
        );
    }

    // =========================================
    // MONTA LISTA DE FILMES
    // =========================================

    private List<Filme> listar(
            int usuarioId,
            boolean curtidos
    ) {

        List<Filme> resultado = new ArrayList<>();

        Set<Integer> ids = curtidos
                ? idsCurtidos(usuarioId)
                : idsSalvos(usuarioId);

        if (ids.isEmpty()) {
            return resultado;
        }

        /*
         * As preferências já vêm do Railway.
         *
         * Por enquanto, reutilizamos FilmeDAO
         * para obter os dados dos filmes.
         *
         * Depois vamos conferir se FilmeDAO
         * também está usando a API.
         */
        List<Filme> todos = new FilmeDAO().listarTodos();

        for (Filme filme : todos) {

            if (ids.contains(filme.getId())) {
                resultado.add(filme);
            }
        }

        return resultado;
    }

    // =========================================
    // TRADUZ ERROS HTTP
    // =========================================

    private String mensagemErroHttp(int codigo) {

        return switch (codigo) {

            case 400 ->
                    "A API recebeu dados inválidos.";

            case 401 ->
                    "Sua sessão expirou ou é inválida. "
                    + "Entre novamente na sua conta.";

            case 403 ->
                    "Você não tem permissão "
                    + "para realizar esta operação.";

            case 404 ->
                    "O filme ou recurso não foi encontrado.";

            case 429 ->
                    "Muitas solicitações. "
                    + "Aguarde um pouco e tente novamente.";

            case 500, 502, 503, 504 ->
                    "O servidor está com um problema "
                    + "temporário. Tente novamente.";

            default ->
                    "Erro na API do CineMatch. "
                    + "Código HTTP: " + codigo;
        };
    }
}
