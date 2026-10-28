import type { Metadata } from "next";
import { Footer } from "@/components/Footer";
import { Header } from "@/components/Header";

export const metadata: Metadata = {
  title: "Termos de Uso",
  description:
    "Termos de Uso da Paysi: o que a plataforma faz, quem presta os serviços financeiros (Asaas), como a Paysi é remunerada e os direitos e deveres de vendedores, afiliados e compradores.",
};

export default function TermosPage() {
  return (
    <>
      <Header base="/" />
      <main className="legal-page">
        <article className="site-container legal-doc">
          <h1>Termos de Uso</h1>
          <p className="legal-meta">Última atualização: 28 de outubro de 2026</p>

          <div className="legal-callout">
            <p>
              <strong>Em resumo:</strong> a Paysi é uma plataforma de software para vender produtos digitais. Ela{" "}
              <strong>não é banco, instituição financeira nem instituição de pagamento</strong>. Todos os serviços
              financeiros (cobranças, abertura de conta de pagamento, custódia do saldo e saques) são prestados pelo{" "}
              <strong>Asaas Gestão Financeira Instituição de Pagamento S.A.</strong>, CNPJ 19.540.550/0001-21 (“Asaas”).
            </p>
          </div>

          <h2>1. Quem somos</h2>
          <p>
            A Paysi é operada por <strong>Paysi Inova Simples (I.S.)</strong>, CNPJ 69.200.600/0001-08 (“Paysi”). Contato:{" "}
            <a href="mailto:comercial@paysi.com.br">comercial@paysi.com.br</a>.
          </p>

          <h2>2. O que a Paysi oferece</h2>
          <p>
            A Paysi oferece software para criação de produtos e ofertas, páginas de checkout, painel do vendedor,
            programa de afiliados, relatórios e integrações. Ao usar a plataforma você declara ter capacidade civil para
            contratar e concorda com estes Termos e com a <a href="/privacidade">Política de Privacidade</a>.
          </p>

          <h2>3. Quem presta os serviços financeiros</h2>
          <ul>
            <li>
              Os serviços financeiros ligados a vendas feitas na Paysi, como cobranças por Pix, boleto e cartão, conta de
              pagamento do vendedor, divisão de valores, repasses e saques, são prestados exclusivamente pelo Asaas, que
              é instituição de pagamento autorizada pelo Banco Central do Brasil.
            </li>
            <li>
              A conta de pagamento do vendedor é aberta no Asaas, em nome do próprio vendedor, e fica sujeita aos termos e
              às políticas do Asaas aceitos na abertura da conta.
            </li>
            <li>
              A Paysi não recebe, não guarda e não movimenta valores de vendedores ou compradores em conta própria. O
              valor das vendas é direcionado pelo Asaas à conta de pagamento do vendedor, descontadas as comissões
              previstas na seção 5.
            </li>
            <li>
              A Paysi não define nem cobra tarifas pelos serviços financeiros. Eventuais tarifas desses serviços são
              definidas e cobradas pelo Asaas, conforme os termos do Asaas.
            </li>
          </ul>

          <h2>4. Cadastro e verificação de identidade (KYC)</h2>
          <ul>
            <li>
              Para vender, o vendedor deve fornecer dados verdadeiros e completos e passar pela verificação de
              identidade exigida pela legislação e pelo Asaas. A aprovação é decisão do Asaas.
            </li>
            <li>
              Os documentos solicitados são enviados pelo painel da Paysi diretamente para a análise do Asaas. A Paysi não
              cobra do vendedor nenhuma taxa pela abertura da conta, pela verificação ou pela análise de documentos.
            </li>
            <li>
              Enquanto a verificação não for aprovada, o vendedor não pode publicar ofertas nem receber valores.
            </li>
            <li>O vendedor é responsável por manter seus dados atualizados e por proteger o acesso à sua conta.</li>
          </ul>

          <h2>5. Como a Paysi é remunerada</h2>
          <p>A Paysi cobra apenas pelo uso do software, de duas formas:</p>
          <ul>
            <li>
              <strong>Comissão sobre vendas aprovadas</strong>, recebida por divisão do pagamento (split) no momento da
              venda. Os percentuais e valores vigentes ficam disponíveis ao vendedor no painel, na aba “Taxas e Prazos”, e
              podem variar conforme o plano contratado.
            </li>
            <li>
              <strong>Mensalidade de plano de software</strong>, quando o vendedor contrata um plano pago, informada no
              painel antes da contratação.
            </li>
          </ul>
          <p>
            A Paysi <strong>não cobra tarifa de saque, de antecipação de recebíveis, de abertura ou manutenção de conta, nem
            qualquer outra tarifa por serviço financeiro em seu próprio nome</strong>. Mudanças em comissões ou planos
            serão comunicadas com antecedência razoável e valem para vendas e cobranças posteriores à comunicação.
          </p>

          <h2>6. Saques, prazos e reservas</h2>
          <ul>
            <li>
              Os saques são solicitados no painel e processados pelo Asaas para a conta bancária informada pelo vendedor,
              que deve ser de sua própria titularidade.
            </li>
            <li>
              Podem ser aplicados prazos de disponibilização do saldo e uma reserva de segurança destinada a cobrir
              reembolsos e contestações (chargebacks). Quando existirem, serão informados no painel, na aba “Taxas e
              Prazos”, e valerão para as vendas feitas depois de informados.
            </li>
            <li>
              Alterações de dados bancários e saques acima do limite configurado podem exigir confirmação adicional de
              segurança (autenticação em dois fatores).
            </li>
          </ul>

          <h2>7. Responsabilidades do vendedor</h2>
          <ul>
            <li>
              O vendedor é o único responsável pelos produtos que vende, pelo conteúdo das ofertas, pela entrega, pelo
              atendimento e suporte ao comprador, pela emissão de documentos fiscais e pelo recolhimento de tributos.
            </li>
            <li>
              É proibido vender produtos ou serviços ilegais, que violem direitos de terceiros, que enganem o comprador ou
              que sejam vedados pelas regras do Asaas e das bandeiras de cartão.
            </li>
            <li>
              Reembolsos e contestações de pagamento são deduzidos do saldo do vendedor, conforme as regras do meio de
              pagamento. Se o saldo for insuficiente, o vendedor poderá ficar com saldo devedor.
            </li>
            <li>
              A Paysi pode suspender ofertas, limitar funcionalidades ou encerrar contas diante de indícios de fraude,
              descumprimento destes Termos, exigência legal ou determinação do Asaas.
            </li>
          </ul>

          <h2>8. Afiliados</h2>
          <p>
            O afiliado divulga produtos de terceiros mediante comissão definida pelo vendedor no programa de afiliados. A
            comissão é paga pelo vendedor, por divisão do pagamento no momento da venda, e depende de o afiliado ter
            conta aprovada no Asaas. A Paysi não garante volume de vendas nem ganhos. É proibido divulgar de forma
            enganosa, praticar spam ou usar cadastros falsos.
          </p>

          <h2>9. Compradores</h2>
          <ul>
            <li>
              O contrato de compra é firmado entre o comprador e o vendedor. A Paysi fornece apenas a tecnologia do
              checkout.
            </li>
            <li>
              O pagamento é processado pelo Asaas. A Paysi não armazena os dados completos do cartão do comprador.
            </li>
            <li>
              Nas compras feitas pela internet, o comprador tem direito de arrependimento em até 7 (sete) dias a contar da
              compra ou do recebimento, nos termos do art. 49 do Código de Defesa do Consumidor, além da garantia
              informada pelo vendedor na oferta. Os pedidos de reembolso são tratados pelo vendedor e processados pelo
              Asaas.
            </li>
            <li>
              Dúvidas sobre o produto, a entrega ou a cobrança devem ser dirigidas primeiro ao vendedor, cujos dados de
              contato constam da oferta ou do e-mail da compra.
            </li>
          </ul>

          <h2>10. Propriedade intelectual</h2>
          <p>
            A marca Paysi, o software e o conteúdo da plataforma pertencem à Paysi. O conteúdo das ofertas pertence ao
            vendedor, que concede à Paysi licença limitada para exibi-lo no checkout e no painel. O vendedor responde por
            ter os direitos sobre o que publica.
          </p>

          <h2>11. Disponibilidade e limitação de responsabilidade</h2>
          <p>
            A Paysi empenha-se em manter a plataforma disponível e segura, mas não garante funcionamento ininterrupto. A
            Paysi não responde por falhas ou indisponibilidade dos serviços financeiros prestados pelo Asaas, nem por
            danos decorrentes de uso indevido da conta, de informações incorretas fornecidas pelo usuário ou dos produtos
            vendidos pelos vendedores, ressalvadas as hipóteses em que a lei impuser responsabilidade.
          </p>

          <h2>12. Privacidade</h2>
          <p>
            O tratamento de dados pessoais segue a <a href="/privacidade">Política de Privacidade</a> e a Lei Geral de
            Proteção de Dados (Lei nº 13.709/2018).
          </p>

          <h2>13. Alterações destes Termos</h2>
          <p>
            Estes Termos podem ser atualizados. A versão vigente fica sempre nesta página, com a data de atualização.
            Mudanças relevantes serão comunicadas por e-mail ou no painel, e o uso da plataforma após a comunicação
            representa concordância com a nova versão.
          </p>

          <h2>14. Lei aplicável e foro</h2>
          <p>
            Estes Termos são regidos pelas leis brasileiras. Nas relações de consumo, fica eleito o foro do domicílio do
            consumidor. Nos demais casos, o foro da comarca da sede da Paysi.
          </p>

          <h2>15. Contato</h2>
          <p>
            Dúvidas sobre estes Termos: <a href="mailto:comercial@paysi.com.br">comercial@paysi.com.br</a>.
          </p>
        </article>
      </main>
      <Footer base="/" />
    </>
  );
}
