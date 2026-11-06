# Respostas propostas — Checklist de Segurança Asaas

**Data da análise:** 06/11/2026
**Regra:** usar as respostas condicionadas somente depois de publicar as
alterações e guardar a primeira evidência de execução.

## Respostas já sustentadas pelo projeto e pelas políticas

### 1.1.2 — Sim

A Paysi realiza revisão trimestral dos acessos aos sistemas críticos, incluindo
Asaas, GitHub, servidor, e-mail e DNS. O responsável técnico confere usuários,
perfis e necessidade de acesso, registra a revisão e revoga acessos sem
justificativa. Em desligamento ou mudança de função, contas, sessões, tokens e
chaves relacionados são bloqueados ou ajustados imediatamente.

### 2.1.1 — Sim, após cadastrar os secrets e publicar o novo deploy

A chave da API do Asaas é mantida como secret protegido do ambiente de deploy,
fora do código, imagens, planilhas, mensagens e arquivos `.env`. Somente o
workflow autorizado e o backend recebem a credencial em memória durante a
execução. O repositório não contém o valor da chave e o procedimento de rotação
e revogação está documentado.

### 3.1.1 — Sim

Produção opera em servidor e stack próprios, com banco, rede e credenciais de
produção separados. Desenvolvimento e testes são executados localmente com
provedor fake ou Sandbox do Asaas e não recebem credenciais nem dados reais de
produção.

### 3.1.3 — Sim, após publicar a migração e os workflows

As ações administrativas registram usuário, ação, alvo, motivo, estado
anterior/posterior, IP e data/hora em trilha append-only. Mudanças de código e
produção também ficam vinculadas ao commit e à execução autenticada do CI/CD,
permitindo identificar autoria, conteúdo e momento da alteração.

### 4.1.1 — Sim

A Paysi mantém política formal de proteção de dados e criptografia abrangendo
aplicação, banco, arquivos, backups, logs, integrações e fornecedores. A
política define classificação, minimização, acesso, criptografia, retenção,
descarte, revisão e responsabilidades sobre dados pessoais e financeiros.

### 4.1.2 — Sim

Os acessos públicos e integrações externas utilizam HTTPS com TLS 1.2 ou
superior. A comunicação com o Asaas usa exclusivamente o endpoint HTTPS do
ambiente correspondente. Banco, Redis e RabbitMQ permanecem na rede privada da
stack e não publicam portas no host.

### 4.1.4 — Sim, após cadastrar os secrets e publicar o novo deploy

Chaves e credenciais de produção são mantidas em cofre de secrets com acesso
restrito e injetadas somente no processo que necessita delas. Há procedimento
documentado para criação, revisão trimestral, rotação anual, troca de
responsável e revogação imediata em suspeita de exposição, com registro das
evidências.

### 5.1.1 — Sim, após publicar e obter a primeira execução bem-sucedida

Endpoints e saúde da aplicação são verificados automaticamente a cada 15
minutos. O monitoramento confere serviços, containers, backup recente e uso de
disco, além dos controles de integridade financeira da aplicação. Falhas abrem
alerta operacional com link para a execução e permanecem registradas até a
normalização.

### 5.1.2 — Sim

As operações administrativas registram quem executou, quando, origem, ação,
alvo, justificativa e estados anterior e posterior. O histórico de código e de
deploy complementa essa rastreabilidade para alterações da plataforma.

### 6.1.1 — Sim

A Paysi possui procedimento escrito de resposta a incidentes com classificação,
responsáveis, preservação de evidências, contenção, erradicação, recuperação,
comunicação e análise de causa raiz. O procedimento é revisado após incidente
ou mudança relevante.

### 6.1.2 — Sim

O procedimento prevê comunicar o Asaas imediatamente pelos canais oficiais
quando a integração, credenciais, dados ou operação BaaS puderem ser afetados.
Também prevê avaliação e comunicação à ANPD e aos titulares nos prazos legais,
com registro da decisão, conteúdo, destinatários e horário de cada aviso.

### 7.1.1 — Sim, após publicar o workflow de segurança

O processo combina revisão de dependências, análise estática, busca de segredos
e configurações inseguras, varredura de imagens e teste dinâmico da aplicação.
Achados são registrados, classificados por severidade, atribuídos e
acompanhados até a correção e nova validação.

### 7.1.2 — Sim, após a primeira execução bem-sucedida

São executados semanalmente CodeQL, Trivy e OWASP ZAP, além das verificações em
push e pull request. Dependências Maven, npm, GitHub Actions e Docker são
revisadas semanalmente pelo Dependabot. Uma avaliação independente mais ampla
é prevista ao menos anualmente ou após mudança crítica.

### 7.1.3 — Sim

Cada vulnerabilidade é registrada com severidade, responsável e prazo. Os
prazos máximos são: crítica em 24 horas, alta em 7 dias, média em 30 dias e
baixa em 90 dias. A correção só é encerrada após nova validação ou aceite formal
e temporário do risco.

### 7.1.4 — Sim

Falhas críticas e altas têm prioridade sobre o desenvolvimento normal. Críticas
devem ser contidas ou corrigidas em até 24 horas e altas em até 7 dias, com
revogação de credenciais, bloqueio de função ou outro controle compensatório
imediato quando a correção definitiva não puder ser aplicada no prazo.

### 8.1.1 — Sim

Existe processo documentado para contratar e conceder acesso a fornecedores.
Antes do acesso são registrados finalidade, dados e sistemas acessados,
criticidade, autenticação, criptografia, continuidade, retenção, suboperadores
e capacidade de notificação, aplicando-se menor privilégio.

### 8.1.2 — Sim para novas contratações

Antes de contratar ou liberar acesso, o responsável avalia riscos de segurança
e privacidade, localização e suboperadores, histórico de incidentes, controles
de acesso, criptografia, continuidade e eliminação dos dados. O risco residual
e a decisão ficam registrados no inventário do fornecedor.

### 9.1.1 — Sim

A Paysi possui plano de continuidade e recuperação. As metas operacionais
atuais são RPO de 24 horas, RTO de 4 horas em horário comercial e 8 horas fora
dele. O procedimento inclui contenção, seleção e validação do backup,
restauração isolada, verificações do esquema e razão financeiro e promoção
somente após validação técnica.

### 9.1.3 — Sim

Restaurações são programadas trimestralmente em ambiente isolado e registram
backup utilizado, horários, hashes, contagens e verificações de integridade. Um
ciclo completo de queda, restauração do PostgreSQL e conferência dos dados foi
executado e documentado em 18/09/2026; o mesmo modelo será usado nas execuções
seguintes.

### 10.1.1 — Sim

A Paysi mantém regras escritas de desenvolvimento seguro aplicáveis desde a
concepção. Elas abrangem minimização de dados, autenticação e menor privilégio,
validação de entradas, consultas parametrizadas, criptografia padronizada,
proteção de segredos, HTTPS, tratamento de erros, idempotência e auditoria de
operações financeiras, segregação de ambientes e testes de segurança. Dados de
cartão são tratados somente de forma transitória no fluxo de tokenização e não
são persistidos pela Paysi.

### 10.1.2 — Sim, após publicar o workflow de segurança

Antes de uma alteração chegar à produção, o CI executa testes automatizados,
lint, validações de arquitetura e verificações contra exposição de dados. Uma
varredura bloqueante do Trivy verifica dependências, segredos e configurações.
CodeQL, OWASP ZAP e Dependabot complementam a análise contínua. A publicação só
ocorre depois da conclusão bem-sucedida do CI; achados críticos ou altos
bloqueiam a liberação até correção ou mitigação formalmente registrada.

## Não marcar “Sim” ainda

### 3.1.2 — Não se aplica

A Paysi possui atualmente um único mantenedor técnico, portanto não existe uma
segunda pessoa que possa aprovar a publicação. As mudanças são rastreadas no
Git e só seguem após CI e verificações automatizadas. Quando houver outro
operador habilitado, alterações críticas passarão a exigir revisão e aprovação
por pessoa distinta.

Para transformar em “Sim”, é necessário nomear uma segunda pessoa competente e
configurar proteção da branch e do ambiente exigindo sua aprovação.

### 4.1.3 — Não, por enquanto

Campos restritos já usam AES-GCM, senhas usam Argon2id e segredos não devem ser
persistidos em texto claro, mas ainda falta comprovar criptografia do volume de
produção e ativar a cópia externa cifrada. Só marcar “Sim” após obter evidência
do provedor e executar restauração do backup cifrado.

### 5.1.3 e 5.1.4 — Não, por enquanto

As trilhas administrativas do banco são append-only e a política define seis
meses para logs de segurança, porém ainda falta arquivo central externo com
retenção imutável que também impeça alteração por administradores e aplique o
prazo de forma verificável. Só marcar “Sim” após ativar o destino WORM/Object
Lock, enviar os logs e testar leitura/restauração.

### 8.1.3, 8.1.4 e 8.1.5 — Não, até revisar os contratos

A política exige cláusulas de segurança, proteção de dados e credenciais,
notificação rápida de incidentes e controle de subcontratação. Ela não altera
contratos já assinados. É necessário revisar e arquivar contrato/DPA de cada
fornecedor crítico e obter aditivo quando alguma cláusula estiver ausente.

### 8.1.6 — Não, até completar o inventário

O inventário-base existe, mas ainda é necessário registrar a razão social dos
provedores de VPS e DNS, serviços prestados, acessos efetivos, contrato/DPA,
responsável e data da última revisão. Depois disso, a revisão será trimestral.

### 9.1.2 — Não, por enquanto

O backup diário local e sua validação já existem, mas a cópia externa separada
ainda depende da criação do bucket, retenção imutável, credenciais criptografadas
do serviço e uma execução/restauração bem-sucedida. O script e o procedimento
para ativação já estão preparados.
