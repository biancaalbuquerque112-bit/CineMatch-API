
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Acesso a usuarios pela API do CineMatch.
 * Nao utiliza conexao direta com o MySQL.
 */
public class UsuarioDAO {

    private final CineMatchApiCliente api =
            new CineMatchApiCliente();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public boolean cadastrar(Usuario u) {

        if (u == null
                || u.getNome() == null
                || u.getNome().isBlank()
                || u.getEmail() == null
                || !u.getEmail().contains("@")
                || u.getSenha() == null
                || u.getSenha().length() < 6
                || u.getNascimento() == null
                || !u.maiorDeIdade()) {
            return false;
        }

        try {
            String resposta = api.cadastrar(
                    u.getNome().trim(),
                    u.getEmail().trim().toLowerCase(),
                    u.getSenha(),
                    u.getNascimento()
            );

            try {
                u.setId(api.extrairId(resposta));
            } catch (IOException ignorado) {
                // Nem toda resposta de cadastro inclui o ID.
            }

            return true;

        } catch (IOException | InterruptedException e) {
            registrarErro("Cadastro", e);
            return false;
        }
    }

    public Usuario autenticar(String email, String senha) {

        if (email == null || senha == null) {
            return null;
        }

        try {
            return api.autenticarUsuario(
                    email.trim(),
                    senha
            );

        } catch (IOException | InterruptedException e) {
            registrarErro("Login", e);
            return null;
        }
    }

    public boolean atualizarPerfil(Usuario u) {

        if (u == null
                || u.getId() <= 0
                || u.getNome() == null
                || u.getNome().isBlank()
                || u.getNascimento() == null
                || !u.maiorDeIdade()
                || !CineMatchApiCliente
                        .tokenPertenceAoUsuario(u.getId())) {
            return false;
        }

        String dados = "{"
                + "\"nome\":" + json(u.getNome().trim()) + ","
                + "\"nascimento\":"
                + json(u.getNascimento().toString()) + ","
                + "\"bio\":" + json(u.getBio()) + ","
                + "\"intencao\":" + json(u.getIntencao()) + ","
                + "\"identidadeGenero\":"
                + json(u.getIdentidadeGenero()) + ","
                + "\"orientacao\":"
                + json(u.getOrientacao()) + ","
                + "\"interesse\":"
                + json(u.getInteresse()) + ","
                + "\"mostrarInformacoes\":"
                + u.isMostrarInformacoes() + ","
                + "\"permitirSugestoes\":"
                + u.isPermitirSugestoes() + ","
                + "\"generosFavoritos\":"
                + json(u.getGenerosFavoritos()) + ","
                + "\"foto\":" + json(u.getFoto())
                + "}";

        try {
            requisitar(
                    "PUT",
                    "/api/usuarios/" + u.getId(),
                    dados
            );

            return true;

        } catch (IOException | InterruptedException e) {
            registrarErro("Salvar perfil", e);
            return false;
        }
    }

    public boolean atualizarPerfil(
            String email,
            String bio,
            String intencao) {

        if (!Sessao.ativa()
                || Sessao.getUsuario() == null
                || email == null) {
            return false;
        }

        Usuario logado = Sessao.getUsuario();

        if (logado.getEmail() == null
                || !logado.getEmail()
                        .equalsIgnoreCase(email.trim())) {
            return false;
        }

        Usuario atual = buscarPorId(logado.getId());

        if (atual == null) {
            return false;
        }

        atual.setBio(bio);
        atual.setIntencao(intencao);

        return atualizarPerfil(atual);
    }

    public Usuario buscarPorId(int id) {

        // A rota atual permite consultar somente
        // o perfil da propria conta autenticada.
        if (!CineMatchApiCliente
                .tokenPertenceAoUsuario(id)) {
            return null;
        }

        try {
            String resposta = requisitar(
                    "GET",
                    "/api/usuarios/" + id,
                    null
            );

            return api.converterUsuario(resposta);

        } catch (IOException | InterruptedException e) {
            registrarErro("Buscar perfil", e);
            return null;
        }
    }

    public List<Usuario> listarTodos() {

        List<Usuario> usuarios = new ArrayList<>();

        try {
            String resposta = requisitar(
                    "GET",
                    "/api/usuarios",
                    null
            );

            // A API retorna ID e nome.
            Matcher objetos = Pattern
                    .compile("\\{[^{}]*\\}")
                    .matcher(resposta);

            while (objetos.find()) {
                try {
                    Usuario usuario = api.converterUsuario(
                            objetos.group()
                    );

                    usuarios.add(usuario);

                } catch (IOException e) {
                    registrarErro(
                            "Ler usuario da lista",
                            e
                    );
                }
            }

        } catch (IOException | InterruptedException e) {
            registrarErro("Listar usuarios", e);
        }

        return usuarios;
    }

    private String requisitar(
            String metodo,
            String caminho,
            String corpo)
            throws IOException, InterruptedException {

        String token = CineMatchApiCliente.obterToken();

        if (token == null || token.isBlank()) {
            throw new IOException(
                    "Entre novamente na sua conta."
            );
        }

        HttpRequest.Builder builder =
                HttpRequest.newBuilder()
                        .uri(URI.create(
                                CineMatchApiCliente.obterUrlApi()
                                + caminho
                        ))
                        .timeout(Duration.ofSeconds(15))
                        .header(
                                "Authorization",
                                "Bearer " + token
                        );

        if ("PUT".equals(metodo)) {

            builder.header(
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );

            builder.PUT(
                    HttpRequest.BodyPublishers.ofString(
                            corpo,
                            StandardCharsets.UTF_8
                    )
            );

        } else {
            builder.GET();
        }

        HttpResponse<String> resposta = http.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8
                )
        );

        int status = resposta.statusCode();

        if (status == 401) {
            throw new IOException(
                    "Sessao expirada. Faca login novamente."
            );
        }

        if (status == 403) {
            throw new IOException(
                    "Acesso negado ao perfil."
            );
        }

        if (status != 200) {
            throw new IOException(
                    "A API respondeu HTTP "
                    + status + ": " + resposta.body()
            );
        }

        return resposta.body();
    }

    private static String json(String valor) {

        if (valor == null) {
            return "\"\"";
        }

        StringBuilder s = new StringBuilder("\"");

        for (int i = 0; i < valor.length(); i++) {

            char c = valor.charAt(i);

            switch (c) {

                case '"' -> s.append("\\\"");

                case '\\' -> s.append("\\\\");

                case '\n' -> s.append("\\n");

                case '\r' -> s.append("\\r");

                case '\t' -> s.append("\\t");

                default -> {
                    if (c < 0x20) {
                        s.append(
                                String.format(
                                        "\\u%04x",
                                        (int) c
                                )
                        );
                    } else {
                        s.append(c);
                    }
                }
            }
        }

        return s.append('"').toString();
    }

    private static void registrarErro(
            String operacao,
            Exception e) {

        System.err.println(
                "UsuarioDAO - "
                + operacao + ": "
                + e.getMessage()
        );

        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
