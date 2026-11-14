
import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Solicitações e conexões aceitas do CineMatch. */
public class TelaConexoes extends JFrame {

    private final Color FUNDO = new Color(43, 22, 70);
    private final Color CARD = new Color(56, 32, 82);
    private final Color CORAL = new Color(255, 98, 92);
    private final Color ROXO = new Color(65, 37, 93);
    private final Color TEXTO = new Color(247, 243, 250);
    private final Color SUAVE = new Color(220, 204, 232);

    private final MatchDAO dao = new MatchDAO();
    private final JPanel lista = new JPanel();

    public TelaConexoes() {
        setTitle("CineMatch - Minhas Conexões");
        setSize(770, 650);
        setMinimumSize(new Dimension(600, 500));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel principal = new JPanel(new BorderLayout(15, 15));
        principal.setBackground(FUNDO);
        principal.setBorder(new EmptyBorder(22, 28, 22, 28));

        JPanel cab = new JPanel(new BorderLayout());
        cab.setOpaque(false);

        JLabel titulo = new JLabel("Minhas Conexões");
        titulo.setForeground(TEXTO);
        titulo.setFont(new Font(
            "Times New Roman", Font.BOLD, 27
        ));
        cab.add(titulo, BorderLayout.WEST);

        JButton fechar = botao("Fechar", ROXO);
        fechar.addActionListener(e -> dispose());
        cab.add(fechar, BorderLayout.EAST);

        principal.add(cab, BorderLayout.NORTH);

        lista.setBackground(FUNDO);
        lista.setLayout(
            new BoxLayout(lista, BoxLayout.Y_AXIS)
        );

        JScrollPane rolagem = new JScrollPane(
            lista,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );

        rolagem.getViewport().setBackground(FUNDO);
        rolagem.setBorder(null);
        rolagem.getVerticalScrollBar().setUnitIncrement(18);
        principal.add(rolagem, BorderLayout.CENTER);

        JButton atualizar = botao(
            "Atualizar solicitações", CORAL
        );
        atualizar.addActionListener(e -> recarregar());
        principal.add(atualizar, BorderLayout.SOUTH);

        setContentPane(principal);
        recarregar();
        setVisible(true);
    }

    private JButton botao(String s, Color cor) {
        JButton b = new JButton(s);
        b.setForeground(Color.WHITE);
        b.setBackground(cor);
        b.setFocusPainted(false);
        b.setFont(new Font(
            "Times New Roman", Font.BOLD, 16
        ));
        b.setBorder(new EmptyBorder(10, 14, 10, 14));
        return b;
    }

    private void recarregar() {
        lista.removeAll();

        if (!Sessao.ativa()) {
            lista.add(texto(
                "Faça login para ver suas conexões.",
                18, SUAVE
            ));
        } else {
            List<MatchDAO.Conexao> conexoes =
                dao.listar(Sessao.getUsuario().getId());

            if (conexoes.isEmpty()) {
                lista.add(texto(
                    "Você ainda não tem solicitações.",
                    18, SUAVE
                ));
            }

            for (MatchDAO.Conexao c : conexoes) {

                JPanel card = new JPanel(
                    new BorderLayout(15, 8)
                );
                card.setBackground(CARD);
                card.setBorder(
                    new EmptyBorder(15, 18, 15, 18)
                );
                card.setMaximumSize(
                    new Dimension(Integer.MAX_VALUE, 100)
                );

                JPanel info = new JPanel(
                    new GridLayout(2, 1, 0, 4)
                );
                info.setOpaque(false);

                info.add(texto(c.nome, 19, TEXTO));

                String status =
                    "ACEITA".equals(c.status)
                        ? "Conexão aceita"
                    : "RECUSADA".equals(c.status)
                        ? "Solicitação recusada"
                    : c.recebida
                        ? "Solicitação recebida"
                        : "Solicitação enviada";

                info.add(texto(status, 15, SUAVE));
                card.add(info, BorderLayout.CENTER);

                if (c.recebida &&
                    "PENDENTE".equals(c.status)) {

                    JPanel acoes = new JPanel(
                        new FlowLayout(FlowLayout.RIGHT, 8, 0)
                    );
                    acoes.setOpaque(false);

                    JButton aceitar = botao(
                        "Aceitar", CORAL
                    );
                    JButton recusar = botao(
                        "Recusar", ROXO
                    );

                    aceitar.addActionListener(
                        e -> responder(c.id, true)
                    );
                    recusar.addActionListener(
                        e -> responder(c.id, false)
                    );

                    acoes.add(aceitar);
                    acoes.add(recusar);
                    card.add(acoes, BorderLayout.EAST);

                } else if ("ACEITA".equals(c.status)) {

                    JPanel acoes = new JPanel(
                        new FlowLayout(FlowLayout.RIGHT, 8, 0)
                    );
                    acoes.setOpaque(false);

                    JButton conversar = botao(
                        "Conversar", CORAL
                    );

                    conversar.addActionListener(e -> {
                        if (!Sessao.ativa()) {
                            JOptionPane.showMessageDialog(
                                this,
                                "Faça login para conversar."
                            );
                            return;
                        }

                        try {
                            new TelaChat(c.outroId, c.nome);
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(
                                this,
                                ex.getMessage(),
                                "CineMatch - Chat",
                                JOptionPane.ERROR_MESSAGE
                            );
                        }
                    });

                    acoes.add(conversar);
                    card.add(acoes, BorderLayout.EAST);
                }

                lista.add(card);
                lista.add(Box.createVerticalStrut(12));
            }
        }

        lista.revalidate();
        lista.repaint();
    }

    private void responder(int id, boolean aceitar) {
        if (Sessao.ativa() &&
            dao.responder(
                id,
                Sessao.getUsuario().getId(),
                aceitar
            )) {
            recarregar();
        } else {
            JOptionPane.showMessageDialog(
                this,
                "Não foi possível responder à solicitação."
            );
        }
    }

    private JLabel texto(String s, int n, Color c) {
        JLabel l = new JLabel(s);
        l.setForeground(c);
        l.setFont(new Font(
            "Times New Roman", Font.PLAIN, n
        ));
        return l;
    }
}
