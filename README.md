# JogoVelhaSocket
Esse programa é uma versão que eu implementei para o projeto final dos alunos do curso de Engenharia de Software da Universidade Federal de Goiás como projeto final da disciplina de Desenvolvimento de Software Concorrente. O objetivo é a construção de um jogo da velha que permita que dois jogadores remotos, em uma mesma rede local, possam jogar uma série de melhor de 5 partidas. Para que os programas remotos possam se comunicar, foi definido o protocolo de comunicação descrito abaixo.
<br>
<h1>Protocolo de Comunicação</h1>
As mensagens trocadas entre os programas serão sempre no formato texto (ASCII), onde cada mensagem possui um cabeçalho de tamanho fixo e uma parte variável, que irá depender do tipo da mensagem trocada entre os dispositivos. O cabeçalho de cada mensagem possui tamanho fixo (5 caracteres) e é formado pelos seguintes campos:
<ul>
<li><b>ID</b>: 2 dígitos que identifica o tipo da mensagem</li>
<li><b>Tamanho</b>: 3 dígitos que indica o número de caracteres (tamanho) total da mensagem, incluindo os 5 dígitos do cabeçalho.</li>
</ul>
<br>
<h3>Mensagens trocadas antes do Jogo</h3>
Mensagens UDP (datagramas, sem conexão prévia entre os computadores) trocadas entre o programa e demais computadores com o objetivo de saber quem está online e fazer o convite para participar de um jogo. O programa irá utilizar a porta <b>20181</b> (uma referência para 2018/1 - primeiro semestre de 2018, quando esse projeto foi proposto) para escutar estas mensagens. Essa porta deverá ficar disponível para troca de mensagens, mesmo quando o jogador estiver participando de um jogo. O tipo e formato das mensagens estão descritos a seguir:
<br>
<dl>
<dt><h4>Mensagem “01” (MSG01): saber quem está online.</h4></dt>
  <dd><b>Formato da Mensagem: <u>01999Apelido</u></b>
  <nl>
    <li><b>01</b>: identificação da mensagem</li>
    <li><b>999</b>: tamanho total da mensagem, incluindo o cabeçalho</li>
    <li><b>Apelido</b>: nome no qual você deseja ser identificado. É o nome do jogador que está enviando a mensagem.</li>
  </nl>
</dd>
<dd>Essa mensagem será enviada via broadcast para a porta 20181 em duas situações:
  <ol>
    <li>quando você iniciar o programa, indicando que você fez o login;</li>
    <li>sempre que desejar saber quais são os jogadores que estão online no momento.</li>
  </ol>
É aconselhável que essa mensagem seja enviada periodicamente (em intervalos nunca inferiores a 3 minutos) para atualizar a lista de jogadores que estão online, pois pode ocorrer algum problema no computador de algum jogador e ele ficar off-line sem avisar aos demais jogadores. Ao receber essa mensagem, o programa deverá guardar o apelido e IP do jogador (conforme explicado na <b>MSG02</b>), e responderá com uma <b>MSG02</b> se identificando, permitindo que seja montada uma lista de jogadores que estão online. Essa lista deverá ser usada para convidar jogadores para a disputa de um jogo.
</dd>
<br>
<dt><h4>Mensagem “02” (MSG02): resposta à MSG01 informando que você está online.</h4></dt>
<dd><b>Formato da Mensagem: <u>02999Apelido</u></b>
<nl>
  <li><b>02</b>: identificação da mensagem</li>
  <li><b>999</b>: tamanho total da mensagem, incluindo o cabeçalho</li>
  <li><b>Apelido</b>: identificação do jogador que está enviando a mensagem</li>
</nl></dd>
<dd>Essa mensagem é uma resposta automática à mensagem MSG01. Essa mensagem deverá ser enviada somente para quem enviou a mensagem MSG01, onde o jogador corrente estará se identificando, mostrando que está online. Ao receber as mensagens MSG02 (bem como a MSG01) o programa deverá atualizar a lista de jogadores que estão online. Essa lista conterá o apelido e IP de cada jogador (o IP pode ser obtido através do campo RemoteAddress existente no socket que estiver escutando a porta 20181), para que o jogador corrente possa fazer um convite a algum desses jogadores para participar de um jogo. Jogadores que estiverem na lista e não responderem à sua mensagem MSG01 deverão ser retirados da lista.
</dd>
<br>
<dt><h4>Mensagem “03” (MSG03): programa foi encerrado.</h4></dt>
<dd><b>Formato da Mensagem: <u>03999Apelido</u></b>
  <nl>
    <li><b>03</b>: identificação da mensagem</li>
    <li><b>999</b>: tamanho total da mensagem, incluindo o cabeçalho</li>
    <li><b>Apelido</b>: identificação do jogador que está enviando a mensagem</li>
  </nl>
</dd>
<dd>Essa mensagem será enviada via broadcasting indicando que você encerrou o programa e está off-line a partir desse instante. Ao receber as mensagens MSG03, o programa deverá atualizar a lista de jogadores que estão online.</dd>
<br>
<dt><h4>Mensagem “04” (MSG04): convite para participar de um jogo.</h4></dt>
<dd><b>Formato da Mensagem: <u>04999Apelido</u></b>
  <nl>
    <li><b>04</b>: identificação da mensagem</li>
    <li><b>999</b>: tamanho total da mensagem</li>
    <li><b>Apelido</b>: identificação do jogador que está enviando a mensagem</li>
  </nl>
</dd>
<dd>Essa mensagem será enviada para um jogador específico que estiver na sua lista de jogadores online. O recebimento dessa mensagem indica que o jogador (Apelido) está te convidando para um jogo. Lembrando que o jogo será uma melhor de 5 partidas.</dd>
<br>
<dt><h4>Mensagem “05” (MSG05): resposta à MSG04 indicando se convite foi aceito ou não.</h4></dt>
<dd><b>Formato da Mensagem: <u>05999Apelido|Porta</u></b>
  <nl>
    <li><b>05</b>: identificação da mensagem</li>
    <li><b>999</b>: tamanho total da mensagem</li>
    <li><b>Apelido</b>: identificação do jogador que está enviando a mensagem</li>
    <li><b>|</b>: separador de campo</li>
    <li><b>Porta</b>: caso o convite tenha sido aceito, número da porta TCP que será utilizada para fazer a conexão ponto-a-ponto (peer-to-peer). Caso não queira (ou não possa) aceitar o convite, o número da porta deverá ser 0 (zero) indicando que o você não quer jogar ou que já está jogando com outro jogador (nesse caso, a resposta deverá ser automática).</li>
  </nl>
</dd>
<dd>Essa mensagem será enviada somente para quem te enviou a MSG04. Ao aceitar um convite, o programa deverá abrir um socket TCP que ficará escutando a porta informada na mensagem, esperando que o jogador que fez o convite conecte com você para iniciar o jogo.</dd>
<br>
<dt><h4>Mensagem “06” (MSG06): resposta à MSG05 indicando que jogador recebeu a resposta ao convite.</h4></dt>
<dd><b>Formato da Mensagem: <u>06007Ok</u></b>
  <nl>
    <li><b>06</b>: identificação da mensagem</li>
    <li><b>007</b>: tamanho total da mensagem (tamanho fixo)</li>
    <li><b>Ok</b>: resposta fixa</li>
  </nl>
</dd>
<dd>Essa mensagem será enviada automaticamente pelo jogador que fez o convite para o jogo, informando ao jogador que foi convidado que ele recebeu a resposta do convite e, caso o convite tenha sido aceito, irá fazer a conexão TCP na porta indicada para iniciar o jogo. Como a resposta ao convite foi feita usando mensagens UDP, essa resposta pode não ser lida pelo jogador que fez o convite. Dessa forma, o jogador que recebeu o convite terá certeza que a conexão TCP será feita. Caso a MSG06 não chegue dentro de um certo intervalo de tempo (timeout), o jogador que recebeu o convite poderá, ou enviar uma nova resposta, ou não se preparar para iniciar o jogo (não abrir uma porta para conexão TCP).</dd>

<h3>Mensagens trocadas durante o Jogo</h3>
Mensagens TCP (com conexão prévia entre os computadores) trocadas entre os dois jogadores participantes do jogo. Essa conexão deverá ser feita pelo jogador que fez o convite, logo após o envio da mensagem MSG06.
<br>
<dt><h4>Mensagem “07” (MSG07): início do jogo.</h4></dt>
<dd><b>Formato da Mensagem: <u>07006N</u></b>
  <nl>
    <li><b>07</b>: identificação da mensagem</li>
    <li><b>006</b>: tamanho total da mensagem (tamanho fixo)</li>
    <li><b>N</b>: indica quem vai iniciar o jogo</li>
  </nl>
</dd>
<dd>Essa mensagem é enviada pelo jogador que recebeu o convite, logo após quem convidou fazer a conexão TCP. O campo N indica quem irá iniciar o jogo e pode ser os seguintes valores:
  <ol>
    <li>quem recebeu o convite irá iniciar o jogo (no caso, o jogador que enviou essa mensagem)</li>
    <li>quem convidou irá iniciar o jogo (no caso, o jogador que recebeu essa mensagem)</li>
  </ol>
Uma vez recebido essa mensagem, o jogo se inicia.</dd>
<br>
<dt><h4>Mensagem “08” (MSG08): jogador fez uma jogada, escolhendo uma posição no tabuleiro.</h4></dt>
<dd><b>Formato da Mensagem: <u>08006N</u></b>
  <nl>
    <li><b>08</b>: identificação da mensagem</li>
    <li><b>006</b>: tamanho total da mensagem (tamanho fixo)</li>
    <li><b>N</b>: indica a posição que o jogador escolheu no tabuleiro</li>
  </nl>
</dd>
<dd>Essa mensagem é enviada pelo jogador que possui a vez, imediatamente após ele escolher uma posição no tabuleiro. No tabuleiro, cada posição é numerada de 1 a 9, conforme mostrado na figura abaixo. Ao receber a mensagem, o programa deverá atualizar o tabuleiro e a vez passa ao jogador que recebeu a mensagem.
<b>
  <br>1"&emsp;"2"&emsp;"3
  <br>4"&emsp;"5"&emsp;"6
  <br>7"&emsp;"8"&emsp;"9
</b>
</dd>
<br>
<dt><h4>Mensagem “09” (MSG09): início de uma nova partida.</h4></dt>
<dd><b>Formato da Mensagem: <u>09005</u></b>
  <nl>
    <li><b>09</b>: identificação da mensagem</li>
    <li><b>005</b>: tamanho total da mensagem (tamanho fixo)</li>
  </nl>
</dd>
<dd>Como cada jogador sabe quando o jogo encerrou (com alguém vencendo ou, no caso de todas as posições do tabuleiro serem preenchidas, havendo empate), não é necessário indicar o fim do jogo. Entretanto, ao encerrar uma partida, uma nova partida deverá ser iniciada, caso ainda nenhum jogador tenha alcançado a vitória na melhor de 5. Essa mensagem deverá ser enviada por quem perdeu a última partida. Em caso de empate, mensagem deverá ser enviada por quem não começou a partida anterior (que acabou de ser encerrada). Quem envia essa mensagem irá começar a nova partida, podendo escolher uma posição para jogar, enviando, logo após, uma mensagem MSG08. Quando houver um ganhador definitivo na melhor de 5 partidas (3ª vitória de algum dos jogadores), ou então um empate entre os jogadores (encerrou-se a 5ª partida e nenhum jogador teve mais vitórias que o outro), a conexão será automaticamente encerrada e, para participar de um novo jogo, deverá ser feito um novo convite.</dd>
<br>
<dt><h4>Mensagem “10” (MSG10): desistência de continuar jogando.</h4></dt>
<dd><b>Formato da Mensagem: <u>10005</u></b>
  <nl>
    <li><b>10: identificação da mensagem</li>
    <li><b>005</b>: tamanho total da mensagem (tamanho fixo)</li>
  </nl>
</dd>
<dd>Essa mensagem deverá ser enviada pelo jogador que, por algum motivo, desistiu de continuar jogando. Nesse caso, logo após o envio da mensagem, o jogador deverá encerrar a conexão.</dd>
</dl>
