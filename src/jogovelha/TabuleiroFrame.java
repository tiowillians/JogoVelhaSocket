/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jogovelha;

import java.awt.Color;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Random;
import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

/**
 *
 * @author willi
 * 
 * Classe que controla a interface gráfica do jogo da velha
 */
public class TabuleiroFrame extends javax.swing.JFrame {
    private char[][] tabuleiro = new char[3][3];  // tabuleiro do jogo
    private boolean estaJogando;    // indica se jogo está em andamento
    private boolean estaConectado;  // indica se jogador local está conectado
    private boolean minhaVez;       // indica se é a vez do jogador local
    private boolean inicieiUltimoJogo;  // indica se último jogo foi iniciado pelo jogador local
    private boolean fuiConvidado;   // indica se jogador local foi convidado
    private ServerSocket servidorTCP;      // socket servidor TCP criado para jogador remoto se conectar
    private ConexaoTCP conexaoTCP;      // conexão TCP com o jogador remoto
    private String apelidoLocal;    // apelido do jogador local
    private DefaultListModel<JogadorOnLine> jogadores;  // lista de jogadores que estão online
    private final static Random numAleatorio = new Random();   // gerador de números aleatórios
    
    private final int PORTA_UDP = 20181;
    
    // cores dos jogadores no tabuleiro
    private final Color COR_LOCAL = new Color(51, 153, 0);
    private final Color COR_REMOTO = new Color(255, 0, 0);
    private final Color COR_EMPATE = new Color(255,255,0);
    
    // identificação dos jogadores
    public final static int JOGADOR_LOCAL = 1;
    public final static int JOGADOR_REMOTO = 2;
    public final char POSICAO_VAZIA = ' ';
    public final char POSICAO_LOCAL = 'X';
    public final char POSICAO_REMOTO = 'O';
    
    // motivos para jogo encerrar
    public final static int CONEXAO_TIMEOUT = 0;
    public final static int CONEXAO_CAIU = 1;
    public final static int JOGADOR_DESISTIU = 2;
    public final static int FIM_JOGO = 3;
    
    // resultados dos jogos
    private final int SEM_RESULTADO = -1;
    private final int EMPATE = 0;
    private final int VITORIA_LOCAL = 1;
    private final int VITORIA_REMOTO = 2;
    
    // posições no tabuleiro onde foi conseguido a vitória
    private final int SEM_GANHADOR = 0;
    private final int LINHA_1 = 1;
    private final int LINHA_2 = 2;
    private final int LINHA_3 = 3;
    private final int COLUNA_1 = 4;
    private final int COLUNA_2 = 5;
    private final int COLUNA_3 = 6;
    private final int DIAGONAL_PRINCIPAL = 7;
    private final int DIAGONAL_SECUNDARIA = 8;
    
    // tipos de mensagens mostradas na tela
    public static final String MSG_IN = "IN";
    public static final String MSG_OUT = "OUT";
    public static final String MSG_ERRO = "ERRO";
    public static final String MSG_INFO = "INFO";
    public static final String MSG_PROTO_TCP = "TCP";
    public static final String MSG_PROTO_UDP = "UDP";
    public static final String MSG_PROTO_NENHUM = "";
    
    private int[] resultados = new int[5];  // resultados de cada jogo
    private int jogoAtual;          // número do jogo atual

    // dados relacionados a threads e sockets
    private EscutaUDP udpEscutaThread;         // thread para leitura da porta UDP
    private EscutaTCP tcpEscutaThread;         // thread de escuta da porta TCP
    private InetAddress addrLocal;             // endereço do jogador local
    private InetAddress addrBroadcast;         // endereço para broadcasting
    private InetAddress addrJogadorRemoto;     // endereço do jogador remoto
    private String apelidoRemoto;              // apelido do jogador remoto
    private Timer quemEstaOnlineTimer;         // temporizador para saber quem está online
    private Timer timeoutQuemEstaOnlineTimer;  // temporizador de timeout
    private Timer timeoutAguardandoJogadorRemoto;    // temporizador de timeout
    
    // status do programa
    private boolean esperandoConexao;
    private boolean esperandoInicioJogo;
    private boolean esperandoConfirmacao;
    private boolean esperandoJogadorRemoto;
    private boolean esperandoRespostaConvite;
    
    /**
     * Creates new form TabuleiroFrame
     */
    public TabuleiroFrame() {
        initComponents();
        
        // título do programa
        this.setTitle("Jogo da Velha Remoto");
        
        // centraliza janela na tela
        this.setLocationRelativeTo(null);
        
        // inicializa variáveis
        estaJogando = estaConectado = false;
        servidorTCP = null;
        conexaoTCP = null;
        udpEscutaThread = null;
        tcpEscutaThread = null;
        addrLocal = null;
        esperandoConexao = esperandoInicioJogo = false;
        esperandoConfirmacao = esperandoJogadorRemoto = false;
        esperandoRespostaConvite = false;

        // cria endereço para broadcasting
        try {
            // envia broadcasting para avisar que eu estou online
            addrBroadcast = InetAddress.getByName("255.255.255.255");
        } catch (UnknownHostException ex) {
            JOptionPane.showMessageDialog(null,
                    "Não foi possível criar endereço para broadcasting.",
                    "Encerrando programa",
                    JOptionPane.ERROR_MESSAGE);
            encerraPrograma();
            return;
        }
        
        // cria lista de jogadores que estão online e
        // vincula a lista ao controle JList
        jogadores = new DefaultListModel<>();
        jogadoresJList.setModel(jogadores);
        jogadoresJList.setCellRenderer(new JogadorJListRenderer());

        // Coletar e mostrar interfaces de rede cadastradas
        try
        {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netint : Collections.list(nets))
            {
                // descarta interfaces virtuais e loopback (127.0.0.1)
                if (netint.isVirtual() || netint.isLoopback()){
                    continue;
                }

                // endereços associados à interface
                Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
                if(inetAddresses.hasMoreElements())
                {
                    for (InetAddress inetAddress : Collections.list(inetAddresses))
                    {
                        if ((inetAddress instanceof Inet4Address) &&
                            inetAddress.isSiteLocalAddress())
                        {
                            interfacesJComboBox.addItem(inetAddress.getHostAddress() +
                                    " - " + netint.getDisplayName());
                        }
                    }
                }
            }
        }catch(SocketException ex)
        {
        }
        
        // temporizador para atualização da lista de jogadores online
        // a cada 3 min (ou seja, a cada 180.000 milisegundos
        ActionListener quemEstaOnlinePerformer = (ActionEvent evt) -> {
            // limpa flag de jogador online da lista de jogadores
            for(int i = 0; i < jogadores.getSize(); ++i)
                jogadores.get(i).setAindaOnline(false);
            
            // envia mensagem para saber quem está online
            enviarMensagemUDP(addrBroadcast, 1, apelidoLocal);
            
            // dispara temporizador de timeout
            timeoutQuemEstaOnlineTimer.start();
        };
        quemEstaOnlineTimer = new Timer(180000, quemEstaOnlinePerformer);
        quemEstaOnlineTimer.setRepeats(true);   // temporizador repete indefinidamente
        
        // temporizador para timeout da atualização da lista de jogadores online
        // Quem não responder a MSG01 em 15 seg será considerado offline
        ActionListener timeoutQuemEstaOnlinePerformer = (ActionEvent evt) -> {
            atualizarListaJogadoresOnline();
        };
        timeoutQuemEstaOnlineTimer = new Timer(15000, timeoutQuemEstaOnlinePerformer);
        timeoutQuemEstaOnlineTimer.setRepeats(false);   // temporizador enviará notificação somente uma vez

        
        // temporizador para timeout de aguardando jogador remoto fazer
        // a conexão para iniciar o jogo ou jogador remoto responder
        // convite para jogar. Timeout de 30 segundos
        ActionListener timeoutAguardandoJogadorRemotoPerformer = (ActionEvent evt) -> {
            if(esperandoRespostaConvite)
                encerrarConviteParaJogar(true);
            else
                encerrarConexaoTCP(CONEXAO_TIMEOUT);
        };
        
        timeoutAguardandoJogadorRemoto = new Timer(30000, timeoutAguardandoJogadorRemotoPerformer);
        timeoutAguardandoJogadorRemoto.setRepeats(false);   // temporizador enviará notificação somente uma vez
    }
        
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel2 = new javax.swing.JLabel();
        jPanel13 = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        posicoesJPanel = new javax.swing.JPanel();
        jPanel4 = new javax.swing.JPanel();
        pos1JLabel = new javax.swing.JLabel();
        jPanel5 = new javax.swing.JPanel();
        pos2JLabel = new javax.swing.JLabel();
        jPanel6 = new javax.swing.JPanel();
        pos3JLabel = new javax.swing.JLabel();
        jPanel7 = new javax.swing.JPanel();
        pos4JLabel = new javax.swing.JLabel();
        jPanel8 = new javax.swing.JPanel();
        pos5JLabel = new javax.swing.JLabel();
        jPanel9 = new javax.swing.JPanel();
        pos6JLabel = new javax.swing.JLabel();
        jPanel10 = new javax.swing.JPanel();
        pos7JLabel = new javax.swing.JLabel();
        jPanel11 = new javax.swing.JPanel();
        pos8JLabel = new javax.swing.JLabel();
        jPanel12 = new javax.swing.JPanel();
        pos9JLabel = new javax.swing.JLabel();
        jogadorLocalJLabel = new javax.swing.JLabel();
        jogadorRemotoJLabel = new javax.swing.JLabel();
        statusJLabel = new javax.swing.JLabel();
        placarLocalJLabel = new javax.swing.JLabel();
        placarRemotoJLabel = new javax.swing.JLabel();
        jogo1JLabel = new javax.swing.JLabel();
        jogo2JLabel = new javax.swing.JLabel();
        jogo3JLabel = new javax.swing.JLabel();
        jogo4JLabel = new javax.swing.JLabel();
        jogo5JLabel = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jogadoresJList = new javax.swing.JList<>();
        convidarJButton = new javax.swing.JButton();
        sairJButton = new javax.swing.JButton();
        jPanel14 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        apelidoLocalJText = new javax.swing.JTextField();
        interfacesJComboBox = new javax.swing.JComboBox<>();
        conectarJButton = new javax.swing.JButton();
        messageJPanel = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        mensagensJTable = new javax.swing.JTable();

        jLabel2.setText("jLabel2");

        javax.swing.GroupLayout jPanel13Layout = new javax.swing.GroupLayout(jPanel13);
        jPanel13.setLayout(jPanel13Layout);
        jPanel13Layout.setHorizontalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );
        jPanel13Layout.setVerticalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setLocationByPlatform(true);
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        jPanel1.setBackground(new java.awt.Color(204, 204, 204));
        jPanel1.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));

        posicoesJPanel.setBackground(new java.awt.Color(153, 153, 153));

        jPanel4.setBackground(new java.awt.Color(204, 204, 204));
        jPanel4.setPreferredSize(new java.awt.Dimension(64, 64));

        pos1JLabel.setBackground(new java.awt.Color(255, 255, 255));
        pos1JLabel.setFont(new java.awt.Font("Calibri", 1, 54)); // NOI18N
        pos1JLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pos1JLabel.setAlignmentY(0.0F);
        pos1JLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pos1JLabelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(pos1JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pos1JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        jPanel5.setBackground(new java.awt.Color(204, 204, 204));
        jPanel5.setPreferredSize(new java.awt.Dimension(64, 64));

        pos2JLabel.setFont(new java.awt.Font("Calibri", 1, 54)); // NOI18N
        pos2JLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pos2JLabel.setAlignmentY(0.0F);
        pos2JLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pos2JLabelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel5Layout.createSequentialGroup()
                .addComponent(pos2JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pos2JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        jPanel6.setBackground(new java.awt.Color(204, 204, 204));
        jPanel6.setPreferredSize(new java.awt.Dimension(64, 64));

        pos3JLabel.setFont(new java.awt.Font("Calibri", 1, 54)); // NOI18N
        pos3JLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pos3JLabel.setAlignmentY(0.0F);
        pos3JLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pos3JLabelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addComponent(pos3JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addComponent(pos3JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        jPanel7.setBackground(new java.awt.Color(204, 204, 204));
        jPanel7.setPreferredSize(new java.awt.Dimension(64, 64));

        pos4JLabel.setFont(new java.awt.Font("Calibri", 1, 54)); // NOI18N
        pos4JLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pos4JLabel.setAlignmentY(0.0F);
        pos4JLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pos4JLabelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addComponent(pos4JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addComponent(pos4JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        jPanel8.setBackground(new java.awt.Color(204, 204, 204));
        jPanel8.setPreferredSize(new java.awt.Dimension(64, 64));

        pos5JLabel.setFont(new java.awt.Font("Calibri", 1, 54)); // NOI18N
        pos5JLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pos5JLabel.setAlignmentY(0.0F);
        pos5JLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pos5JLabelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel8Layout = new javax.swing.GroupLayout(jPanel8);
        jPanel8.setLayout(jPanel8Layout);
        jPanel8Layout.setHorizontalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addComponent(pos5JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel8Layout.setVerticalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addComponent(pos5JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        jPanel9.setBackground(new java.awt.Color(204, 204, 204));
        jPanel9.setPreferredSize(new java.awt.Dimension(64, 64));

        pos6JLabel.setFont(new java.awt.Font("Calibri", 1, 54)); // NOI18N
        pos6JLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pos6JLabel.setAlignmentY(0.0F);
        pos6JLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pos6JLabelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel9Layout = new javax.swing.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addComponent(pos6JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel9Layout.setVerticalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addComponent(pos6JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        jPanel10.setBackground(new java.awt.Color(204, 204, 204));
        jPanel10.setPreferredSize(new java.awt.Dimension(64, 64));

        pos7JLabel.setFont(new java.awt.Font("Calibri", 1, 54)); // NOI18N
        pos7JLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pos7JLabel.setAlignmentY(0.0F);
        pos7JLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pos7JLabelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel10Layout = new javax.swing.GroupLayout(jPanel10);
        jPanel10.setLayout(jPanel10Layout);
        jPanel10Layout.setHorizontalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addComponent(pos7JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel10Layout.setVerticalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addComponent(pos7JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        jPanel11.setBackground(new java.awt.Color(204, 204, 204));
        jPanel11.setPreferredSize(new java.awt.Dimension(64, 64));

        pos8JLabel.setFont(new java.awt.Font("Calibri", 1, 54)); // NOI18N
        pos8JLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pos8JLabel.setAlignmentY(0.0F);
        pos8JLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pos8JLabelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel11Layout = new javax.swing.GroupLayout(jPanel11);
        jPanel11.setLayout(jPanel11Layout);
        jPanel11Layout.setHorizontalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addComponent(pos8JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel11Layout.setVerticalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addComponent(pos8JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        jPanel12.setBackground(new java.awt.Color(204, 204, 204));
        jPanel12.setPreferredSize(new java.awt.Dimension(64, 64));

        pos9JLabel.setFont(new java.awt.Font("Calibri", 1, 54)); // NOI18N
        pos9JLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pos9JLabel.setAlignmentY(0.0F);
        pos9JLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pos9JLabelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel12Layout = new javax.swing.GroupLayout(jPanel12);
        jPanel12.setLayout(jPanel12Layout);
        jPanel12Layout.setHorizontalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addComponent(pos9JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel12Layout.setVerticalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addComponent(pos9JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout posicoesJPanelLayout = new javax.swing.GroupLayout(posicoesJPanel);
        posicoesJPanel.setLayout(posicoesJPanelLayout);
        posicoesJPanelLayout.setHorizontalGroup(
            posicoesJPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(posicoesJPanelLayout.createSequentialGroup()
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanel6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addGroup(posicoesJPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                .addGroup(posicoesJPanelLayout.createSequentialGroup()
                    .addComponent(jPanel10, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                    .addComponent(jPanel11, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                    .addComponent(jPanel12, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGroup(posicoesJPanelLayout.createSequentialGroup()
                    .addComponent(jPanel7, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                    .addComponent(jPanel8, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                    .addComponent(jPanel9, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );
        posicoesJPanelLayout.setVerticalGroup(
            posicoesJPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(posicoesJPanelLayout.createSequentialGroup()
                .addGroup(posicoesJPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jPanel5, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel4, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(posicoesJPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jPanel9, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel7, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel8, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(posicoesJPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jPanel12, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel10, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel11, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jogadorLocalJLabel.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        jogadorLocalJLabel.setForeground(new java.awt.Color(51, 153, 0));
        jogadorLocalJLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        jogadorLocalJLabel.setText("X - Local");
        jogadorLocalJLabel.setEnabled(false);

        jogadorRemotoJLabel.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        jogadorRemotoJLabel.setForeground(new java.awt.Color(255, 0, 0));
        jogadorRemotoJLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        jogadorRemotoJLabel.setText("Remoto - O");
        jogadorRemotoJLabel.setEnabled(false);

        statusJLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        statusJLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);

        placarLocalJLabel.setFont(new java.awt.Font("Tahoma", 1, 24)); // NOI18N
        placarLocalJLabel.setForeground(new java.awt.Color(0, 0, 255));
        placarLocalJLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        placarLocalJLabel.setText("0");
        placarLocalJLabel.setEnabled(false);

        placarRemotoJLabel.setFont(new java.awt.Font("Tahoma", 1, 24)); // NOI18N
        placarRemotoJLabel.setForeground(new java.awt.Color(0, 0, 255));
        placarRemotoJLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        placarRemotoJLabel.setText("0");
        placarRemotoJLabel.setEnabled(false);

        jogo1JLabel.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        jogo1JLabel.setText("1");
        jogo1JLabel.setEnabled(false);

        jogo2JLabel.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        jogo2JLabel.setText("2");
        jogo2JLabel.setEnabled(false);

        jogo3JLabel.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        jogo3JLabel.setText("3");
        jogo3JLabel.setEnabled(false);

        jogo4JLabel.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        jogo4JLabel.setText("4");
        jogo4JLabel.setEnabled(false);

        jogo5JLabel.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        jogo5JLabel.setText("5");
        jogo5JLabel.setEnabled(false);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addComponent(placarLocalJLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jogo1JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 8, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jogo2JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 8, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jogo3JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 8, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jogo4JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 8, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jogo5JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 8, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(175, 175, 175)
                        .addComponent(placarRemotoJLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(31, 31, 31))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addComponent(jogadorLocalJLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 169, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(statusJLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 164, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jogadorRemotoJLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 169, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap())))
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(145, 145, 145)
                .addComponent(posicoesJPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jogadorRemotoJLabel)
                        .addComponent(jogadorLocalJLabel))
                    .addComponent(statusJLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(placarLocalJLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(placarRemotoJLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jogo1JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jogo2JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jogo3JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jogo4JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jogo5JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(posicoesJPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 214, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(16, Short.MAX_VALUE))
        );

        jPanel2.setBackground(new java.awt.Color(153, 153, 153));
        jPanel2.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));

        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        jLabel1.setText("Jogadores Online");

        jogadoresJList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jogadoresJList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                jogadoresJListValueChanged(evt);
            }
        });
        jScrollPane1.setViewportView(jogadoresJList);

        convidarJButton.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        convidarJButton.setText("Convidar");
        convidarJButton.setEnabled(false);
        convidarJButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                convidarJButtonActionPerformed(evt);
            }
        });

        sairJButton.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        sairJButton.setText("Sair");
        sairJButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sairJButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addGap(0, 76, Short.MAX_VALUE))
                    .addComponent(convidarJButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(sairJButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(convidarJButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(sairJButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jPanel2Layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {convidarJButton, sairJButton});

        jPanel14.setBorder(javax.swing.BorderFactory.createTitledBorder("Jogador Local"));

        jLabel3.setText("Apelido:");

        jLabel4.setText("Interface:");

        conectarJButton.setText("Conectar");
        conectarJButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                conectarJButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel14Layout = new javax.swing.GroupLayout(jPanel14);
        jPanel14.setLayout(jPanel14Layout);
        jPanel14Layout.setHorizontalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel14Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(apelidoLocalJText, javax.swing.GroupLayout.PREFERRED_SIZE, 111, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(36, 36, 36)
                .addComponent(jLabel4)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(interfacesJComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 383, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(conectarJButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel14Layout.setVerticalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(jLabel3)
                .addComponent(jLabel4)
                .addComponent(apelidoLocalJText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(interfacesJComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(conectarJButton))
        );

        messageJPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Mensagens"));

        mensagensJTable.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        mensagensJTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Tipo", "Protocolo", "Endereço", "Porta", "Conteúdo"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                true, false, false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        mensagensJTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_LAST_COLUMN);
        mensagensJTable.setFillsViewportHeight(true);
        mensagensJTable.setMinimumSize(new java.awt.Dimension(180, 64));
        mensagensJTable.setPreferredSize(null);
        mensagensJTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        mensagensJTable.getTableHeader().setReorderingAllowed(false);
        jScrollPane2.setViewportView(mensagensJTable);
        mensagensJTable.getColumnModel().getSelectionModel().setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

        javax.swing.GroupLayout messageJPanelLayout = new javax.swing.GroupLayout(messageJPanel);
        messageJPanel.setLayout(messageJPanelLayout);
        messageJPanelLayout.setHorizontalGroup(
            messageJPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(messageJPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 729, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        messageJPanelLayout.setVerticalGroup(
            messageJPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(messageJPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel14, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jPanel14, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(messageJPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(13, 13, 13))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void sairJButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sairJButtonActionPerformed
        // encerra programa
        encerraPrograma();
    }//GEN-LAST:event_sairJButtonActionPerformed

    private void pos2JLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pos2JLabelMouseClicked
        escolhePosicao(2);
    }//GEN-LAST:event_pos2JLabelMouseClicked

    private void pos3JLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pos3JLabelMouseClicked
        escolhePosicao(3);
    }//GEN-LAST:event_pos3JLabelMouseClicked

    private void pos4JLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pos4JLabelMouseClicked
        escolhePosicao(4);
    }//GEN-LAST:event_pos4JLabelMouseClicked

    private void pos5JLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pos5JLabelMouseClicked
        escolhePosicao(5);
    }//GEN-LAST:event_pos5JLabelMouseClicked

    private void pos6JLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pos6JLabelMouseClicked
        escolhePosicao(6);
    }//GEN-LAST:event_pos6JLabelMouseClicked

    private void pos7JLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pos7JLabelMouseClicked
        escolhePosicao(7);
    }//GEN-LAST:event_pos7JLabelMouseClicked

    private void pos8JLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pos8JLabelMouseClicked
        escolhePosicao(8);
    }//GEN-LAST:event_pos8JLabelMouseClicked

    private void pos9JLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pos9JLabelMouseClicked
        escolhePosicao(9);
    }//GEN-LAST:event_pos9JLabelMouseClicked

    private void pos1JLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pos1JLabelMouseClicked
        escolhePosicao(1);
    }//GEN-LAST:event_pos1JLabelMouseClicked

    private void encerraPrograma()
    {
        // informa à rede que jogador local ficou offline
        enviarMensagemUDP(addrBroadcast, 3, apelidoLocal, true);
        
        Container frame = sairJButton.getParent();
        do
        {
            frame = frame.getParent(); 
        }while (!(frame instanceof JFrame));  
        ((JFrame)frame).dispose();
    }
    
    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // programa está sendo encerrado. Fazer os ajustes finais
        if(quemEstaOnlineTimer.isRunning())
            quemEstaOnlineTimer.stop();
        if(timeoutQuemEstaOnlineTimer.isRunning())
            timeoutQuemEstaOnlineTimer.stop();
        if(timeoutAguardandoJogadorRemoto.isRunning())
            timeoutAguardandoJogadorRemoto.stop();

        // informa à rede que jogador local ficou offline
        enviarMensagemUDP(addrBroadcast, 3, apelidoLocal);
        
        // encerra thread de escuta da porta UDP
        if (udpEscutaThread != null)
        {
            udpEscutaThread.encerraConexao();
            udpEscutaThread.cancel(true);
        }
        
        // encerra thread de escuta da porta TCP
        if (tcpEscutaThread != null)
        {
            tcpEscutaThread.encerraConexao();
            tcpEscutaThread.cancel(true);
        }
    }//GEN-LAST:event_formWindowClosing
            
    private void desconectaJogadorLocal()
    {
        estaConectado = false;
        
        // encerra temporizador de atualização da lista de jogadores online
        if(quemEstaOnlineTimer.isRunning())
            quemEstaOnlineTimer.stop();
        if(timeoutQuemEstaOnlineTimer.isRunning())
            timeoutQuemEstaOnlineTimer.stop();
        if(timeoutAguardandoJogadorRemoto.isRunning())
            timeoutAguardandoJogadorRemoto.stop();
        
        // limpa lista de jogadores online
        jogadores.clear();
        
        // envia mensagem informando que jogador local ficou offline
        enviarMensagemUDP(addrBroadcast, 3, apelidoLocal);
        
        // habilita/desabilita controles
        apelidoLocalJText.setEnabled(true);
        interfacesJComboBox.setEnabled(true);
        conectarJButton.setText("Conectar");
        jogadorLocalJLabel.setEnabled(false);
        
        // apaga apelido do jogador local no tabuleiro
        jogadorLocalJLabel.setText(POSICAO_LOCAL + " - Local");
        
        // encerra thread de leitura da porta UDP
        if (udpEscutaThread != null)
        {
            udpEscutaThread.encerraConexao();
            udpEscutaThread.cancel(true);
        }
        
        // encerra thread de leitura da porta TCP
        if (tcpEscutaThread != null)
        {
            tcpEscutaThread.encerraConexao();
            tcpEscutaThread.cancel(true);
        }
       
        statusJLabel.setText("");
        apelidoLocalJText.requestFocus();
    }

    private void jogadoresJListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_jogadoresJListValueChanged
        int idx = jogadoresJList.getSelectedIndex();
        convidarJButton.setEnabled(idx >= 0);
    }//GEN-LAST:event_jogadoresJListValueChanged

    private void convidarJButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_convidarJButtonActionPerformed
        JogadorOnLine j = jogadoresJList.getSelectedValue();
        if (j == null)
            return;
        
        // salva dados do jogador remoto
        apelidoRemoto = j.getApelido();
        addrJogadorRemoto = j.getAddress();
        
        // confirma convite
        statusJLabel.setText("");
        String msg = "Convida " + apelidoRemoto + " para jogar?";
        int resp = JOptionPane.showConfirmDialog(this, msg, "Convite para jogar",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        // jogador local desistiu do convite
        if (resp == JOptionPane.NO_OPTION)
            return;

        // enviar convite para jogador remoto e iniciar temporizador para timeout
        enviarMensagemUDP(j.getAddress(), 4, apelidoLocal);
        esperandoRespostaConvite = true;
        statusJLabel.setText("AGUARDANDO RESPOSTA");
        // DEBUG: timeoutAguardandoJogadorRemoto.start();
    }//GEN-LAST:event_convidarJButtonActionPerformed

    private void conectarJButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_conectarJButtonActionPerformed
        if(estaConectado)
        {
            desconectaJogadorLocal();
            return;
        }

        // apelido do jogador local
        apelidoLocal = apelidoLocalJText.getText().trim();
        if (apelidoLocal.isEmpty())
        {
            apelidoLocalJText.requestFocus();
            return;
        }

        // verifica se usuário escolheu a interface
        int nInterface = interfacesJComboBox.getSelectedIndex();
        if(nInterface < 0)
        {
            interfacesJComboBox.requestFocus();
            return;
        }

        // obtem endereço da interface de rede selecionada
        addrLocal = obtemInterfaceRede();
        if(addrLocal == null)
        {
            JOptionPane.showMessageDialog(null,
                "Erro na obtenção da interface escolhida.",
                "Conexão do jogador local",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        // cria thread para leitura da porta UDP
        try
        {
            udpEscutaThread = new EscutaUDP(this, PORTA_UDP, apelidoLocal,
                addrLocal);
        }catch(SocketException ex)
        {
            JOptionPane.showMessageDialog(null,
                "Erro na criação do thread de leitura da porta "+ PORTA_UDP +
                ".\n" + ex.getMessage(),
                "Conexão do jogador local",
                JOptionPane.ERROR_MESSAGE);
            encerraPrograma();
            return;
        }

        estaConectado = true;

        // habilita/desabilita controles
        apelidoLocalJText.setEnabled(false);
        interfacesJComboBox.setEnabled(false);
        conectarJButton.setText("Desconectar");
        jogadorLocalJLabel.setEnabled(true);

        // mostra apelido do jogador local no tabuleiro
        jogadorLocalJLabel.setText(POSICAO_LOCAL + " - " + apelidoLocal);

        // executa thread de leitura da porta UDP
        udpEscutaThread.execute();

        // envia mensagem para todos os jogadores informando que
        // jogador local ficou online
        enviarMensagemUDP(addrBroadcast, 1, apelidoLocal);

        // inicia temporizador de atualização da lista de jogadores online
        if(quemEstaOnlineTimer.isRunning() == false)
        quemEstaOnlineTimer.start();

    }//GEN-LAST:event_conectarJButtonActionPerformed
                
    private void encerrarConviteParaJogar(boolean timeout)
    {
        esperandoRespostaConvite = false;
        statusJLabel.setText("");
        
        String msg;
        if(timeout)
            msg = "Timeout: " + apelidoRemoto + " não respondeu convite.";
        else
            msg = apelidoRemoto + " recusou o convite.";
        JOptionPane.showMessageDialog(this, msg, "Convite para jogar",
                                      JOptionPane.INFORMATION_MESSAGE);
    }

    // jogador remoto respondeu convite para jogar
    public void respostaConvite(String msg, InetAddress addr)
    {
        // formato da resposta: Apelido|porta
        String[] strPartes= msg.split("\\|");
        if(strPartes.length != 2)
            return;
        
        // estou esperando uma resposta?
        if(esperandoRespostaConvite == false)
            return;
        
        // verifica se quem respondeu foi realmente o jogador remoto
        if ((addr.equals(addrJogadorRemoto) == false) ||
            apelidoRemoto.compareToIgnoreCase(strPartes[0]) != 0)
                return;

        // cancela espera da resposta ao convite
        esperandoRespostaConvite = false;
        if(timeoutAguardandoJogadorRemoto.isRunning())
            timeoutAguardandoJogadorRemoto.stop();

        int porta = Integer.parseInt(strPartes[1]);
        if(porta == 0)
        {
            // jogador recusou o convite
            encerrarConviteParaJogar(false);
            return;
        }
        
        // jogador remoto aceitou convite: enviar mensagem de confirmação
        enviarMensagemUDP(addr, 6, "Ok");
        
        // conectar com jogador remoto via TCP
        try
        {
            // conecta com jogador remoto
            Socket socket = new Socket(addr, porta);
            conexaoTCP = new ConexaoTCP(this, socket);
            
            // inicia thread de comunicação
            conexaoTCP.execute();

            // atualiza variáveis e controles
            statusJLabel.setText("AGUARDANDO INÍCIO");
            esperandoInicioJogo = true;
        } catch(IOException ex)
        {
            String erro = "Erro ao criar conexão TCP com jogador remoto\n" +
                         ex.getMessage();
            JOptionPane.showMessageDialog(this, erro,
                    "Conectar com jogador remoto", JOptionPane.INFORMATION_MESSAGE);
        }
    }
        
    // jogador local escolhe uma posição livre no tabuleiro
    private void escolhePosicao(int pos)
    {
        // verifica se existe jogo em andamento e é vez do jogador corrente
        if (estaJogando == true && minhaVez == true)
            marcaPosicao(JOGADOR_LOCAL, pos);
    }

    public void marcaPosicao(int quemEscolheu, int pos)
    {
        // valida posição (só para garantir)
        if((pos < 1) || (pos > 9))
            return;
        
        // verifica se posição está vazia
        int linha = (pos - 1) / 3;
        int coluna = (pos - 1) % 3;
        if (tabuleiro[linha][coluna] != POSICAO_VAZIA)
            return;
        
        Color cor;
        char marca;
        if(quemEscolheu == JOGADOR_LOCAL)
        {
            cor = COR_LOCAL;
            marca = POSICAO_LOCAL;
        }
        else
        {
            cor = COR_REMOTO;
            marca = POSICAO_REMOTO;
        }
        
        // preenche tabuleiro e mostra posição selecionada
        tabuleiro[linha][coluna] = marca;
        javax.swing.JLabel ctrl = null;
        switch(pos)
        {
            case 1: ctrl = pos1JLabel; break;
            case 2: ctrl = pos2JLabel; break;
            case 3: ctrl = pos3JLabel; break;
            case 4: ctrl = pos4JLabel; break;
            case 5: ctrl = pos5JLabel; break;
            case 6: ctrl = pos6JLabel; break;
            case 7: ctrl = pos7JLabel; break;
            case 8: ctrl = pos8JLabel; break;
            case 9: ctrl = pos9JLabel; break;
        }
        ctrl.setForeground(cor);
        ctrl.setText(Character.toString(marca));
        
        // se quem escolheu a posição foi o jogador local,
        // enviar escolha para o jogador remoto
        if(quemEscolheu == JOGADOR_LOCAL)
            conexaoTCP.enviarMensagemTCP(8, String.valueOf(pos));
        
        // verifica se houve ganhador
        int ganhador = verificaGanhador();
        if(ganhador != SEM_GANHADOR)
        {
            resultados[jogoAtual - 1] = ganhador % 10;
            mostraResultadoPartida(ganhador);
            novaPartida(ganhador);
            return;
        }
        
        // verifica se jogo empatou
        if (jogoEmpatou())
        {
            resultados[jogoAtual - 1] = EMPATE;
            mostraResultadoPartida(EMPATE);
            novaPartida(EMPATE);
            return;
        }
        
        // agora é a vez do outro jogador
        if (quemEscolheu == JOGADOR_LOCAL)
        {
            minhaVez = false;
            statusJLabel.setText("AGUARDANDO JOGADOR");
        }
        else
        {
            minhaVez = true;
            statusJLabel.setText("SUA VEZ");
        }
    }

    // processamento da mensagem MSG07: quem iniciará o jogo
    public void quemIniciaJogo(int jogador)
    {
        // atualiza controles e variáveis
        esperandoInicioJogo = false;
        
        iniciarSerieJogos();
        
        // verifica quem irá iniciar o jogo
        if (jogador == 1)
        {
            // jogador remoto iniciará o jogo
            minhaVez = inicieiUltimoJogo = true;
            statusJLabel.setText("ESPERANDO JOGADOR");
        }
        else
        {
            // jogador local iniciará o jogo
            minhaVez = inicieiUltimoJogo = true;
            statusJLabel.setText("SUA VEZ");
        }
    }

    private void mostraResultadoPartida(int quemGanhou)
    {
        // destaca no tabuleiro as posições vencedoras
        destacaResultadoTabuleiro(quemGanhou / 10);
        mostraResultados();
        String msg = "";
        switch(quemGanhou % 10)
        {
            case EMPATE: msg = "Partida empatou!"; break;
            case VITORIA_LOCAL: msg = "Você ganhou!"; break;
            case VITORIA_REMOTO: msg = "Você perdeu!"; break;
        }
        
        if (jogoAtual == 5)
        {
            int local = Integer.parseInt(placarLocalJLabel.getText());
            int remoto = Integer.parseInt(placarRemotoJLabel.getText());
            msg += "\n\nPlacar final:" +
                   "\n    " + apelidoLocal + ": " + local +
                   "\n    " + apelidoRemoto + ": " + remoto +
                   "\n\n";
            if (local == remoto)
                msg += "Essa série ficou EMPATADA!";
            else
                if (local > remoto)
                    msg += "Você ganhou a série. Parabéns!";
                else
                    msg += apelidoRemoto + " ganhou a série!";
            
            msg += "\n\nPara jogar uma nova série,\njogador deverá ser convidado novamente.";
        }
        
        JOptionPane.showMessageDialog(this, msg, "Partida " + jogoAtual + " de 5.",
                                      JOptionPane.INFORMATION_MESSAGE);
    }
    
    // em caso de vitória, destaca no tabuleiro as posições que propiciaram
    // a vitória. Caso contrário, nenhuma posição será destacada
    private void destacaResultadoTabuleiro(int posicoesVencedoras)
    {
        boolean[][] destaca = {{false, false, false},
                               {false, false, false},
                               {false, false, false}};
        
        switch(posicoesVencedoras)
        {
            case LINHA_1:
                destaca[0][0] = destaca[0][1] = destaca[0][2] = true;
                break;
            case LINHA_2:
                destaca[1][0] = destaca[1][1] = destaca[1][2] = true;
                break;
            case LINHA_3:
                destaca[2][0] = destaca[2][1] = destaca[2][2] = true;
                break;
            case COLUNA_1:
                destaca[0][0] = destaca[1][0] = destaca[2][0] = true;
                break;
            case COLUNA_2:
                destaca[0][1] = destaca[1][1] = destaca[2][1] = true;
                break;
            case COLUNA_3:
                destaca[0][2] = destaca[1][2] = destaca[2][2] = true;
                break;
            case DIAGONAL_PRINCIPAL:
                destaca[0][0] = destaca[1][1] = destaca[2][2] = true;
                break;
            case DIAGONAL_SECUNDARIA:
                destaca[0][2] = destaca[1][1] = destaca[2][0] = true;
                break;
        }
        
        int linha, coluna;
        javax.swing.JLabel ctrl = null;
        for(int pos = 0; pos < 9; ++pos)
        {
            linha = pos / 3;
            coluna = pos % 3;
            switch(pos)
            {
                case 0: ctrl = pos1JLabel; break;
                case 1: ctrl = pos2JLabel; break;
                case 2: ctrl = pos3JLabel; break;
                case 3: ctrl = pos4JLabel; break;
                case 4: ctrl = pos5JLabel; break;
                case 5: ctrl = pos6JLabel; break;
                case 6: ctrl = pos7JLabel; break;
                case 7: ctrl = pos8JLabel; break;
                case 8: ctrl = pos9JLabel; break;
            }
            
            if (destaca[linha][coluna] == false)
                ctrl.setForeground(Color.DARK_GRAY);
        }
    }
    
    private int verificaGanhador()
    {
        // verifica linhas
        for(int linha = 0; linha < 3; ++linha)
        {
            if((tabuleiro[linha][0] != POSICAO_VAZIA) &&
               (tabuleiro[linha][0] == tabuleiro[linha][1]) &&
               (tabuleiro[linha][1] == tabuleiro[linha][2]))
            {
                int resultado = 0;
                switch(linha)
                {
                    case 0: resultado = LINHA_1; break;
                    case 1: resultado = LINHA_2; break;
                    case 2: resultado = LINHA_3; break;
                }
                return 10 * resultado +
                       (tabuleiro[linha][0] == POSICAO_LOCAL ? JOGADOR_LOCAL : JOGADOR_REMOTO);
            }
        }
        
        // verifica colunas
        for(int coluna = 0; coluna < 3; ++coluna)
        {
            if((tabuleiro[0][coluna] != POSICAO_VAZIA) &&
               (tabuleiro[0][coluna] == tabuleiro[1][coluna]) &&
               (tabuleiro[1][coluna] == tabuleiro[2][coluna]))
            {
                int resultado = 0;
                switch(coluna)
                {
                    case 0: resultado = COLUNA_1; break;
                    case 1: resultado = COLUNA_2; break;
                    case 2: resultado = COLUNA_3; break;
                }
                
                return 10 * resultado +
                       (tabuleiro[0][coluna] == POSICAO_LOCAL ? JOGADOR_LOCAL : JOGADOR_REMOTO);
            }
        }
        
        // verifica diagonal principal
        if((tabuleiro[0][0] != POSICAO_VAZIA) &&
           (tabuleiro[0][0] == tabuleiro[1][1]) &&
           (tabuleiro[1][1] == tabuleiro[2][2]))
                return 10 * DIAGONAL_PRINCIPAL +
                       (tabuleiro[0][0] == POSICAO_LOCAL ? JOGADOR_LOCAL : JOGADOR_REMOTO);
        
        // verifica diagonal secundária
        if((tabuleiro[0][2] != POSICAO_VAZIA) &&
           (tabuleiro[0][2] == tabuleiro[1][1]) &&
           (tabuleiro[1][1] == tabuleiro[2][0]))
                return 10 * DIAGONAL_SECUNDARIA +
                       (tabuleiro[0][2] == POSICAO_LOCAL ? JOGADOR_LOCAL : JOGADOR_REMOTO);

        // não teve ganhador
        return SEM_GANHADOR;
    }
    
    // partida atual acabou: inicia nova partida ou encerra jogo
    private void novaPartida(int ultimoGanhador)
    {
        if (jogoAtual == 5)
        {
            // encerra jogo
            encerrarConexaoTCP(FIM_JOGO);
            
            return;
        }
        
        // limpa tabuleiro para início de nova partida
        limpaTabuleiro();
        
        // inicia nova partida
        ++jogoAtual;
        mostraResultados();
        
        // jogador local envia mensagem de início de nova partida se:
        //     (1) jogador local tiver perdido a última partida
        //     (2) última partida tiver empatada e jogador remoto começou
        //         a partida anterior
        if (ultimoGanhador != JOGADOR_LOCAL)
        {
            boolean enviaMensagem = true;
            if(ultimoGanhador == EMPATE)
                enviaMensagem = !inicieiUltimoJogo;
            
            if (enviaMensagem)
            {
                // iniciar novo jogo
                conexaoTCP.enviarMensagemTCP(9, null);
                
                minhaVez = inicieiUltimoJogo = true;
                statusJLabel.setText("SUA VEZ");
            }
        }
        else
        {
            // esperar jogador remoto iniciar novo jogo
            minhaVez = inicieiUltimoJogo = false;
            statusJLabel.setText("AGUARDANDO INÍCIO");
            esperandoInicioJogo = true;
        }
    }
    
    public void jogadorRemotoIniciaNovoJogo()
    {
        esperandoInicioJogo = false;
        statusJLabel.setText("AGUARDANDO JOGADOR");
    }
    
    // se não existir nenhuma posição livre no tabuleiro, o jogo empatou
    private boolean jogoEmpatou()
    {
        for(int i = 0; i < 3; ++i)
            for(int j = 0; j < 3; ++j)
                if(tabuleiro[i][j] == POSICAO_VAZIA)
                    return false;
        
        return true;
    }
    
    // o último parâmetro é opcional e indica se programa está encerrando.
    // Nesse caso, não deverá ser mostrado a mensagem UDP enviada, pois
    // a tabela não existe mais.
    public void enviarMensagemUDP(InetAddress addr, int numero,
                                  String compl, Boolean... encerra)
    {
        // verifica se último parâmetro foi informado. Se programa
        // estiver encerrando, não mostrar mensagem.
        boolean mostrarMensagens = true;
        if ((encerra.length > 0) && (encerra[0] instanceof Boolean))
            mostrarMensagens = !encerra[0];

        String msg;
        if((compl == null) || compl.isEmpty())
            msg = String.format("%02d005", numero);
        else
            msg = String.format("%02d%03d%s", numero, 5 + compl.length(),
                                compl);
        
        DatagramPacket p = new DatagramPacket(msg.getBytes(),
                        msg.getBytes().length, addr, PORTA_UDP);
        
        DatagramSocket udpSocket = null;
        try {
            // cria um socket do tipo datagram e liga-o a qualquer porta
            // disponível. Lembrando que PORTA_UDP local está ocupada
            udpSocket = new DatagramSocket(0, addrLocal);
            udpSocket.setBroadcast(addr.equals(addrBroadcast));
            
            // envia dados para o endereço e porta especificados no pacote
            udpSocket.send(p);                    
            
            // mostra mensagem enviada
            if(mostrarMensagens)
                mostraMensagem(MSG_OUT, MSG_PROTO_UDP, addr.getHostAddress(),
                               udpSocket.getLocalPort(), msg);
        } catch (IOException ex) {
            if(mostrarMensagens)
                mostraMensagem(MSG_OUT, MSG_PROTO_UDP,
                               addr.getHostAddress(),
                               (udpSocket == null ? 0 : udpSocket.getPort()),
                               "Erro: Envio da mensagem [msg " + numero + "]");
        }
    }
    
    // adiciona mensagem na tabela de mensagens
    public void mostraMensagem(String inORout, String protocolo,
                               String endereco, int porta, String conteudo)
    {
        DefaultTableModel model = (DefaultTableModel)mensagensJTable.getModel();
        model.addRow(new String[]{inORout, protocolo, endereco,
                                  (porta > 0 ? String.valueOf(porta) : ""),
                                  conteudo});
        
        // seleciona a linha que foi inserida
        mensagensJTable.changeSelection(mensagensJTable.getRowCount() - 1, 0,false,false);
    }

    public void atualizarListaJogadoresOnline()
    {
        for(int i = 0; i < jogadores.size(); ++i)
        {
            if(jogadores.get(i).getAindaOnline() == false)
                jogadores.remove(i);
        }
    }
    
    // insere ou confirma jogador na lista de jogadore online,
    // em ordem alfabética
    public void adicionaJogador(int nMsg, String apelido, InetAddress addr)
    {
        JogadorOnLine j;
        JogadorOnLine novoJogador;
        
        // percorre toda a lista
        for(int i = 0; i < jogadores.size(); ++i)
        {
            // jogador corrente
            j = jogadores.get(i);
            
            // verifica se jogador já está na lista
            if(j.mesmoApelido(apelido))
            {
                j.setAindaOnline(true); // jogador ainda está online
        
                // informar para o jogador que enviou o pacote que eu estou online
                if(nMsg == 1)
                    enviarMensagemUDP(addr, 2, apelidoLocal);
                
                return;
            }
            
            // adiciona jogador antes do jogador corrente
            if (j.getApelido().compareToIgnoreCase(apelido) > 0)
            {
                novoJogador = new JogadorOnLine(apelido, addr);
                jogadores.add(i, novoJogador);
        
                // informar para o jogador que enviou o pacote que eu estou online
                if(nMsg == 1)
                    enviarMensagemUDP(addr, 2, apelidoLocal);
                
                return;
            }
        }
        
        // insere jogador no final da lista
        novoJogador = new JogadorOnLine(apelido, addr);
        jogadores.addElement(novoJogador);
        
        // informar para o jogador que enviou o pacote que eu estou online
        if(nMsg == 1)
            enviarMensagemUDP(addr, 2, apelidoLocal);
    }
    
    // remove jogador da lista de jogadore online
    public void removeJogador(String apelido)
    {
        if(estaJogando && (apelido.compareToIgnoreCase(apelidoRemoto) == 0))
            encerrarConexaoTCP(JOGADOR_DESISTIU);
        
        // percorre toda a lista
        for(int i = 0; i < jogadores.size(); ++i)
        {
            // verifica se jogador foi encontrado
            if(jogadores.get(i).mesmoApelido(apelido))
            {
                jogadores.remove(i);
                return;
            }
        }
    }
    
    private InetAddress obtemInterfaceRede()
    {
        // verifica se usuário escolheu a interface
        int nInterface = interfacesJComboBox.getSelectedIndex();
        if(nInterface < 0)
            return null;

        // obtem interface selecionada pelo usuário
        String str = interfacesJComboBox.getItemAt(nInterface);
        String[] strParts = str.split(" - ");
        InetAddress addr;
        try {
            addr = InetAddress.getByName(strParts[0]);
        } catch (UnknownHostException ex)
        {
            return null;
        }
        
        return addr;
    }
    
    public void jogadorMeConvidou(String apelido, InetAddress addr)
    {
        // verifica se jogador local já está jogando
        String msg;
        if(estaJogando)
        {
            mostraMensagem(MSG_INFO, MSG_PROTO_NENHUM, addr.getHostAddress(),
                    -1, "Convite recusado automaticamente");
            
            // envia resposta automática recusando o convite
            msg = apelido + "|0";
            enviarMensagemUDP(addr, 5, msg);
            
            return;
        }
        
        // atualiza variáveis e controle
        fuiConvidado = true;
        statusJLabel.setText("");
        addrJogadorRemoto = null;
        
        // pergunta se jogador local aceita o convite
        msg = "O jogador " + apelido + " está te convidando para um jogo\nAceita?";
        int resp = JOptionPane.showConfirmDialog(this, msg, "Convite para jogar",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        
        // se jogador local recusar o convite, enviar
        // resposta de negação do convite
        if (resp == JOptionPane.NO_OPTION)
        {
            msg = apelidoLocal + "|0";
            enviarMensagemUDP(addr, 5, msg);
            mostraMensagem(MSG_INFO, MSG_PROTO_NENHUM, "", 0, "Convite não foi aceito");
            return;
        }
        
        // jogador local aceitou o convite. Criar servidor
        // TCP para jogador remoto se conectar
        servidorTCP = criarSocketTCP();
        if (servidorTCP == null)
        {
            JOptionPane.showMessageDialog(null,
                    "Erro na criação da conexão TCP.",
                    "Conexão do jogador remoto",
                    JOptionPane.ERROR_MESSAGE);
            
            // envia resposta de recusa do convite
            msg = apelidoLocal + "|0";
            enviarMensagemUDP(addr, 5, msg);
            statusJLabel.setText("");
            return;
        }
        
        // atualiza variáveis, cria thread para espera jogador remoto
        // conectar na conexão TCP criada
        addrJogadorRemoto = addr;
        apelidoRemoto = apelido;
        tcpEscutaThread = new EscutaTCP(this, servidorTCP, addr);
        tcpEscutaThread.execute();
            
        // envia resposta aceitando o convite
        msg = apelidoLocal + "|" + servidorTCP.getLocalPort();
        enviarMensagemUDP(addr, 5, msg);
        
        esperandoConexao = true;
        esperandoConfirmacao = true;
        esperandoInicioJogo = true;
        statusJLabel.setText("AGUARDANDO CONEXÃO");
        // DEBUG: timeoutAguardandoJogadorRemoto.start();
    }
    
    public void jogadorRemotoConectou(ConexaoTCP conexao)
    {
        esperandoConexao = false;
        this.conexaoTCP = conexao;
        servidorTCP = null;     // servidor TCP foi encerrado
        iniciarSerieJogos();
    }
    
    public void jogadorRemotoConfirmou(InetAddress addr)
    {
        // verifica se quem confirmou foi realmente o jogador remoto
        if (addr.equals(addrJogadorRemoto) == false)
            return;

        esperandoConfirmacao = false;
        
        iniciarSerieJogos();
    }
    
    // cria e abre um socket TCP em uma porta qualquer na interface indicada
    private ServerSocket criarSocketTCP()
    {
        InetAddress addr = obtemInterfaceRede();
        if(addr == null)
            return null;

        ServerSocket socket;
        try
        {
            // cria um socket para servidor TCP.
            // Parâmetros:
            //     porta: 0 (usar uma porta que será alocada automaticamente)
            //   backlog: 1 (no máximo uma única conexão)
            //  bindAddr: addr (InetAddress local que o servidor irá ligar)
            socket = new ServerSocket(0, 1, addr);
            socket.setReuseAddress(true);
        } catch (IOException e)
        {
            return null;
        }
        
        return socket;
    }
    
    /**
     * Encerra o socket e thread criados para gerenciar a
     * conexão TCP estabelecida entre os jogadores local
     * e remoto durante um jogo.
     * 
     * @param motivo
     * Indica o motivo do encerramento da conexão, a saber:
     *      <dl>
     *      <dt>0: timeout (jogador remoto não conectou)</dt>
     *      <dt>1: conexão caiu;</dt>
     *      <dt>2: jogador remoto desistiu do jogo;</dt>
     *      <dt>3: fim do jogo</dt>
     *      </dl>
     */
    // timeout de conexão com jogador remoto
    public void encerrarConexaoTCP(int motivo)
    {
        // se jogador estiver jogando, encerra a série
        if(estaJogando)
        {
            estaJogando = false;
            zeraResultados();
            limpaTabuleiro();
        }
        
        // obtem dados para mostrar na tabela de mensagens
        int portaRemota = 0;
        String enderecoRemoto = "";
        if ((conexaoTCP != null) && (conexaoTCP.getSocket() != null))
        {
            portaRemota = conexaoTCP.getSocket().getPort();
            if (conexaoTCP.getSocket().getRemoteSocketAddress() != null)
                enderecoRemoto = conexaoTCP.getSocket().getRemoteSocketAddress().toString();
        }
        
        // encerra servidor TCP (socket e thread)
        try
        {
            if(servidorTCP != null)
                servidorTCP.close();
            
            if(tcpEscutaThread != null)
                tcpEscutaThread.cancel(true);
        } catch(IOException ex)
        {
        }
        servidorTCP = null;
        tcpEscutaThread = null;
        
        // encerra conexão TCP
        if (conexaoTCP != null)
            conexaoTCP.cancel(true);
        
        conexaoTCP = null;
        
        if(motivo == CONEXAO_TIMEOUT)
            JOptionPane.showMessageDialog(null,
                    "TIMEOUT: aguardando conexão remota.",
                    "Encerrar jogo",
                    JOptionPane.WARNING_MESSAGE);
        
        if(motivo == CONEXAO_CAIU)
            JOptionPane.showMessageDialog(null,
                    "Conexão com jogador remoto caiu.",
                    "Encerrar jogo",
                    JOptionPane.WARNING_MESSAGE);
        
        if (motivo == JOGADOR_DESISTIU)
            JOptionPane.showMessageDialog(null,
                    "Jogador remoto desistiu do jogo.",
                    "Encerrar jogo",
                    JOptionPane.WARNING_MESSAGE);
        
        esperandoConexao = esperandoInicioJogo = false;
        esperandoConfirmacao = esperandoJogadorRemoto = false;
        
        posicoesJPanel.setBackground(Color.DARK_GRAY);
        
        statusJLabel.setText("");
        
        mostraMensagem(MSG_INFO, MSG_PROTO_TCP, enderecoRemoto, portaRemota, "Conexão foi encerrada.");
        mostraMensagem(MSG_INFO, MSG_PROTO_NENHUM, "", 0, "Fim do jogo");
    }
    
    private void iniciarSerieJogos()
    {
        if (esperandoConexao || esperandoConfirmacao)
            return;
        
        // encerra temporizador de timeout
        if (timeoutAguardandoJogadorRemoto.isRunning())
            timeoutAguardandoJogadorRemoto.stop();
        
        // atualiza tela
        jogadorRemotoJLabel.setText(apelidoRemoto + " - " + POSICAO_REMOTO);
        jogadorRemotoJLabel.setEnabled(true);
        
        if (fuiConvidado)
        {
            // envia mensagem MSG07 para jogador remoto
            int n = numAleatorio.nextInt(2) + 1;
            if (n == JOGADOR_LOCAL)
            {
                // jogador local irá iniciar o jogo
                minhaVez = inicieiUltimoJogo = true;
                statusJLabel.setText("SUA VEZ");
            }
            else
            {
                // jogador remoto irá iniciar o jogo
                minhaVez = inicieiUltimoJogo = false;
                statusJLabel.setText("ESPERANDO JOGADOR");
            }
            String compl = String.valueOf(n);
            conexaoTCP.enviarMensagemTCP(7, compl);
        }
        
        estaJogando = true;
        jogoAtual = 1;
        zeraResultados();

        limpaTabuleiro();
        
        placarLocalJLabel.setEnabled(true);
        placarRemotoJLabel.setEnabled(true);
    }
    
    private void limpaTabuleiro()
    {
        // limpa tabuleiro
        int pos = 0;
        for(int i = 0; i < 3; ++i)
        {
            for(int j = 0; j < 3; ++j)
            {
                tabuleiro[i][j] = POSICAO_VAZIA;
                switch(pos)
                {
                    case 0: pos1JLabel.setText(""); break;
                    case 1: pos2JLabel.setText(""); break;
                    case 2: pos3JLabel.setText(""); break;
                    case 3: pos4JLabel.setText(""); break;
                    case 4: pos5JLabel.setText(""); break;
                    case 5: pos6JLabel.setText(""); break;
                    case 6: pos7JLabel.setText(""); break;
                    case 7: pos8JLabel.setText(""); break;
                    case 8: pos9JLabel.setText(""); break;
                }
                ++pos;
            }
        }
    }
    
    public void zeraResultados()
    {
        String nomeRemoto;
        Color corTabuleiro;
        if (estaJogando)
        {
            nomeRemoto = apelidoRemoto + " - " + POSICAO_REMOTO;
            corTabuleiro = Color.BLACK;
        }
        else
        {
            nomeRemoto = "Remoto - " + POSICAO_REMOTO;
            corTabuleiro = Color.DARK_GRAY;
        }
        
        jogadorRemotoJLabel.setText(nomeRemoto);
        posicoesJPanel.setBackground(corTabuleiro);
        
        // limpa resultados de todos os jogos
        for(int i = 0; i < 5; ++i)
            resultados[i] = SEM_RESULTADO;
        mostraResultados();
        
        // limpa tabuleiro
        int pos = 0;
        for(int i = 0; i < 3; ++i)
        {
            for(int j = 0; j < 3; ++j)
            {
                tabuleiro[i][j] = POSICAO_VAZIA;
                switch(pos)
                {
                    case 0: pos1JLabel.setText(""); break;
                    case 1: pos2JLabel.setText(""); break;
                    case 2: pos3JLabel.setText(""); break;
                    case 3: pos4JLabel.setText(""); break;
                    case 4: pos5JLabel.setText(""); break;
                    case 5: pos6JLabel.setText(""); break;
                    case 6: pos7JLabel.setText(""); break;
                    case 7: pos8JLabel.setText(""); break;
                    case 8: pos9JLabel.setText(""); break;
                }
                ++pos;
            }
        }
        
        jogadorRemotoJLabel.setEnabled(estaJogando);
        placarLocalJLabel.setEnabled(estaJogando);
        placarRemotoJLabel.setEnabled(estaJogando);
    }
    
    public void mostraResultados()
    {
        javax.swing.JLabel ctrlLabel = null;
        Color cor;
        int local = 0, remoto = 0;
        for(int i = 0; i < 5; ++i)
        {
            switch(i)
            {
                case 0: ctrlLabel = jogo1JLabel; break;
                case 1: ctrlLabel = jogo2JLabel; break;
                case 2: ctrlLabel = jogo3JLabel; break;
                case 3: ctrlLabel = jogo4JLabel; break;
                case 4: ctrlLabel = jogo5JLabel; break;
            }
            
            cor = Color.DARK_GRAY;
            if (estaJogando)
            {
                if(((i + 1) == jogoAtual) && (resultados[i] == SEM_RESULTADO))
                    cor = Color.BLACK;
                else
                {
                    switch (resultados[i])
                    {
                        case VITORIA_LOCAL:
                            ++local;
                            cor = COR_LOCAL;
                            break;
                        case VITORIA_REMOTO:
                            ++remoto;
                            cor = COR_REMOTO;
                            break;
                        default:
                            // empate
                            cor = COR_EMPATE;
                            break;
                    }
                }
                
                ctrlLabel.setEnabled((i + 1) <= jogoAtual);
            }
            else
                ctrlLabel.setEnabled(false);
            ctrlLabel.setForeground(cor);
        }
        
        placarLocalJLabel.setText(String.valueOf(local));
        placarRemotoJLabel.setText(String.valueOf(remoto));
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(TabuleiroFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(TabuleiroFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(TabuleiroFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(TabuleiroFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new TabuleiroFrame().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField apelidoLocalJText;
    private javax.swing.JButton conectarJButton;
    private javax.swing.JButton convidarJButton;
    private javax.swing.JComboBox<String> interfacesJComboBox;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel11;
    private javax.swing.JPanel jPanel12;
    private javax.swing.JPanel jPanel13;
    private javax.swing.JPanel jPanel14;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JLabel jogadorLocalJLabel;
    private javax.swing.JLabel jogadorRemotoJLabel;
    private javax.swing.JList<JogadorOnLine> jogadoresJList;
    private javax.swing.JLabel jogo1JLabel;
    private javax.swing.JLabel jogo2JLabel;
    private javax.swing.JLabel jogo3JLabel;
    private javax.swing.JLabel jogo4JLabel;
    private javax.swing.JLabel jogo5JLabel;
    private javax.swing.JTable mensagensJTable;
    private javax.swing.JPanel messageJPanel;
    private javax.swing.JLabel placarLocalJLabel;
    private javax.swing.JLabel placarRemotoJLabel;
    private javax.swing.JLabel pos1JLabel;
    private javax.swing.JLabel pos2JLabel;
    private javax.swing.JLabel pos3JLabel;
    private javax.swing.JLabel pos4JLabel;
    private javax.swing.JLabel pos5JLabel;
    private javax.swing.JLabel pos6JLabel;
    private javax.swing.JLabel pos7JLabel;
    private javax.swing.JLabel pos8JLabel;
    private javax.swing.JLabel pos9JLabel;
    private javax.swing.JPanel posicoesJPanel;
    private javax.swing.JButton sairJButton;
    private javax.swing.JLabel statusJLabel;
    // End of variables declaration//GEN-END:variables
}
