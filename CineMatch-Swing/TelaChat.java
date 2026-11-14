
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.text.SimpleDateFormat;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class TelaChat extends JFrame {

    private final Color FUNDO = new Color(43, 22, 70);
    private final Color CARD = new Color(56, 32, 82);
    private final Color CORAL = new Color(255, 98, 92);
    private final Color ROXO = new Color(75, 46, 105);
    private final Color TEXTO = new Color(247, 243, 250);
    private final Color SUAVE = new Color(220, 204, 232);

    private final int meuId;
    private final int outroId;
    private final MensagemDAO dao = new MensagemDAO();

    private final JPanel painelMensagens = new JPanel();
    private final JScrollPane rolagem;

    private final JTextField campo = new JTextField();
    private final JButton enviar = new JButton("Enviar");

    private final Timer atualizador;
    private boolean carregando = false;
    private int ultimoId = -1;
    private int quantidadeAnterior = -1;
    private boolean primeiraCarga = true;

    public TelaChat(int outroId, String nome) {

        if (!Sessao.ativa()) {
            throw new IllegalStateException(
                "Faça login para conversar."
            );
        }

        meuId = Sessao.getUsuario().getId();
        this.outroId = outroId;

        boolean conexaoAceita = false;

        for (MatchDAO.Conexao c :
                new MatchDAO().listar(meuId)) {

            if (c.outroId == outroId &&
                    "ACEITA".equals(c.status)) {
                conexaoAceita = true;
                break;
            }
        }

        if (!conexaoAceita) {
            throw new IllegalStateException(
                "Esta pessoa não possui uma conexão aceita com você."
            );
        }

        setTitle("CineMatch - Conversa com " + nome);
        setSize(720, 620);
        setMinimumSize(new Dimension(480, 400));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel principal = new JPanel(
            new BorderLayout(0, 15)
        );
        principal.setBackground(FUNDO);
        principal.setBorder(
            new EmptyBorder(18, 18, 18, 18)
        );

        // CABEÇALHO

        JPanel cabecalho = new JPanel(
            new BorderLayout(12, 0)
        );
        cabecalho.setOpaque(false);

        String inicial = nome == null || nome.isBlank()
                ? "?"
                : nome.substring(0, 1).toUpperCase();

        JComponent avatar = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
                );

                int tamanho = Math.min(
                    getWidth(), getHeight()
                );

                g2.setColor(CORAL);
                g2.fillOval(0, 0, tamanho, tamanho);

                g2.setColor(Color.WHITE);
                g2.setFont(
                    new Font("SansSerif", Font.BOLD, 23)
                );

                FontMetrics fm = g2.getFontMetrics();

                int x = (tamanho -
                    fm.stringWidth(inicial)) / 2;

                int y = (tamanho -
                    fm.getHeight()) / 2 +
                    fm.getAscent();

                g2.drawString(inicial, x, y);
                g2.dispose();
            }
        };

        avatar.setPreferredSize(
            new Dimension(48, 48)
        );

        JPanel dados = new JPanel(
            new GridLayout(2, 1, 0, 3)
        );
        dados.setOpaque(false);

        JLabel titulo = new JLabel(nome);
        titulo.setForeground(TEXTO);
        titulo.setFont(
            new Font("Times New Roman", Font.BOLD, 25)
        );

        JLabel subtitulo = new JLabel(
            "CineMatch • Conversa"
        );
        subtitulo.setForeground(SUAVE);
        subtitulo.setFont(
            new Font("SansSerif", Font.PLAIN, 13)
        );

        dados.add(titulo);
        dados.add(subtitulo);

        cabecalho.add(avatar, BorderLayout.WEST);
        cabecalho.add(dados, BorderLayout.CENTER);

        principal.add(
            cabecalho,
            BorderLayout.NORTH
        );

        // HISTÓRICO

        painelMensagens.setBackground(CARD);
        painelMensagens.setLayout(
            new BoxLayout(
                painelMensagens,
                BoxLayout.Y_AXIS
            )
        );
        painelMensagens.setBorder(
            new EmptyBorder(12, 12, 12, 12)
        );

        rolagem = new JScrollPane(
            painelMensagens,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );

        rolagem.setBorder(null);
        rolagem.getViewport().setBackground(CARD);
        rolagem.getVerticalScrollBar()
            .setUnitIncrement(18);

        principal.add(
            rolagem,
            BorderLayout.CENTER
        );

        // CAMPO DE ENVIO

        JPanel rodape = new JPanel(
            new BorderLayout(10, 0)
        );
        rodape.setOpaque(false);

        campo.setFont(
            new Font("SansSerif", Font.PLAIN, 15)
        );
        campo.setBackground(Color.WHITE);
        campo.setForeground(new Color(40, 30, 50));
        campo.setCaretColor(new Color(40, 30, 50));
        campo.setBorder(
            new EmptyBorder(12, 14, 12, 14)
        );
        campo.setToolTipText(
            "Digite sua mensagem"
        );

        enviar.setBackground(CORAL);
        enviar.setForeground(Color.WHITE);
        enviar.setFocusPainted(false);
        enviar.setFont(
            new Font("Times New Roman", Font.BOLD, 17)
        );
        enviar.setBorder(
            new EmptyBorder(12, 20, 12, 20)
        );

        rodape.add(campo, BorderLayout.CENTER);
        rodape.add(enviar, BorderLayout.EAST);

        principal.add(
            rodape,
            BorderLayout.SOUTH
        );

        setContentPane(principal);

        enviar.addActionListener(
            e -> enviarMensagem()
        );
        campo.addActionListener(
            e -> enviarMensagem()
        );

        atualizador = new Timer(
            1000,
            e -> carregarMensagens()
        );

        setVisible(true);
        carregarMensagens();
        atualizador.start();

        SwingUtilities.invokeLater(
            () -> campo.requestFocusInWindow()
        );
    }

    // PAINEL COM CANTOS ARREDONDADOS

    private static class PainelArredondado
            extends JPanel {

        private final Color cor;

        PainelArredondado(Color cor) {
            this.cor = cor;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();

            g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            );

            g2.setColor(cor);
            g2.fill(new RoundRectangle2D.Double(
                0, 0,
                getWidth() - 1,
                getHeight() - 1,
                18, 18
            ));

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // BALÃO COM LARGURA E ALTURA AJUSTADAS

    private JPanel criarBalao(
            MensagemDAO.Mensagem mensagem) {

        boolean minha =
            mensagem.remetenteId == meuId;

        JPanel linha = new JPanel(
            new BorderLayout()
        );
        linha.setOpaque(false);
        linha.setAlignmentX(
            Component.LEFT_ALIGNMENT
        );

        PainelArredondado balao =
            new PainelArredondado(
                minha ? CORAL : ROXO
            );

        balao.setLayout(
            new BorderLayout(0, 4)
        );
        balao.setBorder(
            new EmptyBorder(9, 12, 7, 12)
        );

        JTextArea texto = new JTextArea(
            mensagem.texto == null
                ? ""
                : mensagem.texto
        );

        texto.setEditable(false);
        texto.setFocusable(false);
        texto.setOpaque(false);
        texto.setForeground(Color.WHITE);
        texto.setFont(
            new Font("SansSerif", Font.PLAIN, 15)
        );
        texto.setLineWrap(true);
        texto.setWrapStyleWord(true);
        texto.setBorder(null);

        // Calcula a largura real da mensagem.
        FontMetrics fm =
            texto.getFontMetrics(texto.getFont());

        String conteudo = texto.getText();
        String[] linhas = conteudo.split(
            "\\R", -1
        );

        int larguraNatural = 0;

        for (String parte : linhas) {
            larguraNatural = Math.max(
                larguraNatural,
                fm.stringWidth(parte)
            );
        }

        int larguraMaxima = 330;

        int larguraTexto = Math.max(
            45,
            Math.min(
                larguraNatural + 5,
                larguraMaxima
            )
        );

        texto.setSize(
            new Dimension(
                larguraTexto,
                Integer.MAX_VALUE / 100
            )
        );

        Dimension tamanhoTexto =
            texto.getPreferredSize();

        texto.setPreferredSize(
            new Dimension(
                larguraTexto,
                tamanhoTexto.height
            )
        );

        JLabel horario = new JLabel(
            new SimpleDateFormat("HH:mm")
                .format(mensagem.enviadaEm)
        );

        horario.setForeground(
            minha
                ? new Color(255, 235, 235)
                : SUAVE
        );

        horario.setFont(
            new Font("SansSerif", Font.PLAIN, 11)
        );
        horario.setHorizontalAlignment(
            SwingConstants.RIGHT
        );

        balao.add(texto, BorderLayout.CENTER);
        balao.add(horario, BorderLayout.SOUTH);

        Dimension tamanho =
            balao.getPreferredSize();

        balao.setPreferredSize(tamanho);
        balao.setMaximumSize(tamanho);

        JPanel alinhamento = new JPanel(
            new FlowLayout(
                minha
                    ? FlowLayout.RIGHT
                    : FlowLayout.LEFT,
                0, 0
            )
        );
        alinhamento.setOpaque(false);
        alinhamento.add(balao);

        linha.add(
            alinhamento,
            BorderLayout.CENTER
        );

        linha.setMaximumSize(
            new Dimension(
                Integer.MAX_VALUE,
                tamanho.height
            )
        );

        return linha;
    }

    // ENVIO

    private void enviarMensagem() {

        String texto = campo.getText().trim();

        if (texto.isEmpty()) {
            return;
        }

        if (texto.length() > 2000) {
            JOptionPane.showMessageDialog(
                this,
                "A mensagem deve ter no máximo 2000 caracteres."
            );
            return;
        }

        if (!Sessao.ativa() ||
            Sessao.getUsuario().getId() != meuId) {

            JOptionPane.showMessageDialog(
                this,
                "Sua sessão mudou. Abra a conversa novamente."
            );

            dispose();
            return;
        }

        enviar.setEnabled(false);
        campo.setEnabled(false);

        new SwingWorker<Boolean, Void>() {

            @Override
            protected Boolean doInBackground() {
                return dao.enviar(
                    meuId,
                    outroId,
                    texto
                );
            }

            @Override
            protected void done() {

                if (!isDisplayable()) {
                    return;
                }

                enviar.setEnabled(true);
                campo.setEnabled(true);

                try {
                    if (get()) {

                        if (campo.getText().equals(texto)) {
                            campo.setText("");
                        }

                        // Força a atualização após envio.
                        ultimoId = -1;
                        carregarMensagens();
                        campo.requestFocusInWindow();

                    } else {
                        JOptionPane.showMessageDialog(
                            TelaChat.this,
                            "Não foi possível enviar a mensagem."
                        );
                    }

                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                        TelaChat.this,
                        "Erro ao enviar: " +
                        ex.getMessage()
                    );
                }
            }
        }.execute();
    }

    // ATUALIZAÇÃO DO HISTÓRICO

    private void carregarMensagens() {

        if (carregando || !isDisplayable()) {
            return;
        }

        if (!Sessao.ativa() ||
            Sessao.getUsuario().getId() != meuId) {

            dispose();
            return;
        }

        carregando = true;

        new SwingWorker<
            List<MensagemDAO.Mensagem>, Void
        >() {

            @Override
            protected List<MensagemDAO.Mensagem>
                    doInBackground() {

                return dao.listar(
                    meuId,
                    outroId
                );
            }

            @Override
            protected void done() {

                carregando = false;

                if (!isDisplayable()) {
                    return;
                }

                try {
                    List<MensagemDAO.Mensagem> mensagens =
                        get();

                    int novoUltimoId =
                        mensagens.isEmpty()
                            ? 0
                            : mensagens.get(
                                mensagens.size() - 1
                            ).id;

                    if (novoUltimoId == ultimoId &&
                        mensagens.size() == quantidadeAnterior) {
                        return;
                    }

                    JScrollBar barra =
                        rolagem.getVerticalScrollBar();

                    boolean irParaFinal =
                        primeiraCarga ||
                        barra.getValue() +
                        barra.getVisibleAmount() >=
                        barra.getMaximum() - 30;

                    ultimoId = novoUltimoId;
                    quantidadeAnterior = mensagens.size();
                    primeiraCarga = false;

                    painelMensagens.removeAll();

                    if (mensagens.isEmpty()) {

                        JLabel vazio = new JLabel(
                            "Comece a conversa! 💜"
                        );

                        vazio.setForeground(SUAVE);
                        vazio.setFont(
                            new Font(
                                "SansSerif",
                                Font.PLAIN,
                                15
                            )
                        );

                        vazio.setAlignmentX(
                            Component.CENTER_ALIGNMENT
                        );

                        painelMensagens.add(vazio);

                    } else {

                        for (MensagemDAO.Mensagem m :
                                mensagens) {

                            painelMensagens.add(
                                criarBalao(m)
                            );

                            painelMensagens.add(
                                Box.createVerticalStrut(8)
                            );
                        }
                    }

                    painelMensagens.revalidate();
                    painelMensagens.repaint();

                    if (irParaFinal) {
                        SwingUtilities.invokeLater(
                            () -> barra.setValue(
                                barra.getMaximum()
                            )
                        );
                    }

                } catch (Exception ex) {
                    System.err.println(
                        "Erro ao atualizar chat: " +
                        ex.getMessage()
                    );
                }
            }
        }.execute();
    }

    @Override
    public void dispose() {
        if (atualizador != null) {
            atualizador.stop();
        }
        super.dispose();
    }
}
