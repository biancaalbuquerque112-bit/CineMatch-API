
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CineMatchApiCliente {

    // Usa o Railway automaticamente em qualquer computador.
    // A variavel de ambiente continua opcional para desenvolvimento.
    private static final String URL_API = configurarUrlApi();

    private static String configurarUrlApi() {
        String url = System.getenv("CINEMATCH_API_URL");
        if (url == null || url.isBlank()) {
            url = "https://cinematch-production-7d60.up.railway.app";
        }
        return url.trim().replaceAll("/+$", "");
    }

    public static String obterUrlApi() {
        return URL_API;
    }

    // Uma sessão ativa por instância do aplicativo desktop.
    private static volatile String tokenSessao;
    private static volatile int usuarioIdToken;

    private final HttpClient cliente = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static String obterToken() {
        return tokenSessao;
    }

    public static void limparToken() {
        tokenSessao = null;
        usuarioIdToken = 0;
    }

    public static boolean tokenPertenceAoUsuario(int usuarioId) {
        return usuarioId > 0 && usuarioIdToken == usuarioId
                && tokenSessao != null && !tokenSessao.isBlank();
    }

    public String fazerLogin(String email, String senha)
            throws IOException, InterruptedException {

        limparToken();

        String dados = "{"
                + "\"email\":\"" + escaparJson(email) + "\","
                + "\"senha\":\"" + escaparJson(senha) + "\""
                + "}";

        String resposta = enviarPost("/api/login", dados, 200);
        Map<String, String> dadosResposta = lerJsonSimples(resposta);
        String token = dadosResposta.get("token");
        String idTexto = dadosResposta.get("id");
        int id;

        try {
            id = Integer.parseInt(idTexto);
        } catch (NumberFormatException e) {
            throw new IOException(
                    "A API não retornou um ID de usuário válido.", e
            );
        }

        if (token == null || token.isBlank() || id <= 0) {
            throw new IOException(
                    "A API não retornou uma sessão válida."
            );
        }

        usuarioIdToken = id;
        tokenSessao = token;

        return resposta;
    }

    public Usuario autenticarUsuario(String email, String senha)
            throws IOException, InterruptedException {

        try {
            Usuario usuario = converterUsuario(
                    fazerLogin(email, senha)
            );

            if (!tokenPertenceAoUsuario(usuario.getId())) {
                throw new IOException(
                        "O usuário autenticado não corresponde "
                        + "ao token recebido."
                );
            }

            return usuario;

        } catch (IOException | InterruptedException e) {
            limparToken();
            throw e;
        }
    }

    public String cadastrar(
            String nome,
            String email,
            String senha,
            LocalDate nascimento
    ) throws IOException, InterruptedException {

        if (nascimento == null) {
            throw new IOException(
                    "Informe a data de nascimento."
            );
        }

        String dados = "{"
                + "\"nome\":\"" + escaparJson(nome) + "\","
                + "\"email\":\"" + escaparJson(email) + "\","
                + "\"senha\":\"" + escaparJson(senha) + "\","
                + "\"nascimento\":\"" + nascimento + "\""
                + "}";

        return enviarPost("/api/cadastro", dados, 201);
    }

    public Usuario converterUsuario(String json)
            throws IOException {

        Map<String, String> dados = lerJsonSimples(json);
        Usuario usuario = new Usuario();

        try {
            String id = dados.get("id");

            if (id == null) {
                throw new IOException(
                        "A API não retornou o ID do usuário."
                );
            }

            usuario.setId(Integer.parseInt(id));

            String nome = dados.get("nome");

            if (nome == null || nome.isBlank()) {
                throw new IOException(
                        "A API não retornou o nome do usuário."
                );
            }

            usuario.setNome(nome);
            usuario.setEmail(dados.get("email"));
            usuario.setBio(dados.get("bio"));
            usuario.setIntencao(dados.get("intencao"));

            String nascimento = dados.get("nascimento");

            if (nascimento != null && !nascimento.isBlank()) {
                usuario.setNascimento(
                        LocalDate.parse(nascimento)
                );
            }

            usuario.setIdentidadeGenero(
                    valorOuPadrao(
                            dados.get("identidadeGenero"),
                            "Prefiro não informar"
                    )
            );

            usuario.setOrientacao(
                    valorOuPadrao(
                            dados.get("orientacao"),
                            "Prefiro não informar"
                    )
            );

            usuario.setInteresse(
                    valorOuPadrao(
                            dados.get("interesse"),
                            "Sem preferência"
                    )
            );

            usuario.setGenerosFavoritos(
                    valorOuPadrao(
                            dados.get("generosFavoritos"),
                            ""
                    )
            );

            usuario.setFoto(
                    valorOuPadrao(
                            dados.get("foto"),
                            ""
                    )
            );

            usuario.setMostrarInformacoes(
                    Boolean.parseBoolean(
                            dados.get("mostrarInformacoes")
                    )
            );

            String permitir = dados.get("permitirSugestoes");

            usuario.setPermitirSugestoes(
                    permitir == null
                            || Boolean.parseBoolean(permitir)
            );

            // Nunca coloca a senha no objeto Usuario.
            return usuario;

        } catch (NumberFormatException
                 | DateTimeParseException e) {

            throw new IOException(
                    "A API retornou dados inválidos do perfil.",
                    e
            );
        }
    }

    public int extrairId(String json) throws IOException {
        try {
            return Integer.parseInt(
                    lerJsonSimples(json).get("id")
            );

        } catch (NumberFormatException e) {
            throw new IOException(
                    "A API não retornou um ID válido.",
                    e
            );
        }
    }

    // Mantido por compatibilidade.
    // Usa o token para consultar o perfil.
    public String buscarPerfil(int id)
            throws IOException, InterruptedException {

        if (!tokenPertenceAoUsuario(id)) {
            throw new IOException(
                    "Entre na sua conta para consultar seu perfil."
            );
        }

        HttpRequest requisicao = HttpRequest.newBuilder()
                .uri(URI.create(
                        URL_API + "/api/usuarios/" + id
                ))
                .timeout(Duration.ofSeconds(10))
                .header(
                        "Authorization",
                        "Bearer " + obterToken()
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
            throw new IOException(
                    "Erro ao buscar perfil. HTTP: "
                    + resposta.statusCode()
            );
        }

        return resposta.body();
    }

    private String enviarPost(
            String caminho,
            String dados,
            int statusEsperado
    ) throws IOException, InterruptedException {

        HttpRequest requisicao = HttpRequest.newBuilder()
                .uri(URI.create(URL_API + caminho))
                .timeout(Duration.ofSeconds(10))
                .header(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                )
                .POST(
                        HttpRequest.BodyPublishers.ofString(
                                dados,
                                StandardCharsets.UTF_8
                        )
                )
                .build();

        HttpResponse<String> resposta;

        try {
            resposta = cliente.send(
                    requisicao,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );

        } catch (ConnectException e) {
            throw new IOException(
                    "Não foi possível conectar ao servidor do CineMatch. "
                    + "Verifique sua internet e tente novamente.",
                    e
            );
        }

        if (resposta.statusCode() == statusEsperado) {
            return resposta.body();
        }

        if (resposta.statusCode() == 401) {
            throw new IOException(
                    "E-mail ou senha incorretos."
            );
        }

        if (resposta.statusCode() == 409) {
            throw new IOException(
                    "Não foi possível cadastrar. "
                    + "Verifique se o e-mail já está em uso."
            );
        }

        if (resposta.statusCode() == 400) {
            throw new IOException(
                    "Dados inválidos. "
                    + "Confira as informações preenchidas."
            );
        }

        throw new IOException(
                "Erro na API. Código HTTP: "
                + resposta.statusCode()
        );
    }

    private String valorOuPadrao(
            String valor,
            String padrao
    ) {
        return valor == null ? padrao : valor;
    }

    // Lê campos simples do JSON de login.
    // Não serve para JSON aninhado.
    private Map<String, String> lerJsonSimples(String json)
            throws IOException {

        if (json == null
                || !json.trim().startsWith("{")) {
            throw new IOException(
                    "Resposta JSON inválida da API."
            );
        }

        Map<String, String> resultado = new HashMap<>();

        String stringJson = "\"(?:\\\\.|[^\"\\\\])*\"";

        Pattern padrao = Pattern.compile(
                "(" + stringJson + ")\\s*:\\s*"
                + "(" + stringJson
                + "|null|true|false|-?\\d+)"
        );

        Matcher matcher = padrao.matcher(json);

        while (matcher.find()) {
            String chave = decodificarStringJson(
                    matcher.group(1)
            );

            String bruto = matcher.group(2);

            String valor = "null".equals(bruto)
                    ? null
                    : bruto.startsWith("\"")
                    ? decodificarStringJson(bruto)
                    : bruto;

            resultado.put(chave, valor);
        }

        if (resultado.isEmpty()) {
            throw new IOException(
                    "A API retornou JSON sem campos reconhecidos."
            );
        }

        return resultado;
    }

    private String decodificarStringJson(String texto)
            throws IOException {

        if (texto == null
                || texto.length() < 2
                || texto.charAt(0) != '"'
                || texto.charAt(texto.length() - 1) != '"') {

            throw new IOException(
                    "Texto JSON inválido."
            );
        }

        StringBuilder resultado = new StringBuilder();

        for (int i = 1; i < texto.length() - 1; i++) {

            char c = texto.charAt(i);

            if (c != '\\') {
                resultado.append(c);
                continue;
            }

            i++;

            if (i >= texto.length() - 1) {
                throw new IOException(
                        "Escape JSON incompleto."
                );
            }

            switch (texto.charAt(i)) {

                case '"' -> resultado.append('"');

                case '\\' -> resultado.append('\\');

                case '/' -> resultado.append('/');

                case 'b' -> resultado.append('\b');

                case 'f' -> resultado.append('\f');

                case 'n' -> resultado.append('\n');

                case 'r' -> resultado.append('\r');

                case 't' -> resultado.append('\t');

                case 'u' -> {

                    if (i + 4 >= texto.length() - 1) {
                        throw new IOException(
                                "Unicode JSON incompleto."
                        );
                    }

                    try {
                        resultado.append(
                                (char) Integer.parseInt(
                                        texto.substring(
                                                i + 1,
                                                i + 5
                                        ),
                                        16
                                )
                        );

                    } catch (NumberFormatException e) {
                        throw new IOException(
                                "Unicode JSON inválido.",
                                e
                        );
                    }

                    i += 4;
                }

                default -> throw new IOException(
                        "Escape JSON inválido."
                );
            }
        }

        return resultado.toString();
    }

    private String escaparJson(String texto) {

        if (texto == null) {
            return "";
        }

        StringBuilder resultado = new StringBuilder();

        for (char c : texto.toCharArray()) {

            switch (c) {

                case '"' -> resultado.append("\\\"");

                case '\\' -> resultado.append("\\\\");

                case '\n' -> resultado.append("\\n");

                case '\r' -> resultado.append("\\r");

                case '\t' -> resultado.append("\\t");

                default -> {

                    if (c < 0x20) {
                        resultado.append(
                                String.format(
                                        "\\u%04x",
                                        (int) c
                                )
                        );

                    } else {
                        resultado.append(c);
                    }
                }
            }
        }

        return resultado.toString();
    }
}
