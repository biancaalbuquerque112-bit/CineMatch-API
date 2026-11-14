
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class TesteDriverMySQL {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/cinematch";
        String usuario = "cinematch_app";
        String senha = System.getenv("CINEMATCH_DB_PASSWORD");

        if (senha == null || senha.isEmpty()) {
            System.out.println("Variavel CINEMATCH_DB_PASSWORD nao configurada.");
            return;
        }

        try (Connection conexao =
                DriverManager.getConnection(url, usuario, senha)) {

            System.out.println("CineMatch conectado ao MySQL com sucesso!");

        } catch (SQLException e) {
            System.out.println("Erro ao conectar:");
            System.out.println(e.getMessage());
        }
    }
}
