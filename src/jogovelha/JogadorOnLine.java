/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jogovelha;

import java.net.InetAddress;

/**
 *
 * @author willi
 */
public class JogadorOnLine {
    private final String apelido;
    private final InetAddress addr;
    private int portaTCP;
    private boolean aindaOnline;     // indica se jogador ainda está online

    public JogadorOnLine(String apelido, InetAddress addr)
    {
        this.apelido = apelido;
        this.addr = addr;
        this.portaTCP = 0;
        this.aindaOnline = true;
    }
    
    public String getApelido()
    {
        return this.apelido;
    }
    
    public InetAddress getAddress()
    {
        return this.addr;
    }
    
    public int getPorta()
    {
        return this.portaTCP;
    }
    
    public void setPorta(int porta)
    {
        this.portaTCP = porta;
    }
    
    public void setAindaOnline(boolean b)
    {
        this.aindaOnline = b;
    }
    
    public boolean getAindaOnline()
    {
        return this.aindaOnline;
    }

    public boolean mesmoApelido(String apelido) {
        return (this.apelido.compareToIgnoreCase(apelido) == 0);
    }
}
