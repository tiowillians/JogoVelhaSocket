/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jogovelha;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import javax.swing.SwingWorker;

/**
 *
 * @author willi
 */
public class ConexaoTCP extends SwingWorker<Boolean, String> {
    private final TabuleiroFrame mainFrame;
    private final Socket socket;
    
    // leitura dos dados
    private InputStream entrada;  
    private InputStreamReader inr;  
    private BufferedReader bfr;
    
    // envio dos dados
    private OutputStream saida;  
    private OutputStreamWriter outw;  
    private BufferedWriter bfw;      
    
    public Socket getSocket()
    {
        return socket;
    }
    
    public ConexaoTCP (TabuleiroFrame mainFrame, Socket socket)
    {
        this.mainFrame = mainFrame;
        this.socket = socket;
        try
        {
            entrada  = this.socket.getInputStream();
            inr = new InputStreamReader(entrada, "ISO-8859-1");
            bfr = new BufferedReader(inr);
            
            saida =  this.socket.getOutputStream();
            outw = new OutputStreamWriter(saida, "ISO-8859-1");
            bfw = new BufferedWriter(outw); 
        }
        catch (IOException e)
        {
            mainFrame.mostraMensagem(TabuleiroFrame.MSG_ERRO,
                            TabuleiroFrame.MSG_PROTO_TCP,
                            socket.getRemoteSocketAddress().toString(),
                            socket.getPort(),
                            "Erro: criação da nova conexão");
        } 
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        String msg;
        while(true)
        {
            try
            {
                msg = (String)bfr.readLine();
                if (msg != null)
                {
                    // tamanho mínimo da mensagem: 5 caracteres
                    if (msg.length() < 5)
                    {
                        mainFrame.mostraMensagem(TabuleiroFrame.MSG_IN,
                                TabuleiroFrame.MSG_PROTO_TCP,
                                socket.getRemoteSocketAddress().toString(),
                                socket.getPort(),
                                "Mensagem inválida [" + msg + "]");
                        continue;
                    }

                    // processa mensagem
                    int tam = Integer.parseInt(msg.substring(2, 5));
                    if (msg.length() != tam)
                    {
                        mainFrame.mostraMensagem(TabuleiroFrame.MSG_IN,
                                TabuleiroFrame.MSG_PROTO_TCP,
                                socket.getRemoteSocketAddress().toString(),
                                socket.getPort(),
                                "Erro: tamanho da mensagem [" + msg + "]");
                        continue;
                    }

                    // mostra mensagem recebida
                    mainFrame.mostraMensagem(TabuleiroFrame.MSG_IN,
                                TabuleiroFrame.MSG_PROTO_TCP,
                                socket.getRemoteSocketAddress().toString(),
                                socket.getPort(), msg);

                    // não processar mensagens enviadas pelo jogador local (via broadcast)
                    String complemento = "";
                    if(tam > 5)
                        complemento = msg.substring(5);

                    int pos;
                    int nMsg = Integer.parseInt(msg.substring(0, 2));
                    switch(nMsg)
                    {
                        case 7:
                            // indica quem vai começar o jogo
                            // complemento: quem vai começar o jogo (1 ou 2)
                            pos = Integer.parseInt(complemento);
                            if((pos == 1) || (pos == 2))
                                mainFrame.quemIniciaJogo(pos);
                            break;
                        
                        case 8:
                            // jogador remoto jogou
                            // complemento: posição escolhida no tabuleiro (de 1 a 9)
                            pos = Integer.parseInt(complemento);
                            if((pos > 0) && (pos < 10))
                                mainFrame.marcaPosicao(TabuleiroFrame.JOGADOR_REMOTO, pos);
                            break;
                        
                        case 9:
                            // início de uma nova partida: sem complemento
                            mainFrame.jogadorRemotoIniciaNovoJogo();
                            break;
                        
                        case 10: 
                            mainFrame.encerrarConexaoTCP(TabuleiroFrame.JOGADOR_DESISTIU);
                            break;
                            
                        default:
                            mainFrame.mostraMensagem(TabuleiroFrame.MSG_IN,
                                    TabuleiroFrame.MSG_PROTO_TCP,
                                    socket.getRemoteSocketAddress().toString(),
                                    socket.getPort(),
                                    "Mensagem inválida [" + msg + "]");
                    }
                }
                else
                {
                    // encerra atributos de comunicação
                    bfr.close();
                    inr.close();  
                    entrada.close();  
                    bfw.close();
                    outw.close();  
                    saida.close();  
                    socket.close();
                    
                    mainFrame.encerrarConexaoTCP(TabuleiroFrame.CONEXAO_CAIU);
                    
                    Thread.currentThread().stop();
                }
            }
            catch(IOException ex)
            {
                // mostra mensagem de erro
                mainFrame.mostraMensagem(TabuleiroFrame.MSG_IN,
                        TabuleiroFrame.MSG_PROTO_TCP,
                        socket.getRemoteSocketAddress().toString(),
                        socket.getPort(), ex.getMessage());
                return false;
            }
        }
    }
    
    public boolean enviarMensagemTCP(int numero, String compl)
    {
        String msg = "";
        try
        {
            if((compl == null) || compl.isEmpty())
                msg = String.format("%02d005", numero);
            else
                msg = String.format("%02d%03d%s", numero, 5 + compl.length(),
                                    compl);

            outw.write(msg + "\n");
            outw.flush();
            
            // mostra mensagem enviada
            mainFrame.mostraMensagem(TabuleiroFrame.MSG_OUT,
                            TabuleiroFrame.MSG_PROTO_TCP,
                            socket.getRemoteSocketAddress().toString(),
                            socket.getPort(), msg);
            
            return true;
        }catch(IOException ex)
        {
            mainFrame.mostraMensagem(TabuleiroFrame.MSG_OUT,
                    TabuleiroFrame.MSG_PROTO_TCP,
                    socket.getRemoteSocketAddress().toString(),
                    socket.getPort(),
                    "Erro: envio da mensagem [" + msg + "]");
            return false;
        }
    }}
