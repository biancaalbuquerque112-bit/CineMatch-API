
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consulta os filmes pela API CineMatch,
 * sem acessar diretamente o MySQL.
 */
public class FilmeDAO {

    private static final String URL_API =
            System.getenv().getOrDefault(
                    "CINEMATCH_API_URL",
                    "http://localhost:8081"
            ).replaceAll("/+$", "");

    private final HttpClient cliente =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

    private final Catalogo catalogo = new Catalogo();

    // LISTAR OS FILMES OFICIAIS
    public List<Filme> listarTodos() {

        return apenasFilmesOficiais(
                consultar("/api/filmes")
        );
    }

    // PESQUISAR POR GÊNERO OU TÍTULO
    public List<Filme> buscarPorGenero(String busca) {

        String termo = busca == null ? "" : busca;

        String codificado = URLEncoder.encode(
                termo,
                StandardCharsets.UTF_8
        );

        return apenasFilmesOficiais(
                consultar(
                        "/api/filmes/buscar?termo="
                                + codificado
                )
        );
    }

    // BUSCAR UM FILME PELO TÍTULO EXATO
    public Filme buscarPorTitulo(String titulo) {

        if (titulo == null || titulo.isBlank()) {
            return null;
        }

        String codificado = URLEncoder.encode(
                titulo,
                StandardCharsets.UTF_8
        );

        List<Filme> encontrados = consultar(
                "/api/filmes/buscar?termo="
                        + codificado
        );

        for (Filme filme : encontrados) {

            if (filme.getTitulo() != null
                    && filme.getTitulo()
                            .equalsIgnoreCase(titulo)) {

                return filme;
            }
        }

        return null;
    }

    // SINCRONIZAÇÃO SERÁ RESPONSABILIDADE DA API
    public void sincronizarCatalogo() {

        System.err.println(
                "Aviso: sincronizarCatalogo() não altera "
                        + "mais o banco. A sincronização "
                        + "dos 50 títulos deve ser feita "
                        + "no servidor da API."
        );
    }

    public void popularBancoSeVazio() {
        sincronizarCatalogo();
    }

    // AINDA NÃO EXISTE ROTA SEGURA PARA AVALIAÇÕES
    public boolean atualizarAvaliacao(Filme filme) {

        System.err.println(
                "Avaliação não salva: a API ainda "
                        + "não possui rota autenticada "
                        + "de avaliação."
        );

        return false;
    }

    // AINDA NÃO EXISTE ROTA SEGURA PARA CADASTRAR FILMES
    public boolean salvar(Filme filme) {

        System.err.println(
                "Filme não salvo: a API ainda "
                        + "não possui rota autenticada "
                        + "de cadastro de filmes."
        );

        return false;
    }

    // MANTÉM O FILTRO DOS 50 FILMES DO CATALOGO.JAVA
    private List<Filme> apenasFilmesOficiais(
            List<Filme> filmes
    ) {

        List<Filme> oficiais = new ArrayList<>();

        for (Filme filme : filmes) {

            if (filme.getTitulo() != null
                    && catalogo.buscarPorTitulo(
                            filme.getTitulo()
                    ) != null) {

                oficiais.add(filme);
            }
        }

        return oficiais;
    }

    // REALIZA A CONSULTA HTTP
    private List<Filme> consultar(String caminho) {

        HttpRequest requisicao =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        URL_API + caminho
                                )
                        )
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

        try {

            HttpResponse<String> resposta =
                    cliente.send(
                            requisicao,
                            HttpResponse.BodyHandlers.ofString(
                                    StandardCharsets.UTF_8
                            )
                    );

            if (resposta.statusCode() != 200) {

                throw new IllegalStateException(
                        "A API retornou HTTP "
                                + resposta.statusCode()
                                + " ao consultar filmes."
                );
            }

            return converterFilmes(resposta.body());

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Consulta de filmes interrompida.",
                    e
            );

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Não foi possível consultar os filmes. "
                            + "Verifique se a API está ligada.",
                    e
            );
        }
    }

    // CONVERTE A LISTA JSON EM OBJETOS FILME
    private List<Filme> converterFilmes(String json)
            throws IOException {

        Object resposta = new LeitorJson(json).lerTudo();

        if (!(resposta instanceof List<?> registros)) {

            throw new IOException(
                    "A API não retornou uma lista de filmes."
            );
        }

        List<Filme> filmes = new ArrayList<>();

        for (Object registro : registros) {

            if (!(registro instanceof Map<?, ?> dados)) {

                throw new IOException(
                        "Registro de filme inválido na API."
                );
            }

            Filme filme = new Filme(
                    numero(dados, "id"),
                    texto(dados, "titulo"),
                    texto(dados, "genero"),
                    numero(dados, "ano"),
                    texto(dados, "capa"),
                    texto(dados, "diretor")
            );

            filme.setCurtidas(
                    numero(dados, "curtidas")
            );

            filme.setNota(
                    numero(dados, "nota")
            );

            // A API ainda não envia "rejeicoes".
            // O valor permanece 0, conforme Filme.java.

            filmes.add(filme);
        }

        return filmes;
    }

    private String texto(
            Map<?, ?> dados,
            String campo
    ) throws IOException {

        Object valor = dados.get(campo);

        if (valor == null) {
            return null;
        }

        if (valor instanceof String s) {
            return s;
        }

        throw new IOException(
                "Campo inválido no filme: " + campo
        );
    }

    private int numero(
            Map<?, ?> dados,
            String campo
    ) throws IOException {

        Object valor = dados.get(campo);

        if (valor instanceof Number n) {
            return n.intValue();
        }

        throw new IOException(
                "Campo numérico inválido no filme: "
                        + campo
        );
    }

    /**
     * Leitor de JSON sem dependências externas.
     * Aceita listas, objetos, strings, números,
     * booleanos e null.
     */
    private static final class LeitorJson {

        private final String fonte;
        private int posicao;

        LeitorJson(String fonte) {
            this.fonte = fonte == null ? "" : fonte;
        }

        Object lerTudo() throws IOException {

            Object valor = lerValor();

            pularEspacos();

            if (posicao != fonte.length()) {
                throw erro("Conteúdo após o JSON");
            }

            return valor;
        }

        private Object lerValor() throws IOException {

            pularEspacos();

            if (posicao >= fonte.length()) {
                throw erro("JSON incompleto");
            }

            char c = fonte.charAt(posicao);

            if (c == '"') {
                return lerString();
            }

            if (c == '{') {
                return lerObjeto();
            }

            if (c == '[') {
                return lerLista();
            }

            if (c == 't') {
                literal("true");
                return Boolean.TRUE;
            }

            if (c == 'f') {
                literal("false");
                return Boolean.FALSE;
            }

            if (c == 'n') {
                literal("null");
                return null;
            }

            if (c == '-'
                    || (c >= '0' && c <= '9')) {

                return lerNumero();
            }

            throw erro("Valor JSON inválido");
        }

        private Map<String, Object> lerObjeto()
                throws IOException {

            consumir('{');

            Map<String, Object> objeto =
                    new LinkedHashMap<>();

            pularEspacos();

            if (tentar('}')) {
                return objeto;
            }

            do {

                pularEspacos();

                if (posicao >= fonte.length()
                        || fonte.charAt(posicao) != '"') {

                    throw erro("Chave JSON inválida");
                }

                String chave = lerString();

                consumir(':');

                objeto.put(
                        chave,
                        lerValor()
                );

                pularEspacos();

                if (tentar('}')) {
                    return objeto;
                }

                consumir(',');

            } while (true);
        }

        private List<Object> lerLista()
                throws IOException {

            consumir('[');

            List<Object> lista = new ArrayList<>();

            pularEspacos();

            if (tentar(']')) {
                return lista;
            }

            do {

                lista.add(lerValor());

                pularEspacos();

                if (tentar(']')) {
                    return lista;
                }

                consumir(',');

            } while (true);
        }

        private String lerString()
                throws IOException {

            consumir('"');

            StringBuilder texto = new StringBuilder();

            while (posicao < fonte.length()) {

                char c = fonte.charAt(posicao++);

                if (c == '"') {
                    return texto.toString();
                }

                if (c == '\\') {

                    if (posicao >= fonte.length()) {
                        throw erro("Escape incompleto");
                    }

                    char escape = fonte.charAt(
                            posicao++
                    );

                    switch (escape) {

                        case '"' -> texto.append('"');

                        case '\\' -> texto.append('\\');

                        case '/' -> texto.append('/');

                        case 'b' -> texto.append('\b');

                        case 'f' -> texto.append('\f');

                        case 'n' -> texto.append('\n');

                        case 'r' -> texto.append('\r');

                        case 't' -> texto.append('\t');

                        case 'u' -> {

                            if (posicao + 4
                                    > fonte.length()) {

                                throw erro(
                                        "Unicode incompleto"
                                );
                            }

                            String hex = fonte.substring(
                                    posicao,
                                    posicao + 4
                            );

                            try {

                                texto.append(
                                        (char) Integer.parseInt(
                                                hex, 16
                                        )
                                );

                            } catch (
                                    NumberFormatException e
                            ) {

                                throw erro(
                                        "Unicode inválido"
                                );
                            }

                            posicao += 4;
                        }

                        default -> throw erro(
                                "Escape inválido"
                        );
                    }

                } else {

                    if (c < 0x20) {
                        throw erro(
                                "Caractere de controle "
                                        + "em string"
                        );
                    }

                    texto.append(c);
                }
            }

            throw erro("String não terminada");
        }

        private Number lerNumero()
                throws IOException {

            int inicio = posicao;

            if (fonte.charAt(posicao) == '-') {
                posicao++;
            }

            if (posicao >= fonte.length()) {
                throw erro("Número incompleto");
            }

            if (fonte.charAt(posicao) == '0') {

                posicao++;

            } else {

                if (!digito(fonte.charAt(posicao))) {
                    throw erro("Número inválido");
                }

                while (posicao < fonte.length()
                        && digito(
                                fonte.charAt(posicao)
                        )) {

                    posicao++;
                }
            }

            if (posicao < fonte.length()
                    && fonte.charAt(posicao) == '.') {

                posicao++;

                if (posicao >= fonte.length()
                        || !digito(
                                fonte.charAt(posicao)
                        )) {

                    throw erro("Decimal inválido");
                }

                while (posicao < fonte.length()
                        && digito(
                                fonte.charAt(posicao)
                        )) {

                    posicao++;
                }
            }

            if (posicao < fonte.length()
                    && (fonte.charAt(posicao) == 'e'
                    || fonte.charAt(posicao) == 'E')) {

                posicao++;

                if (posicao < fonte.length()
                        && (fonte.charAt(posicao) == '+'
                        || fonte.charAt(posicao) == '-')) {

                    posicao++;
                }

                if (posicao >= fonte.length()
                        || !digito(
                                fonte.charAt(posicao)
                        )) {

                    throw erro("Expoente inválido");
                }

                while (posicao < fonte.length()
                        && digito(
                                fonte.charAt(posicao)
                        )) {

                    posicao++;
                }
            }

            try {

                return Double.valueOf(
                        fonte.substring(
                                inicio,
                                posicao
                        )
                );

            } catch (NumberFormatException e) {

                throw erro("Número inválido");
            }
        }

        private boolean digito(char c) {

            return c >= '0' && c <= '9';
        }

        private void literal(String esperado)
                throws IOException {

            if (!fonte.startsWith(
                    esperado,
                    posicao
            )) {

                throw erro("Literal inválido");
            }

            posicao += esperado.length();
        }

        private void pularEspacos() {

            while (posicao < fonte.length()
                    && (fonte.charAt(posicao) == ' '
                    || fonte.charAt(posicao) == '\n'
                    || fonte.charAt(posicao) == '\r'
                    || fonte.charAt(posicao) == '\t')) {

                posicao++;
            }
        }

        private boolean tentar(char esperado) {

            pularEspacos();

            if (posicao < fonte.length()
                    && fonte.charAt(posicao) == esperado) {

                posicao++;
                return true;
            }

            return false;
        }

        private void consumir(char esperado)
                throws IOException {

            if (!tentar(esperado)) {

                throw erro(
                        "Esperado: " + esperado
                );
            }
        }

        private IOException erro(String mensagem) {

            return new IOException(
                    mensagem
                            + " na posição "
                            + posicao
            );
        }
    }
}
