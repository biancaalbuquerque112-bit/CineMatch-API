
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Mensagens enviadas e consultadas pela API do CineMatch no Railway. */
public class MensagemDAO {

 private static final String URL_API =
        CineMatchApiCliente.obterUrlApi();

    private final HttpClient cliente = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private String ultimoErro = "";

    public static class Mensagem {
        public final int id;
        public final int remetenteId;
        public final String texto;
        public final Timestamp enviadaEm;

        public Mensagem(int id, int remetenteId, String texto,
                        Timestamp enviadaEm) {
            this.id = id;
            this.remetenteId = remetenteId;
            this.texto = texto;
            this.enviadaEm = enviadaEm;
        }
    }

    public String getUltimoErro() {
        return ultimoErro;
    }

    public boolean enviar(int remetente, int destinatario, String texto) {
        ultimoErro = "";

        if (remetente <= 0 || destinatario <= 0 || remetente == destinatario
                || texto == null || texto.trim().isEmpty()
                || texto.trim().length() > 2000) {
            registrarErro("Destinatário ou mensagem inválida.");
            return false;
        }

        if (!sessaoValida(remetente)) {
            return false;
        }

        String corpo = "{\"destinatarioId\":" + destinatario
                + ",\"texto\":\"" + escaparJson(texto.trim()) + "\"}";

        try {
            HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(URL_API + "/api/mensagens/enviar"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + CineMatchApiCliente.obterToken())
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            corpo, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resposta = executar(requisicao);
            if (resposta.statusCode() == 200) {
                return true;
            }

            registrarErro(descreverHttp(resposta.statusCode(), resposta.body()));
            return false;

        } catch (IOException | IllegalArgumentException e) {
            registrarErro("Falha ao enviar mensagem: " + e.getMessage());
            return false;
        }
    }

    public List<Mensagem> listar(int usuario, int outro) {
        ultimoErro = "";
        List<Mensagem> mensagens = new ArrayList<>();

        if (usuario <= 0 || outro <= 0 || usuario == outro) {
            registrarErro("Usuários da conversa inválidos.");
            return mensagens;
        }

        if (!sessaoValida(usuario)) {
            return mensagens;
        }

        try {
            HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(URL_API + "/api/mensagens/conversa/" + outro))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + CineMatchApiCliente.obterToken())
                    .GET()
                    .build();

            HttpResponse<String> resposta = executar(requisicao);
            if (resposta.statusCode() != 200) {
                registrarErro(descreverHttp(resposta.statusCode(), resposta.body()));
                return mensagens;
            }

            return new LeitorJson(resposta.body()).lerMensagens();

        } catch (IOException | IllegalArgumentException e) {
            registrarErro("Falha ao buscar mensagens: " + e.getMessage());
            return mensagens;
        }
    }

    private boolean sessaoValida(int usuarioId) {
        Usuario usuario = Sessao.getUsuario();
        if (usuario == null || usuario.getId() != usuarioId
                || !CineMatchApiCliente.tokenPertenceAoUsuario(usuarioId)) {
            registrarErro("Sessão inválida. Feche a conversa e entre novamente.");
            return false;
        }
        return true;
    }

    private HttpResponse<String> executar(HttpRequest requisicao)
            throws IOException {
        try {
            return cliente.send(requisicao,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Requisição interrompida.", e);
        }
    }

    private String descreverHttp(int codigo, String corpo) {
        if (codigo == 401) {
            return "Sessão expirada. Faça login novamente.";
        }
        if (codigo == 403) {
            return "Conversa não autorizada. Verifique se a conexão foi aceita.";
        }
        if (codigo == 404) {
            return "Rota de mensagens não encontrada na API.";
        }
        if (codigo == 400) {
            return "Mensagem ou destinatário inválido.";
        }
        return "Erro da API de mensagens (HTTP " + codigo + "). "
                + (corpo == null ? "" : corpo);
    }

    private void registrarErro(String erro) {
        ultimoErro = erro;
        System.err.println("CineMatch - Mensagens: " + erro);
    }

    private static String escaparJson(String texto) {
        StringBuilder saida = new StringBuilder();
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '"' -> saida.append("\\\"");
                case '\\' -> saida.append("\\\\");
                case '\n' -> saida.append("\\n");
                case '\r' -> saida.append("\\r");
                case '\t' -> saida.append("\\t");
                case '\b' -> saida.append("\\b");
                case '\f' -> saida.append("\\f");
                default -> {
                    if (c < 0x20) {
                        saida.append(String.format("\\u%04x", (int) c));
                    } else {
                        saida.append(c);
                    }
                }
            }
        }
        return saida.toString();
    }

    /** Lê o JSON retornado pela API de mensagens. */
    private static final class LeitorJson {
        private final String fonte;
        private int pos;

        LeitorJson(String fonte) {
            this.fonte = fonte == null ? "" : fonte;
        }

        List<Mensagem> lerMensagens() throws IOException {
            List<Mensagem> lista = new ArrayList<>();
            exigir('[');

            if (!consumir(']')) {
                do {
                    Map<String, String> dados = lerObjeto();
                    try {
                        int id = Integer.parseInt(dados.get("id"));
                        int remetenteId = Integer.parseInt(dados.get("remetenteId"));
                        String texto = dados.get("texto");
                        String data = dados.get("enviadaEm");

                        if (texto == null || data == null || data.isBlank()) {
                            throw new IllegalArgumentException(
                                    "Campo obrigatório ausente.");
                        }

                        lista.add(new Mensagem(
                                id,
                                remetenteId,
                                texto,
                                Timestamp.valueOf(data)
                        ));
                    } catch (IllegalArgumentException e) {
                        throw new IOException(
                                "Mensagem inválida na resposta da API.", e);
                    }
                } while (consumir(','));

                exigir(']');
            }

            pularEspacos();
            if (pos != fonte.length()) {
                throw new IOException(
                        "Conteúdo inesperado na resposta da API.");
            }

            return lista;
        }

        private Map<String, String> lerObjeto() throws IOException {
            Map<String, String> dados = new HashMap<>();
            exigir('{');

            if (!consumir('}')) {
                do {
                    String chave = lerString();
                    exigir(':');
                    dados.put(chave, lerValor());
                } while (consumir(','));

                exigir('}');
            }

            return dados;
        }

        private String lerValor() throws IOException {
            pularEspacos();

            if (pos >= fonte.length()) {
                throw new IOException("JSON incompleto.");
            }

            if (fonte.charAt(pos) == '"') {
                return lerString();
            }

            int inicio = pos;

            while (pos < fonte.length()) {
                char c = fonte.charAt(pos);

                if (c == ',' || c == '}'
                        || Character.isWhitespace(c)) {
                    break;
                }

                pos++;
            }

            if (pos == inicio) {
                throw new IOException("Valor JSON inválido.");
            }

            String valor = fonte.substring(inicio, pos);
            return "null".equals(valor) ? null : valor;
        }

        private String lerString() throws IOException {
            exigir('"');

            StringBuilder texto = new StringBuilder();

            while (pos < fonte.length()) {
                char c = fonte.charAt(pos++);

                if (c == '"') {
                    return texto.toString();
                }

                if (c != '\\') {
                    if (c < 0x20) {
                        throw new IOException(
                                "Caractere inválido no JSON.");
                    }

                    texto.append(c);
                    continue;
                }

                if (pos >= fonte.length()) {
                    throw new IOException(
                            "Escape JSON incompleto.");
                }

                char escape = fonte.charAt(pos++);

                switch (escape) {
                    case '"' -> texto.append('"');
                    case '\\' -> texto.append('\\');
                    case '/' -> texto.append('/');
                    case 'n' -> texto.append('\n');
                    case 'r' -> texto.append('\r');
                    case 't' -> texto.append('\t');
                    case 'b' -> texto.append('\b');
                    case 'f' -> texto.append('\f');

                    case 'u' -> {
                        if (pos + 4 > fonte.length()) {
                            throw new IOException(
                                    "Escape Unicode incompleto.");
                        }

                        try {
                            int unicode = Integer.parseInt(
                                    fonte.substring(pos, pos + 4), 16);

                            texto.append((char) unicode);
                        } catch (NumberFormatException e) {
                            throw new IOException(
                                    "Escape Unicode inválido.", e);
                        }

                        pos += 4;
                    }

                    default -> throw new IOException(
                            "Escape JSON inválido.");
                }
            }

            throw new IOException(
                    "String JSON sem fechamento.");
        }

        private boolean consumir(char esperado) {
            pularEspacos();

            if (pos < fonte.length()
                    && fonte.charAt(pos) == esperado) {
                pos++;
                return true;
            }

            return false;
        }

        private void exigir(char esperado) throws IOException {
            if (!consumir(esperado)) {
                throw new IOException(
                        "JSON inválido: esperado '" + esperado + "'.");
            }
        }

        private void pularEspacos() {
            while (pos < fonte.length()
                    && Character.isWhitespace(fonte.charAt(pos))) {
                pos++;
            }
        }
    }
}
