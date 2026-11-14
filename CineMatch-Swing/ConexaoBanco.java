
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConexaoBanco {

    private static final String URL =
        "jdbc:mysql://localhost:3306/cinematch";

    private static final String USUARIO = "cinematch_app";

    private static final String SENHA =
        System.getenv("CINEMATCH_DB_PASSWORD");

    public static Connection getConexao() throws SQLException {
        if (SENHA == null || SENHA.isEmpty()) {
            throw new SQLException(
                "Variavel CINEMATCH_DB_PASSWORD nao configurada."
            );
        }

        return DriverManager.getConnection(URL, USUARIO, SENHA);
    }

    public static void inicializarBanco() {
        try (Connection conexao = getConexao()) {
            System.out.println("CineMatch conectado ao MySQL!");
        } catch (SQLException e) {
            throw new IllegalStateException(
                "Erro ao conectar ao MySQL: " + e.getMessage(), e
            );
        }

        new FilmeDAO().sincronizarCatalogo();
    }
}
