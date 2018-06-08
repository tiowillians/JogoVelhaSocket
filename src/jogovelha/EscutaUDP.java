/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jogovelha;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import javax.swing.SwingWorker;

/**
 *
 * @author willi
 */
public class EscutaUDP extends SwingWorker<Void, String>  {
    private TabuleiroFrame mainFrame;   // frame principal do programa
    private String apelidoLocal;        // apelido do jogador local
    private DatagramSocket udpSocket;
    private int porta;
    private InetAddress addrLocal;

    public EscutaUDP(TabuleiroFrame mainFrame, int porta,
                     String apelidoLocal, InetAddress addr) throws SocketException
    {
        this.mainFrame = mainFrame;
        this.porta = porta;
        this.apelidoLocal = apelidoLocal;
        this.addrLocal = addr;
        udpSocket = new DatagramSocket(porta, addr);
        udpSocket.setReuseAddress(true);

    }

    @Override
    protected Void doInBackground() throws Exception {
        // escuta porta
        String msg;

        while (true)
        {
            byte[] buf = new byte[256];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            // bloqueia até que um pacote seja lido
            try {
                udpSocket.receive(packet);
            } catch (IOException ex)
            {
                mainFrame.mostraMensagem(TabuleiroFrame.MSG_IN,
                        TabuleiroFrame.MSG_PROTO_UDP,
                        packet.getAddress().getHostAddress(),
                        packet.getPort(), ex.getMessage());
                continue;
            }
            
            // obtém dados
            msg = new String(packet.getData()).trim();
            
            // mostra mensagem recebida
            mainFrame.mostraMensagem(TabuleiroFrame.MSG_IN,
                        TabuleiroFrame.MSG_PROTO_UDP,
                        packet.getAddress().getHostAddress(),
                        packet.getPort(), msg);
            
            // tamanho mínimo da mensagem: 5 caracteres
            if (msg.length() < 5)
            {
                mainFrame.mostraMensagem(TabuleiroFrame.MSG_IN,
                        TabuleiroFrame.MSG_PROTO_UDP,
                        packet.getAddress().getHostAddress(),
                        packet.getPort(), "Mensagem inválida [" + msg + "]");
                continue;
            }
            
            // processa mensagem
            int tam = Integer.parseInt(msg.substring(2, 5));
            if (msg.length() != tam)
            {
                mainFrame.mostraMensagem(TabuleiroFrame.MSG_IN,
                        TabuleiroFrame.MSG_PROTO_UDP,
                        packet.getAddress().getHostAddress(),
                        packet.getPort(),
                        "Erro: tamanho da mensagem [" + msg + "]");
                continue;
            }

            // não processar mensagens enviadas pelo jogador local (via broadcast)
            if(packet.getAddress().equals(addrLocal))
                continue;
            
            String complemento = "";
            if (tam > 5)
                complemento = msg.substring(5);
            
            int nMsg = Integer.parseInt(msg.substring(0, 2));
            switch(nMsg)
            {
                case 1:
                case 2:
                    mainFrame.adicionaJogador(nMsg, complemento, packet.getAddress()); break;
                    
                case 3:
                    mainFrame.removeJogador(complemento);
                    break;
                    
                case 4:
                    mainFrame.jogadorMeConvidou(complemento, packet.getAddress());
                    break;
                    
                case 5:
                    mainFrame.respostaConvite(complemento, packet.getAddress());
                    break;
                    
                case 6:
                    mainFrame.jogadorRemotoConfirmou(packet.getAddress());
                    break;
                    
                default:
                    mainFrame.mostraMensagem(TabuleiroFrame.MSG_IN,
                                TabuleiroFrame.MSG_PROTO_UDP,
                                packet.getAddress().getHostAddress(),
                                packet.getPort(),
                                "Mensagem inválida [" + msg + "]");
            }
        }
    }
    
    public void encerraConexao()
    {
        if (udpSocket.isConnected())
            udpSocket.disconnect();
        
        udpSocket.close();
    }
}
