/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jogovelha;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import javax.swing.SwingWorker;

/**
 *
 * @author willi
 */
public class EscutaTCP extends SwingWorker<Boolean, String> {
    private final TabuleiroFrame mainFrame;
    private final ServerSocket socket;
    private final InetAddress addrRemoto;
    
    public EscutaTCP(TabuleiroFrame mainFrame, ServerSocket socket,
                     InetAddress addrRemoto)
    {
        this.mainFrame = mainFrame;
        this.socket = socket;
        this.addrRemoto = addrRemoto;
    }
    
    @Override
    protected Boolean doInBackground() throws Exception {
        try
        {
            while(true)
            {
                // espera conexão
                Socket connection = socket.accept();
                
                // verifica se quem conectou foi o jogador remoto
                if (connection.getInetAddress().equals(addrRemoto) == false)
                {
                    // não aceitar a conexão
                    connection.close();
                    
                    mainFrame.mostraMensagem(TabuleiroFrame.MSG_IN,
                            TabuleiroFrame.MSG_PROTO_TCP,
                            connection.getRemoteSocketAddress().toString(),
                            connection.getPort(), 
                            "Tentativa de conexão na porta " + socket.getLocalPort());
                }
                else
                {
                    // cria conexão com cliente
                    ConexaoTCP novaConexao = new ConexaoTCP(mainFrame, connection); 
                    
                    // processa as comunicações com o cliente
                    novaConexao.execute();
                    
                    // informa à interface gráfica que o jogador remoto conectou
                    mainFrame.jogadorRemotoConectou(novaConexao);
                    
                    // encerra escuta na porta (servidor para uma única conexão)
                    return true;
                }
            }
        }catch (IOException ex)
        {
            return false;
        }
    }
    
    public void encerraConexao()
    {
        try
        {
            if (socket.isClosed() == false)
                socket.close();
        } catch (IOException ex)
        {
        }
    }
}
