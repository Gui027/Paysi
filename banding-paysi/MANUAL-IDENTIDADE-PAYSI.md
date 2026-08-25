# Paysi — Manual de Identidade Visual

**Versão 1.0** · Base normativa: arte *"Paysi — Identidade Visual"* (direção aprovada)
Assinatura verbal: *Paysi — Pagamentos inteligentes para o seu negócio.*

---

## Como ler este manual

| Marcação | Significado |
|---|---|
| **[ARTE]** | Definido na direção aprovada. Não alterar sem nova aprovação. |
| **[EXT]** | Extensão derivada, criada aqui para cobrir o que a arte não especifica (estados, rampas tonais, tema escuro, regras de aplicação). Segue a lógica da arte e pode ser ajustada. |

Tudo que a arte define está preservado ao pé da letra. As extensões existem porque uma arte de direção mostra a marca, mas não é suficiente para operar produto, campanha e material impresso sem decisões arbitrárias por parte de quem aplica.

---

## 1. Conceito

O símbolo une a inicial **P** de Paysi à silhueta de uma **maquininha / terminal POS**. A haste do "P" é o corpo do aparelho; a bojo é a face com display e teclado. **[ARTE]**

Isso ancora três leituras ao mesmo tempo:

1. **Categoria** — reconhecimento imediato do universo de pagamentos, sem precisar de explicação.
2. **Inicial** — a marca continua legível como letra, o que sustenta o símbolo isolado como app icon.
3. **Ponto de contato** — a maquininha é onde o lojista encontra a Paysi todo dia. O símbolo é o produto.

**Atributos de expressão** **[EXT]**: direto, confiável, moderno sem ser frio, feito para o comércio de bairro tanto quanto para a rede. A geometria arredondada e o azul saturado carregam isso; nada na identidade deve empurrar a marca para "banco tradicional" nem para "fintech agressiva".

---

## 2. Assinatura

### 2.1 Assinatura principal — horizontal **[ARTE]**

Símbolo à esquerda, logotipo à direita, alinhados pelo eixo óptico. É a versão padrão. Na dúvida, use esta.

`paysi-logo-horizontal.svg`

### 2.2 Construção **[EXT — medido a partir da arte]**

A unidade de construção é a **altura de capitular (H)** do logotipo — a altura do "P".

| Relação | Valor |
|---|---|
| Altura do símbolo | **1,58 H** |
| Proporção do símbolo (L : A) | **0,72 : 1** (72 × 100) |
| Respiro entre símbolo e logotipo | **0,43 H** |
| Alinhamento vertical | centro do símbolo = centro da banda capitular→linha de base |

Essas proporções estão travadas dentro dos arquivos SVG. **Nunca reposicione ou redimensione os elementos separadamente** — mova a assinatura como um bloco único.

### 2.3 Área de proteção **[EXT]**

**X = altura do símbolo ÷ 4.** Reserve X livre nos quatro lados da assinatura. Nada entra nessa margem: texto, imagem, borda, dobra ou recorte.

```
┌───────────────────────────────┐
│              X                │
│   ┌───────────────────────┐   │
│ X │  [símbolo]   Paysi    │ X │
│   └───────────────────────┘   │
│              X                │
└───────────────────────────────┘
```

Em peças apertadas (rodapé de nota fiscal, tarja de patrocínio), X pode cair para metade — nunca a zero.

### 2.4 Versões

| Versão | Arquivo | Quando usar |
|---|---|---|
| Horizontal positiva **[ARTE]** | `paysi-logo-horizontal.svg` | Padrão, sobre fundo claro |
| Horizontal negativa **[ARTE]** | `paysi-logo-horizontal-negativo.svg` | Fundo escuro ou de alto contraste |
| Horizontal monocromática **[EXT]** | `paysi-logo-horizontal-mono.svg` | Impressão em 1 cor, gravação, bordado, fax/carimbo. Usa `currentColor` |
| Vertical **[EXT]** | `paysi-logo-vertical.svg` | Espaços estreitos e altos: banner lateral, sacola, adesivo de vitrine |
| Símbolo isolado **[ARTE]** | `paysi-simbolo.svg` | Quando o nome já aparece no contexto: avatar, favicon, selo, marca d'água |
| Símbolo em `currentColor` **[EXT]** | `paysi-simbolo-currentcolor.svg` | Uso em código, herda a cor do elemento pai |
| App icon **[ARTE]** | `paysi-app-icon.svg` / `paysi-app-icon-azul.svg` | Loja de apps, atalho, favicon grande |
| Favicon **[EXT]** | `paysi-favicon.svg` | 32 px e abaixo, com área útil ampliada |
| Logotipo isolado **[EXT]** | `paysi-logotipo.svg` | Casos raros de co-branding em linha de texto |

Os furos do símbolo (display e teclas) são **transparentes**, não brancos. Isso faz o símbolo funcionar sobre qualquer fundo sem gerar retângulos brancos indesejados.

### 2.5 Tamanhos mínimos **[EXT]**

| Aplicação | Assinatura horizontal | Símbolo isolado |
|---|---|---|
| Digital | 96 px de largura | 24 px de largura |
| Impresso | 24 mm de largura | 8 mm de largura |
| Gravação / relevo | 32 mm | 12 mm |

Abaixo de 96 px de largura, troque a assinatura horizontal pelo símbolo isolado — o logotipo fecha e vira mancha.

### 2.6 Usos incorretos **[EXT]**

Não faça:

- Recolorir símbolo ou logotipo fora da paleta oficial
- Aplicar gradiente, sombra projetada, contorno, bisel ou brilho
- Distorcer, esticar, condensar ou inclinar
- Rotacionar em qualquer ângulo
- Trocar a fonte do logotipo ou redigitar "Paysi" com fonte de sistema — o logotipo está em curvas, use o arquivo
- Alterar o respiro entre símbolo e logotipo
- Colocar sobre foto sem contraste suficiente ou sobre padrão visualmente ruidoso
- Encaixar a assinatura dentro de caixa, pílula ou moldura não prevista
- Usar o símbolo azul sobre fundo azul de baixo contraste
- Escrever "PAYSI", "paysi" ou "PaySi" em texto corrido — a grafia é **Paysi**

### 2.7 Aplicação sobre fotografia **[EXT]**

Use a versão negativa e garanta contraste mínimo de **4,5:1** entre o branco da assinatura e a área da foto sob ela. Se a foto for irregular, aplique um véu escuro sobre a região (`#14181F` a 40–60%) em vez de mover o logo para um canto claro por acaso.

---

## 3. Cores

### 3.1 Paleta oficial **[ARTE]**

| Nome | HEX | RGB | HSL | Papel |
|---|---|---|---|---|
| **Primária** | `#1D6BD8` | 29, 107, 216 | 215°, 76%, 48% | Cor da marca. Ações primárias, símbolo, destaques |
| **Pressionado** | `#1858B4` | 24, 88, 180 | 215°, 76%, 40% | Estado `:active`, links sobre fundo claro |
| **Secundária** | `#7FAFEE` | 127, 175, 238 | 214°, 77%, 72% | Preenchimentos, gráficos, apoio |
| **Texto principal** | `#14181F` | 20, 24, 31 | 218°, 22%, 10% | Títulos e corpo de texto |
| **Texto secundário** | `#5C6472` | 92, 100, 114 | 218°, 11%, 40% | Legendas, apoio, metadados |
| **Linha** | `#E6E9EF` | 230, 233, 239 | 220°, 22%, 92% | Divisores, bordas, contornos de campo |
| **Fundo suave** | `#F7F9FC` | 247, 249, 252 | 216°, 45%, 98% | Fundo de seção, cards de baixo destaque |

A paleta inteira vive num eixo azul frio (214–220°). Nenhuma cor destoa de temperatura — é o que dá a coesão da arte. Extensões devem respeitar isso.

### 3.2 Proporção de uso **[EXT]**

Regra prática **60 / 30 / 10**:

- **60%** neutros claros — branco e `#F7F9FC`
- **30%** texto e estrutura — `#14181F`, `#5C6472`, `#E6E9EF`
- **10%** azul de marca — `#1D6BD8` e derivados

O azul é o acento, não o fundo. Quando tudo é azul, nada é ação.

### 3.3 Contraste e acessibilidade **[EXT — verificado]**

Razões de contraste medidas (WCAG 2.1):

| Combinação | Razão | Veredito |
|---|---|---|
| `#14181F` sobre branco | **17,79:1** | AAA para qualquer tamanho |
| `#14181F` sobre `#F7F9FC` | **16,87:1** | AAA |
| `#5C6472` sobre branco | **5,96:1** | AA para texto normal |
| `#1D6BD8` sobre branco | **5,06:1** | AA para texto normal |
| `#1D6BD8` sobre `#F7F9FC` | **4,80:1** | AA para texto normal |
| Branco sobre `#1D6BD8` | **5,06:1** | AA — botão primário aprovado |
| Branco sobre `#1858B4` | **6,79:1** | AA com folga |
| `#7FAFEE` sobre branco | **2,27:1** | **Reprovado.** Nem texto, nem borda funcional |
| `#7FAFEE` sobre `#14181F` | **7,85:1** | AAA — é aqui que a Secundária brilha |
| `#E6E9EF` sobre branco | **1,22:1** | Só decorativo. Divisor não pode carregar informação sozinho |

Três regras que saem daí:

1. **`#7FAFEE` nunca recebe texto sobre fundo claro** e nunca é a única marcação de um estado. Use-a como preenchimento, série de gráfico ou destaque sobre fundo escuro.
2. **Para links e texto pequeno em azul sobre fundo claro, prefira `#1858B4`** (6,79:1) em vez da Primária (5,06:1). A Primária continua correta em botões e áreas maiores.
3. **Nenhum estado é comunicado só por cor.** Aprovado / recusado / pendente sempre trazem ícone ou rótulo junto — parte relevante do público lojista tem deficiência de visão de cores, e vermelho/verde é justamente o eixo mais afetado.

### 3.4 Rampa tonal **[EXT]**

Escala completa gerada a partir dos âncoras da arte. Necessária para produto: hover, fundo de alerta, série de gráfico, borda de foco.

**Azul** — 300 = Secundária, 500 = Primária, 600 = Pressionado

| 50 | 100 | 200 | 300 | 400 | 500 | 600 | 700 | 800 | 900 |
|---|---|---|---|---|---|---|---|---|---|
| `#F0F5FD` | `#DCE8FA` | `#BBD2F6` | `#7FAFEE` | `#4A8AE3` | `#1D6BD8` | `#1858B4` | `#144890` | `#12386C` | `#0F2A4E` |

**Neutra** — 25 = Fundo suave, 100 = Linha, 500 = Texto secundário, 900 = Texto principal

| 0 | 25 | 50 | 100 | 200 | 300 | 400 | 500 | 600 | 700 | 800 | 900 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `#FFFFFF` | `#F7F9FC` | `#F1F4F9` | `#E6E9EF` | `#D3D8E2` | `#B4BCCA` | `#8B94A5` | `#5C6472` | `#474E5C` | `#343A46` | `#23282F` | `#14181F` |

### 3.5 Cores de estado **[EXT]**

A arte não define estados, e um produto de pagamentos não opera sem eles — a tela mais importante da Paysi é a que diz se a venda passou.

| Estado | Cor | Superfície | Contraste sobre branco | Uso |
|---|---|---|---|---|
| Sucesso | `#067A55` | `#E8F6F0` | 5,35:1 | Pagamento aprovado, transferência concluída |
| Atenção | `#8A5A00` | `#FCF3E3` | 5,93:1 | Pendência, antecipação em análise, prazo próximo |
| Erro | `#C0362E` | `#FDEDEB` | 5,51:1 | Transação recusada, estorno, maquininha offline |
| Informação | `#1858B4` | `#EAF1FC` | 6,79:1 | Avisos neutros, dicas, novidades |

Todas dessaturadas o suficiente para conviver com o azul da marca sem competir com ele, e todas acima de 4,5:1 sobre branco e sobre a própria superfície.

### 3.6 Tema escuro **[EXT]**

Derivado da *Versão negativa* da arte. Dois ajustes obrigatórios:

- **Fundo `#14181F`, superfície elevada `#1C222C`.** Não use preto puro.
- **A Primária clareia para `#3B82E8`** em fundo escuro. `#1D6BD8` sobre `#14181F` dá 3,51:1 — insuficiente para texto.

Valores completos em `tokens/paysi-tokens.css`, seletor `[data-tema="escuro"]`.

---

## 4. Tipografia

### 4.1 Família **[ARTE]**

**Segoe UI / Inter / SF-like** — uma grotesca neutra de tela, com terminais retos e altura-x generosa.

Pilha recomendada:

```css
font-family: "Inter", "Segoe UI", -apple-system, BlinkMacSystemFont,
             "SF Pro Display", Roboto, "Helvetica Neue", Arial, sans-serif;
```

A ordem entrega Inter onde estiver disponível, cai para a fonte nativa do sistema (Segoe UI no Windows, SF no Apple) e mantém a mesma métrica visual nos três casos. **Inter é a referência normativa** — é a única das três licenciável para uso livre (SIL OFL) em web, app e impresso.

### 4.2 Pesos **[ARTE]**

**600 · 500 · 400.** Três pesos, e só.

| Peso | Papel |
|---|---|
| **600 Semibold** | Títulos, rótulos, valores em destaque, botões |
| **500 Medium** | Rótulos de campo, legendas com ênfase, tabs |
| **400 Regular** | Corpo de texto, descrições, listas |

Peso **700** existe apenas dentro do logotipo, já vetorizado. Não use 700 em interface — quebra a hierarquia contra o 600.

Nunca use itálico como recurso de ênfase; use peso ou cor.

### 4.3 Escala tipográfica **[EXT]**

| Estilo | Tamanho | Entrelinha | Peso | Tracking |
|---|---|---|---|---|
| Display | 48 px | 1,1 | 700¹ | −0,02em |
| H1 | 32 px | 1,2 | 600 | −0,015em |
| H2 | 24 px | 1,3 | 600 | −0,01em |
| H3 | 20 px | 1,4 | 600 | 0 |
| Corpo grande | 18 px | 1,6 | 400 | 0 |
| Corpo | 16 px | 1,5 | 400 | 0 |
| Apoio | 14 px | 1,5 | 400 | 0 |
| Legenda | 12 px | 1,4 | 500 | 0 |
| Rótulo | 12 px | 1,2 | 600 | +0,08em, caixa alta |

¹ Display em 700 é permitido em peça publicitária e capa; em interface, use 600.

O tracking negativo cresce com o corpo — texto grande fecha, texto pequeno abre. É o comportamento da própria arte no título "Paysi — Identidade Visual".

Corpo mínimo em produto: **14 px**. Em impresso: **8 pt**.

### 4.4 Números e valores monetários **[EXT]**

Crítico para uma marca de pagamentos. Toda cifra em tabela, extrato, recibo ou dashboard usa **algarismos tabulares**:

```css
font-variant-numeric: tabular-nums;
font-feature-settings: "tnum" 1, "zero" 1;
```

Sem isso, colunas de R$ desalinham e o extrato parece quebrado. A classe `.paysi-valor` já resolve isso nos tokens.

Formato brasileiro sempre: `R$ 1.234,56` — espaço após o cifrão, ponto no milhar, vírgula no decimal.

---

## 5. Sistema de interface **[EXT]**

Derivado da própria arte: cantos arredondados generosos, sombras curtas e frias, contornos de 1 px em `#E6E9EF`.

### 5.1 Raio de canto

| Token | Valor | Onde |
|---|---|---|
| `--raio-xs` | 4 px | Tags, checkbox |
| `--raio-sm` | 8 px | Input, select, botão pequeno |
| `--raio-md` | 12 px | Botão padrão, card interno |
| `--raio-lg` | 16 px | Card, modal, painel |
| `--raio-xl` | 24 px | Card de destaque, app icon reduzido |
| `--raio-total` | 999 px | Pílula, avatar, chip de status |

### 5.2 Espaçamento

Grade de **4 px**: 4, 8, 12, 16, 20, 24, 32, 40, 48, 64, 80, 96. Nenhum valor fora da escala.

### 5.3 Elevação

Sombras curtas, de baixa opacidade e tonalizadas com o `#14181F` da marca — nunca preto puro.

| Token | Valor |
|---|---|
| `--sombra-sm` | `0 1px 2px rgba(20,24,31,.06)` |
| `--sombra-md` | `0 2px 8px rgba(20,24,31,.08)` |
| `--sombra-lg` | `0 8px 24px rgba(20,24,31,.10)` |
| `--sombra-xl` | `0 16px 48px rgba(20,24,31,.12)` |
| `--anel-foco` | `0 0 0 3px rgba(29,107,216,.32)` |

### 5.4 Foco e alvo de toque

Todo alvo interativo tem foco visível (`--anel-foco`) e área mínima de **44 × 44 px**. Vale especialmente para o app do lojista, usado em pé, com uma mão, no balcão.

### 5.5 Movimento

120 ms para microinterações, 200 ms para transições de estado, 320 ms para entrada de painel. Curva padrão `cubic-bezier(0.2, 0, 0.2, 1)`. `prefers-reduced-motion` zera as durações — já implementado nos tokens.

---

## 6. Tom de voz **[EXT]**

O público é lojista, não analista financeiro.

**Como escrever**

- Fale do lado de quem usa: *"Seu dinheiro cai amanhã"*, não *"Liquidação em D+1"*.
- Verbo ativo e direto. Botão diz o que acontece: **Cobrar**, não *Prosseguir*.
- A mesma ação mantém o mesmo nome do começo ao fim do fluxo. O botão **Antecipar** gera o aviso **Antecipação solicitada**.
- Erro explica o que houve e o que fazer: *"Cartão recusado pelo banco emissor. Peça outra forma de pagamento."* Erro não pede desculpas nem some no vago.
- Tela vazia é convite: *"Nenhuma venda hoje ainda. Faça a primeira cobrança."*
- Frase curta. Sem jargão de adquirência voltado para fora.

**O que evitar**

Sem exclamação em série, sem "incrível", sem gíria forçada, sem tratar o lojista como iniciante. Números sempre explícitos: **taxa de 1,99%**, não "as menores taxas do mercado".

---

## 7. Arquivos entregues

```
paysi-brand/
├── MANUAL-IDENTIDADE-PAYSI.md         este documento
├── paysi-brandbook.html               versão visual navegável, arquivo único
├── assets/
│   ├── paysi-logo-horizontal.svg
│   ├── paysi-logo-horizontal-negativo.svg
│   ├── paysi-logo-horizontal-mono.svg
│   ├── paysi-logo-vertical.svg
│   ├── paysi-logotipo.svg
│   ├── paysi-simbolo.svg
│   ├── paysi-simbolo-currentcolor.svg
│   ├── paysi-app-icon.svg
│   ├── paysi-app-icon-azul.svg
│   └── paysi-favicon.svg
└── tokens/
    ├── paysi-tokens.css               variáveis CSS, claro e escuro
    ├── paysi-tokens.json              formato W3C Design Tokens
    └── paysi.tailwind.preset.js       preset Tailwind
```

**Sobre os vetores.** Todos os SVGs foram reconstruídos em curvas a partir da arte aprovada. O logotipo "Paysi" está **convertido em contornos** (Inter Display Bold, licença SIL OFL) — não depende de fonte instalada e renderiza igual em qualquer ambiente. O símbolo é um único `path` com `fill-rule="evenodd"`, então display e teclas são furos transparentes, não formas brancas.

---

## 8. O que ainda falta decidir

Pontos que a arte não cobre e que valem uma rodada antes de escalar:

1. **Ilustração e iconografia.** Não há sistema de ícones definido. Recomendo grade de 24 px, traço de 1,5 px, cantos arredondados coerentes com o símbolo.
2. **Fotografia.** Nenhuma direção definida. A categoria pede lojista real em ambiente real, luz natural, sem banco de imagem genérico.
3. **Padrão gráfico de apoio.** O teclado do símbolo é um ativo óbvio para virar textura/pattern em cartão, sacola e fundo de peça. Vale explorar.
4. **Licenciamento da tipografia.** Se a Paysi for imprimir com Segoe UI, é preciso checar licença Microsoft. Padronizar em Inter (OFL) elimina esse risco.
5. **Assinatura vertical e co-branding.** A vertical aqui é proposta; falta validar. Regras de convívio com marcas parceiras (bandeiras, adquirentes) ainda não existem.
6. **Registro.** Vale a busca de anterioridade e o depósito de marca no INPI nas classes de serviços financeiros e software, se ainda não foi feito.

---

*Paysi — Pagamentos inteligentes para o seu negócio.*
Manual v1.0 · Dúvidas de aplicação: consulte antes de improvisar.
