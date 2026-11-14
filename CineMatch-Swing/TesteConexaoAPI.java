
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TesteConexaoAPI {

    public static void main(String[] args) {
        try {
            HttpClient cliente = HttpClient.newHttpClient();

            HttpRequest requisicao = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8081/api/usuarios"))
                .GET()
                .build();

            HttpResponse<String> resposta = cliente.send(
                requisicao,
                HttpResponse.BodyHandlers.ofString()
            );

            System.out.println("Status HTTP: " + resposta.statusCode());
            System.out.println("Resposta da API:");
            System.out.println(resposta.body());

        } catch (Exception e) {
            System.out.println("Erro ao acessar a API:");
            e.printStackTrace();
        }
    }
}
