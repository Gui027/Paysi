# Gestão de chaves de API e credenciais

**Responsável:** sócio-administrador de Tecnologia e Segurança da Informação
**Periodicidade de revisão:** trimestral e após qualquer incidente ou mudança de acesso

## Chave principal do Asaas

A chave de produção do Asaas e a chave mestra de criptografia da aplicação são
armazenadas exclusivamente como secrets protegidos `ASAAS_API_KEY` e
`MFA_ENCRYPTION_KEY_BASE64` no GitHub Environment `staging`. Elas não devem ser colocadas em
código, imagem Docker, artefato de CI, mensagem, planilha ou arquivo `.env`.

O workflow de deploy:

1. obtém a chave do cofre do GitHub somente durante a execução protegida;
2. transmite o valor pelo canal SSH, via entrada padrão;
3. mantém o valor apenas na memória do processo remoto;
4. injeta a variável somente no container do backend;
5. remove do servidor qualquer entrada legada `ASAAS_API_KEY` ou
   `MFA_ENCRYPTION_KEY_BASE64` existente no arquivo `/opt/paysi/.env`.

Desenvolvimento e testes não recebem a chave de produção. Quando uma chamada
real ao Sandbox for necessária, a chave de homologação deve ser criada para
esse ambiente e injetada apenas na sessão corrente do terminal. O padrão do
projeto continua sendo `PAYSI_PROVIDER=fake`.

## Controle de acesso

Somente o sócio-administrador responsável por Tecnologia e Segurança da
Informação pode cadastrar, substituir ou remover o secret no GitHub. O valor
não é disponibilizado aos frontends, aos testes, às imagens ou aos artefatos.
O backend é o único processo da aplicação que recebe a credencial em execução.

Os acessos ao GitHub, ao servidor e ao painel do Asaas são revisados
trimestralmente. A revisão registra data, responsável, sistemas conferidos,
usuários/permissões encontrados e providências adotadas.

## Rotação e suspeita de comprometimento

Em caso de suspeita de exposição:

1. desabilitar imediatamente a chave no painel do Asaas;
2. criar uma nova chave de produção com nome e validade identificáveis;
3. substituir o secret `ASAAS_API_KEY` no GitHub Environment `staging`;
4. executar o deploy e validar uma chamada autenticada e o health check;
5. excluir a chave antiga após confirmar o uso da nova;
6. revisar logs do Asaas, GitHub, servidor e aplicação, preservando evidências;
7. comunicar o time de Segurança do Asaas se houver possível uso indevido ou
   impacto financeiro.

A meta de contenção e substituição é de até uma hora após a identificação.

## Evidências

- configuração do GitHub Environment mostrando apenas o nome do secret, nunca
  seu valor;
- histórico protegido do workflow de deploy;
- registro trimestral de revisão de acessos;
- registro de rotação ou incidente, quando aplicável.
