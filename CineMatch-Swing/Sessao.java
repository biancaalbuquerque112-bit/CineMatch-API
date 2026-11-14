
public final class Sessao {

    private static Usuario usuario;

    private Sessao() {
    }

    public static Usuario getUsuario() {
        return usuario;
    }

    public static void entrar(Usuario novo) {

        if (novo == null || novo.getId() <= 0) {
            sair();
            return;
        }

        usuario = novo;
    }

    public static void sair() {

        // Remove o usuário da sessão.
        usuario = null;

        // Remove o token de autenticação.
        CineMatchApiCliente.limparToken();
    }

    public static boolean ativa() {

        return usuario != null
                && usuario.getId() > 0;
    }

    public static boolean adulta() {

        return ativa()
                && usuario.maiorDeIdade();
    }
}
