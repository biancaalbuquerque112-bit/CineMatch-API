import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Comentarios do CineMatch armazenados na API Railway, sem MySQL local. */
public class ComentarioDAO {

    private static final String URL_API = CineMatchApiCliente.obterUrlApi();

    private final HttpClient cliente = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // O construtor nao abre conexao com banco de dados.
    public ComentarioDAO() {
    }

    /** Publica comentario ou resposta. A API identifica o autor pelo token. */
    public boolean inserir(Comentario comentario) {
        if (comentario == null || comentario.getFilmeId() <= 0
                || comentario.getRespostaA() < 0
                || comentario.getTexto() == null
                || comentario.getTexto().isBlank()) {
            return false;
        }

        String corpo = "{\"filmeId\":" + comentario.getFilmeId()
                + ",\"respostaA\":" + comentario.getRespostaA()
                + ",\"tituloFilme\":\"" + escapar(comentario.getTituloFilme()) + "\""
                + ",\"texto\":\"" + escapar(comentario.getTexto()) + "\"}";

        try {
            HttpResponse<String> resposta = requisitar("POST", "/api/comentarios", corpo);
            if (resposta.statusCode() == 201) {
                return true;
            }
            informarFalha("publicar comentario", resposta);
        } catch (IOException e) {
            informarExcecao("publicar comentario", e);
        }
        return false;
    }

    /** Lista comentarios e respostas de um filme. */
    public List<Comentario> listarPorFilme(int filmeId) {
        List<Comentario> lista = new ArrayList<>();
        if (filmeId <= 0) {
            return lista;
        }

        try {
            HttpResponse<String> resposta = requisitar(
                    "GET", "/api/comentarios/filmes/" + filmeId, null);

            if (resposta.statusCode() != 200) {
                informarFalha("listar comentarios", resposta);
                return lista;
            }

            Object raiz = new LeitorJson(resposta.body()).lerTudo();
            if (!(raiz instanceof List<?> registros)) {
                throw new IOException("A API nao retornou uma lista de comentarios.");
            }

            for (Object registro : registros) {
                if (!(registro instanceof Map<?, ?> dados)) {
                    throw new IOException("Comentario invalido recebido da API.");
                }

                Comentario comentario = new Comentario(
                        numero(dados, "id"),
                        numero(dados, "respostaA"),
                        numero(dados, "curtidas"),
                        texto(dados, "tituloFilme"),
                        texto(dados, "autor"),
                        texto(dados, "texto")
                );
                comentario.setFilmeId(numero(dados, "filmeId"));
                comentario.setAutorId(numero(dados, "autorId"));
                lista.add(comentario);
            }
        } catch (IOException e) {
            informarExcecao("listar comentarios", e);
        }
        return lista;
    }

    /** Cada conta pode curtir um comentario apenas uma vez. */
    public boolean curtir(int comentarioId, int usuarioId) {
        if (comentarioId <= 0 || !usuarioAtual(usuarioId)) {
            return false;
        }

        try {
            HttpResponse<String> resposta = requisitar(
                    "POST", "/api/comentarios/" + comentarioId + "/curtir", "{}");
            if (resposta.statusCode() == 200) {
                return true;
            }
            informarFalha("curtir comentario", resposta);
        } catch (IOException e) {
            informarExcecao("curtir comentario", e);
        }
        return false;
    }

    /** Compatibilidade com telas que usam curtir(id). */
    public void curtir(int comentarioId) {
        Usuario usuario = Sessao.getUsuario();
        if (usuario != null) {
            curtir(comentarioId, usuario.getId());
        }
    }

    /** Apaga os comentarios da conta atual no filme e suas respostas. */
    public boolean limparMeusPorFilme(int filmeId, int usuarioId) {
        if (filmeId <= 0 || !usuarioAtual(usuarioId)) {
            return false;
        }

        try {
            HttpResponse<String> resposta = requisitar(
                    "DELETE", "/api/comentarios/filmes/" + filmeId + "/meus", null);
            if (resposta.statusCode() == 200) {
                return true;
            }
            informarFalha("apagar meus comentarios", resposta);
        } catch (IOException e) {
            informarExcecao("apagar meus comentarios", e);
        }
        return false;
    }

    /** Operacao administrativa legada: nao existe endpoint equivalente na API. */
    public void limparPorFilme(int filmeId) {
        System.err.println("ComentarioDAO: limparPorFilme nao executado. "
                + "A API so permite apagar os comentarios da conta atual.");
    }

    private boolean usuarioAtual(int usuarioId) {
        Usuario usuario = Sessao.getUsuario();
        return usuarioId > 0 && usuario != null && usuario.getId() == usuarioId;
    }

    private HttpResponse<String> requisitar(String metodo, String caminho, String corpo)
            throws IOException {
        String token = CineMatchApiCliente.obterToken();
        if (token == null || token.isBlank()) {
            throw new IOException("Sessao nao iniciada ou expirada. Faca login novamente.");
        }

        HttpRequest.Builder construtor = HttpRequest.newBuilder()
                .uri(URI.create(URL_API + caminho))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token);

        switch (metodo) {
            case "GET" -> construtor.GET();
            case "DELETE" -> construtor.DELETE();
            case "POST" -> construtor
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            corpo == null ? "{}" : corpo, StandardCharsets.UTF_8));
            default -> throw new IOException("Metodo HTTP nao suportado: " + metodo);
        }

        try {
            return cliente.send(construtor.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Requisicao interrompida.", e);
        }
    }

    private void informarFalha(String operacao, HttpResponse<String> resposta) {
        System.err.println("ComentarioDAO: falha ao " + operacao
                + " (HTTP " + resposta.statusCode() + "): " + resposta.body());
    }

    private void informarExcecao(String operacao, IOException e) {
        System.err.println("ComentarioDAO: erro ao " + operacao + ": " + e.getMessage());
    }

    private static String texto(Map<?, ?> dados, String chave) {
        Object valor = dados.get(chave);
        return valor == null ? "" : valor.toString();
    }

    private static int numero(Map<?, ?> dados, String chave) throws IOException {
        Object valor = dados.get(chave);
        if (valor == null) {
            return 0;
        }
        if (valor instanceof Number numero) {
            return numero.intValue();
        }
        throw new IOException("Campo numerico invalido: " + chave);
    }

    /** Escapa strings corretamente para JSON, incluindo quebras de linha. */
    private static String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        StringBuilder saida = new StringBuilder();
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"' -> saida.append("\\\"");
                case '\\' -> saida.append("\\\\");
                case '\n' -> saida.append("\\n");
                case '\r' -> saida.append("\\r");
                case '\t' -> saida.append("\\t");
                case '\b' -> saida.append("\\b");
                case '\f' -> saida.append("\\f");
                default -> {
                    if (c < 32) {
                        saida.append(String.format("\\u%04x", (int) c));
                    } else {
                        saida.append(c);
                    }
                }
            }
        }
        return saida.toString();
    }

    /** Leitor JSON pequeno, sem dependencias extras. */
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
                throw erro("Texto depois do JSON");
            }
            return valor;
        }

        private Object lerValor() throws IOException {
            pularEspacos();
            if (posicao >= fonte.length()) {
                throw erro("JSON incompleto");
            }
            char c = fonte.charAt(posicao);
            return switch (c) {
                case '{' -> lerObjeto();
                case '[' -> lerLista();
                case '"' -> lerString();
                case 't' -> { literal("true"); yield Boolean.TRUE; }
                case 'f' -> { literal("false"); yield Boolean.FALSE; }
                case 'n' -> { literal("null"); yield null; }
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield lerNumero();
                    }
                    throw erro("Valor JSON invalido");
                }
            };
        }

        private Map<String, Object> lerObjeto() throws IOException {
            consumir('{');
            Map<String, Object> mapa = new LinkedHashMap<>();
            if (tentar('}')) {
                return mapa;
            }
            do {
                pularEspacos();
                if (posicao >= fonte.length() || fonte.charAt(posicao) != '"') {
                    throw erro("Chave JSON invalida");
                }
                String chave = lerString();
                consumir(':');
                mapa.put(chave, lerValor());
                if (tentar('}')) {
                    return mapa;
                }
                consumir(',');
            } while (true);
        }

        private List<Object> lerLista() throws IOException {
            consumir('[');
            List<Object> lista = new ArrayList<>();
            if (tentar(']')) {
                return lista;
            }
            do {
                lista.add(lerValor());
                if (tentar(']')) {
                    return lista;
                }
                consumir(',');
            } while (true);
        }

        private String lerString() throws IOException {
            consumir('"');
            StringBuilder resultado = new StringBuilder();
            while (posicao < fonte.length()) {
                char c = fonte.charAt(posicao++);
                if (c == '"') {
                    return resultado.toString();
                }
                if (c != '\\') {
                    if (c < 32) {
                        throw erro("Caractere de controle em string");
                    }
                    resultado.append(c);
                    continue;
                }
                if (posicao >= fonte.length()) {
                    throw erro("Escape incompleto");
                }
                char escape = fonte.charAt(posicao++);
                switch (escape) {
                    case '"' -> resultado.append('"');
                    case '\\' -> resultado.append('\\');
                    case '/' -> resultado.append('/');
                    case 'b' -> resultado.append('\b');
                    case 'f' -> resultado.append('\f');
                    case 'n' -> resultado.append('\n');
                    case 'r' -> resultado.append('\r');
                    case 't' -> resultado.append('\t');
                    case 'u' -> {
                        if (posicao + 4 > fonte.length()) {
                            throw erro("Unicode incompleto");
                        }
                        try {
                            resultado.append((char) Integer.parseInt(
                                    fonte.substring(posicao, posicao + 4), 16));
                        } catch (NumberFormatException e) {
                            throw erro("Unicode invalido");
                        }
                        posicao += 4;
                    }
                    default -> throw erro("Escape JSON invalido");
                }
            }
            throw erro("String nao terminada");
        }

        private Number lerNumero() throws IOException {
            int inicio = posicao;
            if (fonte.charAt(posicao) == '-') {
                posicao++;
            }
            if (posicao >= fonte.length()) {
                throw erro("Numero incompleto");
            }
            if (fonte.charAt(posicao) == '0') {
                posicao++;
            } else {
                if (!digito(fonte.charAt(posicao))) {
                    throw erro("Numero invalido");
                }
                while (posicao < fonte.length() && digito(fonte.charAt(posicao))) {
                    posicao++;
                }
            }
            if (posicao < fonte.length() && fonte.charAt(posicao) == '.') {
                posicao++;
                if (posicao >= fonte.length() || !digito(fonte.charAt(posicao))) {
                    throw erro("Decimal invalido");
                }
                while (posicao < fonte.length() && digito(fonte.charAt(posicao))) {
                    posicao++;
                }
            }
            if (posicao < fonte.length()
                    && (fonte.charAt(posicao) == 'e' || fonte.charAt(posicao) == 'E')) {
                posicao++;
                if (posicao < fonte.length()
                        && (fonte.charAt(posicao) == '+' || fonte.charAt(posicao) == '-')) {
                    posicao++;
                }
                if (posicao >= fonte.length() || !digito(fonte.charAt(posicao))) {
                    throw erro("Expoente invalido");
                }
                while (posicao < fonte.length() && digito(fonte.charAt(posicao))) {
                    posicao++;
                }
            }
            try {
                String trecho = fonte.substring(inicio, posicao);
                if (trecho.contains(".") || trecho.contains("e") || trecho.contains("E")) {
                    return Double.parseDouble(trecho);
                }
                return Long.parseLong(trecho);
            } catch (NumberFormatException e) {
                throw erro("Numero invalido");
            }
        }

        private boolean digito(char c) {
            return c >= '0' && c <= '9';
        }

        private void literal(String esperado) throws IOException {
            if (!fonte.startsWith(esperado, posicao)) {
                throw erro("Literal JSON invalido");
            }
            posicao += esperado.length();
        }

        private void pularEspacos() {
            while (posicao < fonte.length()) {
                char c = fonte.charAt(posicao);
                if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                    break;
                }
                posicao++;
            }
        }

        private boolean tentar(char esperado) {
            pularEspacos();
            if (posicao < fonte.length() && fonte.charAt(posicao) == esperado) {
                posicao++;
                return true;
            }
            return false;
        }

        private void consumir(char esperado) throws IOException {
            if (!tentar(esperado)) {
                throw erro("Esperado: " + esperado);
            }
        }

        private IOException erro(String mensagem) {
            return new IOException(mensagem + " na posicao " + posicao);
        }
    }
}
