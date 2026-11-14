
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gerencia matches e conexões pela API do CineMatch.
 *
 * Não acessa o MySQL diretamente.
 * As informações ficam centralizadas no Railway.
 */
public class MatchDAO {

    private static final String URL_API =
            System.getenv()
                    .getOrDefault(
                            "CINEMATCH_API_URL",
                            "http://localhost:8081"
                    )
                    .replaceAll("/+$", "");

    private final HttpClient cliente =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

    // =========================================
    // CLASSE DE CONEXÃO
    // =========================================

    public static class Conexao {

        public final int id;
        public final int outroId;
        public final String nome;
        public final String status;
        public final boolean recebida;

        Conexao(
                int id,
                int outroId,
                String nome,
                String status,
                boolean recebida
        ) {
            this.id = id;
            this.outroId = outroId;
            this.nome = nome;
            this.status = status;
            this.recebida = recebida;
        }
    }

    // =========================================
    // BUSCAR MATCHES PELO FILME
    // =========================================

    public List<AlgoritmoMatch.Resultado> sugerir(
            int usuarioId,
            int filmeId
    ) {

        List<AlgoritmoMatch.Resultado> resultados =
                new ArrayList<>();

        if (!usuarioAutenticado(usuarioId)
                || filmeId <= 0) {
            return resultados;
        }

        try {

            HttpResponse<String> resposta = enviar(
                    "GET",
                    "/api/matches/filmes/" + filmeId,
                    null
            );

            if (resposta.statusCode() != 200) {

                throw new IllegalStateException(
                        "API de matches retornou HTTP "
                                + resposta.statusCode()
                );
            }

            for (Object item : listaJson(
                    resposta.body()
            )) {

                Map<String, Object> dados =
                        objeto(item);

                Map<String, Object> perfil =
                        objeto(dados.get("usuario"));

                int id = numero(
                        perfil.get("id")
                );

                String nome = texto(
                        perfil.get("nome")
                );

                int idade = numero(
                        perfil.get("idade")
                );

                if (id <= 0
                        || nome == null
                        || nome.isBlank()
                        || idade < 18) {

                    continue;
                }

                Usuario pessoa = new Usuario();

                pessoa.setId(id);
                pessoa.setNome(nome);

                pessoa.setBio(
                        texto(perfil.get("bio"))
                );

                pessoa.setFoto(
                        texto(perfil.get("foto"))
                );

                pessoa.setGenerosFavoritos(
                        texto(
                                perfil.get(
                                        "generosFavoritos"
                                )
                        )
                );

                pessoa.setPermitirSugestoes(true);

                /*
                 * A API envia apenas a idade,
                 * não a data real de nascimento.
                 *
                 * Criamos uma data aproximada
                 * somente para a interface
                 * conseguir exibir a idade.
                 *
                 * Essa data não deve ser salva
                 * como nascimento verdadeiro.
                 */
                pessoa.setNascimento(
                        LocalDate.now()
                                .minusYears(idade)
                );

                AlgoritmoMatch.Resultado resultado =
                        new AlgoritmoMatch.Resultado(
                                pessoa,
                                numero(
                                        dados.get(
                                                "percentual"
                                        )
                                ),
                                numero(
                                        dados.get(
                                                "filmesEmComum"
                                        )
                                ),
                                numero(
                                        dados.get(
                                                "generosEmComum"
                                        )
                                ),
                                texto(
                                        dados.get("motivo")
                                )
                        );

                resultados.add(resultado);
            }

            resultados.sort(
                    Comparator.comparingInt(
                            (AlgoritmoMatch.Resultado r)
                                    -> r.percentual
                    ).reversed()
            );

        } catch (IOException
                 | InterruptedException
                 | RuntimeException e) {

            registrarErro(
                    "buscar matches",
                    e
            );

            throw new IllegalStateException(
                    "Não foi possível consultar "
                            + "os matches na API.",
                    e
            );
        }

        return resultados;
    }

    // =========================================
    // SOLICITAR CONEXÃO
    // =========================================

    public boolean solicitar(
            int origem,
            int destino
    ) {

        if (!usuarioAutenticado(origem)
                || destino <= 0
                || origem == destino) {

            return false;
        }

        try {

            String json =
                    "{\"destinoId\":"
                            + destino
                            + "}";

            HttpResponse<String> resposta = enviar(
                    "POST",
                    "/api/matches/solicitar",
                    json
            );

            return sucesso(resposta, 201);

        } catch (IOException
                 | InterruptedException
                 | RuntimeException e) {

            registrarErro(
                    "solicitar conexão",
                    e
            );

            return false;
        }
    }

    // =========================================
    // VERIFICAR FILME EM COMUM
    // =========================================

    /**
     * Verifica se a outra pessoa aparece como
     * match em algum filme curtido.
     *
     * O servidor também aplica os filtros de
     * compatibilidade e privacidade.
     *
     * Portanto, este método não expõe curtidas
     * de perfis incompatíveis.
     */
    public boolean temFilmeComum(
            int a,
            int b
    ) {

        Usuario atual = Sessao.getUsuario();

        if (atual == null
                || a <= 0
                || b <= 0
                || a == b) {

            return false;
        }

        int meuId = atual.getId();

        if ((meuId != a && meuId != b)
                || !usuarioAutenticado(meuId)) {

            return false;
        }

        int outroId = meuId == a ? b : a;

        Set<Integer> curtidos =
                new PreferenciaFilmeDAO()
                        .idsCurtidos(meuId);

        try {

            for (int filmeId : curtidos) {

                List<AlgoritmoMatch.Resultado> sugestoes =
                        sugerir(
                                meuId,
                                filmeId
                        );

                for (AlgoritmoMatch.Resultado resultado
                        : sugestoes) {

                    if (resultado.usuario.getId()
                            == outroId) {

                        return true;
                    }
                }
            }

        } catch (IllegalStateException e) {

            System.err.println(
                    "MatchDAO: " + e.getMessage()
            );
        }

        return false;
    }

    // =========================================
    // CONSULTAR STATUS ENTRE CONTAS
    // =========================================

    public String statusEntre(
            int a,
            int b
    ) {

        Usuario atual = Sessao.getUsuario();

        if (atual == null
                || a <= 0
                || b <= 0
                || a == b) {

            return null;
        }

        int meuId = atual.getId();

        if ((meuId != a && meuId != b)
                || !usuarioAutenticado(meuId)) {

            return null;
        }

        int outroId = meuId == a ? b : a;

        try {

            HttpResponse<String> resposta = enviar(
                    "GET",
                    "/api/matches/status/" + outroId,
                    null
            );

            if (!sucesso(resposta, 200)) {
                return null;
            }

            Map<String, Object> dados =
                    objeto(
                            Json.ler(
                                    resposta.body()
                            )
                    );

            String status =
                    texto(dados.get("status"));

            String direcao =
                    texto(dados.get("direcao"));

            if (status == null
                    || status.isBlank()) {

                return null;
            }

            if (direcao == null) {
                return status;
            }

            // A resposta da API usa a perspectiva
            // da pessoa que está logada.
            if (meuId != a) {

                direcao = "enviada".equals(direcao)
                        ? "recebida"
                        : "enviada";
            }

            return status
                    + " ("
                    + direcao
                    + ")";

        } catch (IOException
                 | InterruptedException
                 | RuntimeException e) {

            registrarErro(
                    "consultar status",
                    e
            );

            return null;
        }
    }

    // =========================================
    // LISTAR CONEXÕES
    // =========================================

    public List<Conexao> listar(int uid) {

        List<Conexao> lista =
                new ArrayList<>();

        if (!usuarioAutenticado(uid)) {
            return lista;
        }

        try {

            HttpResponse<String> resposta = enviar(
                    "GET",
                    "/api/matches/conexoes",
                    null
            );

            if (!sucesso(resposta, 200)) {
                return lista;
            }

            for (Object item : listaJson(
                    resposta.body()
            )) {

                Map<String, Object> dados =
                        objeto(item);

                Conexao conexao = new Conexao(
                        numero(
                                dados.get("id")
                        ),
                        numero(
                                dados.get("outroId")
                        ),
                        texto(
                                dados.get("nome")
                        ),
                        texto(
                                dados.get("status")
                        ),
                        Boolean.TRUE.equals(
                                dados.get("recebida")
                        )
                );

                lista.add(conexao);
            }

        } catch (IOException
                 | InterruptedException
                 | RuntimeException e) {

            registrarErro(
                    "listar conexões",
                    e
            );
        }

        return lista;
    }

    // =========================================
    // ACEITAR OU RECUSAR CONEXÃO
    // =========================================

    public boolean responder(
            int conexaoId,
            int destinatarioId,
            boolean aceitar
    ) {

        if (conexaoId <= 0
                || !usuarioAutenticado(
                        destinatarioId
                )) {

            return false;
        }

        try {

            String json =
                    "{\"aceitar\":"
                            + aceitar
                            + "}";

            HttpResponse<String> resposta = enviar(
                    "POST",
                    "/api/matches/conexoes/"
                            + conexaoId
                            + "/responder",
                    json
            );

            return sucesso(resposta, 200);

        } catch (IOException
                 | InterruptedException
                 | RuntimeException e) {

            registrarErro(
                    "responder solicitação",
                    e
            );

            return false;
        }
    }

    // =========================================
    // VERIFICAR AUTENTICAÇÃO
    // =========================================

    private boolean usuarioAutenticado(int id) {

        Usuario usuario =
                Sessao.getUsuario();

        return id > 0
                && usuario != null
                && usuario.getId() == id
                && CineMatchApiCliente
                        .tokenPertenceAoUsuario(id);
    }

    // =========================================
    // ENVIAR REQUISIÇÃO PARA A API
    // =========================================

    private HttpResponse<String> enviar(
            String metodo,
            String caminho,
            String json
    ) throws IOException, InterruptedException {

        String token =
                CineMatchApiCliente.obterToken();

        if (token == null
                || token.isBlank()) {

            throw new IOException(
                    "Sem token de login. "
                            + "Entre novamente."
            );
        }

        HttpRequest.Builder builder =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        URL_API + caminho
                                )
                        )
                        .timeout(
                                Duration.ofSeconds(12)
                        )
                        .header(
                                "Authorization",
                                "Bearer " + token
                        );

        if ("POST".equals(metodo)) {

            builder.header(
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );

            builder.POST(
                    HttpRequest.BodyPublishers
                            .ofString(
                                    json,
                                    StandardCharsets.UTF_8
                            )
            );

        } else {

            builder.GET();
        }

        return cliente.send(
                builder.build(),
                HttpResponse.BodyHandlers
                        .ofString(
                                StandardCharsets.UTF_8
                        )
        );
    }

    // =========================================
    // VERIFICAR RESPOSTA HTTP
    // =========================================

    private boolean sucesso(
            HttpResponse<String> resposta,
            int esperado
    ) {

        if (resposta.statusCode() == esperado) {
            return true;
        }

        System.err.println(
                "MatchDAO: HTTP "
                        + resposta.statusCode()
                        + " ao acessar a API. Resposta: "
                        + resposta.body()
        );

        return false;
    }

    // =========================================
    // REGISTRAR ERROS
    // =========================================

    private void registrarErro(
            String operacao,
            Exception e
    ) {

        if (e instanceof InterruptedException) {

            Thread.currentThread()
                    .interrupt();
        }

        System.err.println(
                "MatchDAO: erro ao "
                        + operacao
                        + ": "
                        + e.getMessage()
        );
    }

    // =========================================
    // CONVERSÃO DE DADOS
    // =========================================

    private static String texto(Object valor) {

        return valor == null
                ? null
                : String.valueOf(valor);
    }

    private static int numero(Object valor) {

        return valor instanceof Number n
                ? n.intValue()
                : 0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objeto(
            Object valor
    ) {

        if (!(valor instanceof Map<?, ?> mapa)) {

            throw new IllegalArgumentException(
                    "Objeto JSON inválido."
            );
        }

        return (Map<String, Object>) mapa;
    }

    private static List<?> listaJson(
            String json
    ) {

        Object valor = Json.ler(json);

        if (!(valor instanceof List<?> lista)) {

            throw new IllegalArgumentException(
                    "Lista JSON inválida."
            );
        }

        return lista;
    }

    // =========================================
    // LEITOR JSON SEM BIBLIOTECAS EXTERNAS
    // =========================================

    /**
     * Interpreta as respostas JSON da API.
     *
     * Suporta:
     * - objetos
     * - listas
     * - strings
     * - números
     * - booleanos
     * - null
     *
     * Também suporta objetos aninhados,
     * necessários para os dados dos matches.
     */
    private static final class Json {

        private final String fonte;
        private int pos;

        private Json(String fonte) {
            this.fonte = fonte;
        }

        static Object ler(String fonte) {

            if (fonte == null) {

                throw new IllegalArgumentException(
                        "JSON vazio."
                );
            }

            Json p = new Json(fonte);

            Object valor = p.valor();

            p.espacos();

            if (p.pos != fonte.length()) {

                throw new IllegalArgumentException(
                        "JSON excedente."
                );
            }

            return valor;
        }

        private Object valor() {

            espacos();

            if (pos >= fonte.length()) {

                throw new IllegalArgumentException(
                        "JSON incompleto."
                );
            }

            char c = fonte.charAt(pos);

            if (c == '{') {
                return objeto();
            }

            if (c == '[') {
                return lista();
            }

            if (c == '"') {
                return string();
            }

            if (c == 't') {
                literal("true");
                return true;
            }

            if (c == 'f') {
                literal("false");
                return false;
            }

            if (c == 'n') {
                literal("null");
                return null;
            }

            return numero();
        }

        private Map<String, Object> objeto() {

            Map<String, Object> mapa =
                    new java.util.LinkedHashMap<>();

            exigir('{');
            espacos();

            if (consumir('}')) {
                return mapa;
            }

            do {

                espacos();

                String chave = string();

                exigir(':');

                mapa.put(
                        chave,
                        valor()
                );

                espacos();

                if (consumir('}')) {
                    return mapa;
                }

                exigir(',');

            } while (true);
        }

        private List<Object> lista() {

            List<Object> valores =
                    new ArrayList<>();

            exigir('[');
            espacos();

            if (consumir(']')) {
                return valores;
            }

            do {

                valores.add(
                        valor()
                );

                espacos();

                if (consumir(']')) {
                    return valores;
                }

                exigir(',');

            } while (true);
        }

        private String string() {

            espacos();
            exigir('"');

            StringBuilder saida =
                    new StringBuilder();

            while (pos < fonte.length()) {

                char c = fonte.charAt(pos++);

                if (c == '"') {
                    return saida.toString();
                }

                if (c != '\\') {

                    if (c < 32) {

                        throw new IllegalArgumentException(
                                "Controle em string JSON."
                        );
                    }

                    saida.append(c);
                    continue;
                }

                if (pos >= fonte.length()) {

                    throw new IllegalArgumentException(
                            "Escape incompleto."
                    );
                }

                char e = fonte.charAt(pos++);

                switch (e) {

                    case '"', '\\', '/' ->
                            saida.append(e);

                    case 'b' ->
                            saida.append('\b');

                    case 'f' ->
                            saida.append('\f');

                    case 'n' ->
                            saida.append('\n');

                    case 'r' ->
                            saida.append('\r');

                    case 't' ->
                            saida.append('\t');

                    case 'u' -> {

                        if (pos + 4 > fonte.length()) {

                            throw new IllegalArgumentException(
                                    "Unicode incompleto."
                            );
                        }

                        saida.append(
                                (char) Integer.parseInt(
                                        fonte.substring(
                                                pos,
                                                pos + 4
                                        ),
                                        16
                                )
                        );

                        pos += 4;
                    }

                    default ->
                            throw new IllegalArgumentException(
                                    "Escape JSON inválido."
                            );
                }
            }

            throw new IllegalArgumentException(
                    "String JSON não fechada."
            );
        }

        private Number numero() {

            int inicio = pos;

            consumir('-');

            if (!consumir('0')) {
                digitos();
            }

            boolean decimal = false;

            if (consumir('.')) {

                decimal = true;
                digitos();
            }

            if (consumir('e') || consumir('E')) {

                decimal = true;

                if (!consumir('+')) {
                    consumir('-');
                }

                digitos();
            }

            String valor = fonte.substring(
                    inicio,
                    pos
            );

            try {

                if (decimal) {
                    return Double.valueOf(valor);
                }

                return Long.valueOf(valor);

            } catch (NumberFormatException e) {

                throw new IllegalArgumentException(
                        "Número JSON inválido.",
                        e
                );
            }
        }

        private void digitos() {

            int inicio = pos;

            while (pos < fonte.length()
                    && fonte.charAt(pos) >= '0'
                    && fonte.charAt(pos) <= '9') {

                pos++;
            }

            if (pos == inicio) {

                throw new IllegalArgumentException(
                        "Dígitos esperados."
                );
            }
        }

        private void literal(String esperado) {

            if (!fonte.startsWith(
                    esperado,
                    pos
            )) {

                throw new IllegalArgumentException(
                        "Literal JSON inválido."
                );
            }

            pos += esperado.length();
        }

        private void exigir(char c) {

            espacos();

            if (!consumir(c)) {

                throw new IllegalArgumentException(
                        "Esperado: " + c
                );
            }
        }

        private boolean consumir(char c) {

            if (pos < fonte.length()
                    && fonte.charAt(pos) == c) {

                pos++;
                return true;
            }

            return false;
        }

        private void espacos() {

            while (pos < fonte.length()
                    && Character.isWhitespace(
                            fonte.charAt(pos)
                    )) {

                pos++;
            }
        }
    }
}
