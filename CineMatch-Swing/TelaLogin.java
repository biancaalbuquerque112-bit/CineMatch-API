
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;

import javax.swing.*;
import javax.swing.border.AbstractBorder;

public class TelaLogin extends JFrame {

    private final Color FUNDO = new Color(43, 22, 70);
    private final Color CORAL = new Color(255, 98, 92);
    private final Color ROXO_BOTAO = new Color(65, 37, 93);
    private final Color BRANCO = new Color(247, 243, 250);

    private final Font FONTE_LOGO =
            new Font("Nimbus Sans", Font.BOLD, 38);
    private final Font FONTE_TEXTO =
            new Font("Modern No. 20", Font.PLAIN, 18);
    private final Font FONTE_LABEL =
            new Font("Modern No. 20", Font.BOLD, 18);
    private final Font FONTE_BOTAO =
            new Font("Modern No. 20", Font.BOLD, 20);
    private final Font FONTE_CAMPO =
            new Font("Modern No. 20", Font.PLAIN, 20);
    private final Font FONTE_TITULO =
            new Font("Modern No. 20", Font.BOLD, 30);

    private JTextField campoEmail;
    private JPasswordField campoSenha;

    private final CineMatchApiCliente apiCliente;

    public TelaLogin() {

        // Login e cadastro utilizam a API.
        // Não precisamos mais criar UsuarioDAO nesta tela.
        apiCliente = new CineMatchApiCliente();

        setTitle("CineMatch - Login");
        setSize(500, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel painel = new JPanel();
        painel.setBackground(FUNDO);
        painel.setLayout(
                new BoxLayout(painel, BoxLayout.Y_AXIS)
        );
        painel.setBorder(
                BorderFactory.createEmptyBorder(35, 65, 35, 65)
        );

        JLabel icone = new JLabel();

        ImageIcon imagemIcone = new ImageIcon(
                "imagens/icone_cinematch.png"
        );

        if (imagemIcone.getIconWidth() > 0) {
            Image imagemAjustada = imagemIcone.getImage()
                    .getScaledInstance(
                            86, 61, Image.SCALE_SMOOTH
                    );
            icone.setIcon(new ImageIcon(imagemAjustada));
        }

        JLabel nomeAplicativo = new JLabel("CineMatch");
        nomeAplicativo.setFont(FONTE_LOGO);
        nomeAplicativo.setForeground(BRANCO);

        JPanel cabecalho = new JPanel(
                new FlowLayout(FlowLayout.CENTER, 10, 0)
        );
        cabecalho.setOpaque(false);
        cabecalho.add(icone);
        cabecalho.add(nomeAplicativo);
        cabecalho.setAlignmentX(Component.CENTER_ALIGNMENT);
        cabecalho.setMaximumSize(new Dimension(370, 68));

        JLabel subtitulo = new JLabel(
                "Entre no universo do cinema"
        );
        subtitulo.setFont(FONTE_TEXTO);
        subtitulo.setForeground(BRANCO);
        subtitulo.setAlignmentX(Component.CENTER_ALIGNMENT);

        campoEmail = new JTextField();
        campoSenha = new JPasswordField();

        configurarCampo(campoEmail);
        configurarCampo(campoSenha);

        JCheckBox mostrarSenha = new JCheckBox("Mostrar senha");
        mostrarSenha.setFont(FONTE_TEXTO);
        mostrarSenha.setForeground(BRANCO);
        mostrarSenha.setBackground(FUNDO);
        mostrarSenha.setFocusPainted(false);
        mostrarSenha.setAlignmentX(Component.CENTER_ALIGNMENT);

        char caractereOriginal = campoSenha.getEchoChar();

        mostrarSenha.addActionListener(e -> {
            if (mostrarSenha.isSelected()) {
                campoSenha.setEchoChar((char) 0);
                mostrarSenha.setText("Ocultar senha");
            } else {
                campoSenha.setEchoChar(caractereOriginal);
                mostrarSenha.setText("Mostrar senha");
            }
        });

        JButton entrar = criarBotao("Entrar", CORAL);
        JButton cadastrar = criarBotao(
                "Criar Conta", ROXO_BOTAO
        );

        painel.add(cabecalho);
        painel.add(Box.createVerticalStrut(10));
        painel.add(subtitulo);
        painel.add(Box.createVerticalStrut(35));

        painel.add(criarLabel("E-mail"));
        painel.add(Box.createVerticalStrut(8));
        painel.add(campoEmail);
        painel.add(Box.createVerticalStrut(15));

        painel.add(criarLabel("Senha"));
        painel.add(Box.createVerticalStrut(8));
        painel.add(campoSenha);
        painel.add(Box.createVerticalStrut(8));

        painel.add(mostrarSenha);
        painel.add(Box.createVerticalStrut(20));

        painel.add(entrar);
        painel.add(Box.createVerticalStrut(10));
        painel.add(cadastrar);

        entrar.addActionListener(e -> fazerLogin());
        cadastrar.addActionListener(e -> criarConta());

        add(painel);
        setVisible(true);
    }

    private JLabel criarLabel(String texto) {
        JLabel label = new JLabel(texto);
        label.setFont(FONTE_LABEL);
        label.setForeground(BRANCO);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }

    private void configurarCampo(JTextField campo) {
        campo.setMaximumSize(new Dimension(350, 40));
        campo.setFont(FONTE_CAMPO);
        campo.setAlignmentX(Component.CENTER_ALIGNMENT);
        campo.setBorder(new BordaArredondada());
        campo.setBackground(Color.WHITE);
        campo.setForeground(new Color(35, 18, 60));
    }

    private JButton criarBotao(String texto, Color cor) {

        JButton botao = new JButton(texto) {

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D desenho = (Graphics2D) g.create();

                desenho.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );

                desenho.setColor(getBackground());
                desenho.fillRoundRect(
                        0, 0, getWidth(), getHeight(), 18, 18
                );

                desenho.dispose();
                super.paintComponent(g);
            }
        };

        botao.setContentAreaFilled(false);
        botao.setBorderPainted(false);
        botao.setOpaque(false);
        botao.setBackground(cor);
        botao.setForeground(Color.WHITE);
        botao.setFont(FONTE_BOTAO);
        botao.setFocusPainted(false);
        botao.setAlignmentX(Component.CENTER_ALIGNMENT);
        botao.setMaximumSize(new Dimension(350, 45));
        botao.setCursor(new Cursor(Cursor.HAND_CURSOR));

        Color corHover = cor.equals(CORAL)
                ? new Color(255, 124, 116)
                : new Color(82, 52, 111);

        botao.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseEntered(MouseEvent e) {
                botao.setBackground(corHover);
                botao.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                botao.setBackground(cor);
                botao.repaint();
            }
        });

        return botao;
    }

    private static class BordaArredondada
            extends AbstractBorder {

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(8, 12, 8, 12);
        }

        @Override
        public void paintBorder(
                Component c,
                Graphics g,
                int x,
                int y,
                int width,
                int height
        ) {
            Graphics2D desenho = (Graphics2D) g.create();

            desenho.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            desenho.setColor(new Color(190, 168, 210));
            desenho.drawRoundRect(
                    x, y, width - 1, height - 1, 14, 14
            );

            desenho.dispose();
        }
    }

    // LOGIN E CARREGAMENTO DO PERFIL PELA API
    private void fazerLogin() {

        String email = campoEmail.getText().trim();

        char[] caracteres = campoSenha.getPassword();
        String senha = new String(caracteres);
        Arrays.fill(caracteres, '\0');

        if (email.isEmpty() || senha.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Por favor, preencha o e-mail e a senha."
            );
            return;
        }

        campoEmail.setEnabled(false);
        campoSenha.setEnabled(false);

        SwingWorker<Usuario, Void> tarefa =
                new SwingWorker<Usuario, Void>() {

            @Override
            protected Usuario doInBackground()
                    throws Exception {

                // A API valida a senha e devolve o perfil.
                // Não há mais consulta ao UsuarioDAO.
                return apiCliente.autenticarUsuario(
                        email, senha
                );
            }

            @Override
            protected void done() {

                try {
                    Usuario usuario = get();

                    Sessao.entrar(usuario);

                    new TelaInicial(usuario.getNome());

                    dispose();

                } catch (Exception ex) {

                    Throwable causa = ex.getCause() != null
                            ? ex.getCause()
                            : ex;

                    JOptionPane.showMessageDialog(
                            TelaLogin.this,
                            causa.getMessage(),
                            "Erro ao entrar",
                            JOptionPane.ERROR_MESSAGE
                    );

                } finally {
                    campoEmail.setEnabled(true);
                    campoSenha.setEnabled(true);
                }
            }
        };

        tarefa.execute();
    }

    // CADASTRO PELA API
    private void criarConta() {

        JDialog janela = new JDialog(
                this, "Criar Conta - CineMatch", true
        );

        janela.setDefaultCloseOperation(
                JDialog.DISPOSE_ON_CLOSE
        );
        janela.setSize(470, 580);
        janela.setResizable(false);
        janela.setLocationRelativeTo(this);

        JPanel painel = new JPanel();
        painel.setBackground(FUNDO);
        painel.setLayout(
                new BoxLayout(painel, BoxLayout.Y_AXIS)
        );
        painel.setBorder(
                BorderFactory.createEmptyBorder(25, 40, 25, 40)
        );

        JLabel titulo = new JLabel("Criar conta");
        titulo.setFont(FONTE_TITULO);
        titulo.setForeground(BRANCO);
        titulo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel descricao = new JLabel(
                "Seu próximo filme começa aqui."
        );
        descricao.setFont(FONTE_TEXTO);
        descricao.setForeground(BRANCO);
        descricao.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextField nome = new JTextField();
        JTextField email = new JTextField();
        JPasswordField senha = new JPasswordField();
        JTextField nascimento = new JTextField();

        configurarCampo(nome);
        configurarCampo(email);
        configurarCampo(senha);
        configurarCampo(nascimento);

        JButton confirmar = criarBotao(
                "Criar conta", CORAL
        );
        JButton cancelar = criarBotao(
                "Cancelar", ROXO_BOTAO
        );

        painel.add(titulo);
        painel.add(Box.createVerticalStrut(8));
        painel.add(descricao);
        painel.add(Box.createVerticalStrut(23));

        painel.add(criarLabel("Nome"));
        painel.add(Box.createVerticalStrut(5));
        painel.add(nome);
        painel.add(Box.createVerticalStrut(13));

        painel.add(criarLabel("E-mail"));
        painel.add(Box.createVerticalStrut(5));
        painel.add(email);
        painel.add(Box.createVerticalStrut(13));

        painel.add(criarLabel("Senha"));
        painel.add(Box.createVerticalStrut(5));
        painel.add(senha);
        painel.add(Box.createVerticalStrut(13));

        painel.add(criarLabel(
                "Data de nascimento (DD/MM/AAAA)"
        ));
        painel.add(Box.createVerticalStrut(5));

        nascimento.setToolTipText(
                "Obrigatória para maiores de 18 anos"
        );

        painel.add(nascimento);
        painel.add(Box.createVerticalStrut(22));

        painel.add(confirmar);
        painel.add(Box.createVerticalStrut(10));
        painel.add(cancelar);

        confirmar.addActionListener(e -> {

            String nomeStr = nome.getText().trim();
            String emailStr = email.getText().trim();

            char[] caracteres = senha.getPassword();
            String senhaDigitada = new String(caracteres);
            Arrays.fill(caracteres, '\0');

            if (nomeStr.isEmpty()
                    || emailStr.isEmpty()
                    || senhaDigitada.isEmpty()) {

                JOptionPane.showMessageDialog(
                        janela,
                        "Preencha todos os campos!"
                );
                return;
            }

            LocalDate dataNascimento;

            try {
                DateTimeFormatter formato =
                        DateTimeFormatter
                                .ofPattern("dd/MM/uuuu")
                                .withResolverStyle(
                                        ResolverStyle.STRICT
                                );

                dataNascimento = LocalDate.parse(
                        nascimento.getText().trim(),
                        formato
                );

            } catch (DateTimeParseException ex) {

                JOptionPane.showMessageDialog(
                        janela,
                        "Digite uma data válida: DD/MM/AAAA."
                );
                return;
            }

            LocalDate hoje = LocalDate.now();

            if (dataNascimento.isAfter(
                    hoje.minusYears(18))) {

                JOptionPane.showMessageDialog(
                        janela,
                        "Cadastro exclusivo para maiores de 18 anos."
                );
                return;
            }

            if (dataNascimento.isBefore(
                    hoje.minusYears(110))) {

                JOptionPane.showMessageDialog(
                        janela,
                        "Confira o ano de nascimento informado."
                );
                return;
            }

            if (!emailStr.contains("@")
                    || senhaDigitada.length() < 6) {

                JOptionPane.showMessageDialog(
                        janela,
                        "Use um e-mail válido e senha com "
                                + "pelo menos 6 caracteres."
                );
                return;
            }

            confirmar.setEnabled(false);
            cancelar.setEnabled(false);
            confirmar.setText("Cadastrando...");

            final LocalDate nascimentoFinal =
                    dataNascimento;

            SwingWorker<String, Void> tarefa =
                    new SwingWorker<String, Void>() {

                @Override
                protected String doInBackground()
                        throws Exception {

                    return apiCliente.cadastrar(
                            nomeStr,
                            emailStr,
                            senhaDigitada,
                            nascimentoFinal
                    );
                }

                @Override
                protected void done() {

                    try {
                        get();

                        JOptionPane.showMessageDialog(
                                janela,
                                "Conta criada com sucesso pela API!"
                        );

                        campoEmail.setText(emailStr);
                        campoSenha.setText("");

                        janela.dispose();

                    } catch (Exception ex) {

                        Throwable causa = ex.getCause() != null
                                ? ex.getCause()
                                : ex;

                        JOptionPane.showMessageDialog(
                                janela,
                                causa.getMessage(),
                                "Erro ao cadastrar",
                                JOptionPane.ERROR_MESSAGE
                        );

                    } finally {
                        confirmar.setEnabled(true);
                        cancelar.setEnabled(true);
                        confirmar.setText("Criar conta");
                    }
                }
            };

            tarefa.execute();
        });

        cancelar.addActionListener(e -> janela.dispose());

        janela.add(painel);
        janela.setVisible(true);
    }
}
