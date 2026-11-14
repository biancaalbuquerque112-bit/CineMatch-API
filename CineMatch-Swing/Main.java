import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class Main {

    public static void main(String[] args) {

        // A tela de login utiliza a API Spring Boot.
        // Não é necessário conectar diretamente ao MySQL
        // para iniciar o aplicativo.

        SwingUtilities.invokeLater(() -> {

            try {
                new TelaLogin();

            } catch (Exception erro) {

                JOptionPane.showMessageDialog(
                    null,
                    "Não foi possível iniciar o CineMatch.\n\n"
                            + erro.getMessage(),
                    "Erro ao iniciar",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }
}
