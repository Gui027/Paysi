import type { Metadata } from "next";
import { Footer } from "@/components/Footer";
import { Header } from "@/components/Header";

export const metadata: Metadata = {
  title: "Política de Privacidade",
  description:
    "Como a Paysi coleta, usa, compartilha e protege dados pessoais de vendedores, afiliados e compradores, e como exercer seus direitos na LGPD.",
};

export default function PrivacidadePage() {
  return (
    <>
      <Header base="/" />
      <main className="legal-page">
        <article className="site-container legal-doc">
          <h1>Política de Privacidade</h1>
          <p className="legal-meta">Última atualização: 28 de outubro de 2026</p>

          <p>
            Esta política explica como a <strong>Paysi Inova Simples (I.S.)</strong>, CNPJ 69.200.600/0001-08 (“Paysi”),
            trata dados pessoais, em conformidade com a Lei Geral de Proteção de Dados (Lei nº 13.709/2018, “LGPD”). Ela
            vale para vendedores, afiliados e compradores que usam a plataforma. Leia também os{" "}
            <a href="/termos">Termos de Uso</a>.
          </p>

          <h2>1. Quem trata os dados</h2>
          <ul>
            <li>
              A <strong>Paysi</strong> é controladora dos dados necessários para operar a plataforma (conta, painel,
              checkout, antifraude e suporte).
            </li>
            <li>
              O <strong>Asaas Gestão Financeira Instituição de Pagamento S.A.</strong>, CNPJ 19.540.550/0001-21, presta os
              serviços financeiros e é controlador independente dos dados que trata para abrir contas de pagamento,
              processar cobranças e cumprir a regulação financeira. O tratamento feito pelo Asaas segue a política de
              privacidade dele.
            </li>
            <li>
              O <strong>vendedor</strong> é controlador dos dados dos seus próprios clientes, usados para entregar o
              produto e dar suporte.
            </li>
          </ul>

          <h2>2. Quais dados tratamos</h2>
          <ul>
            <li>
              <strong>Vendedores e afiliados:</strong> nome, CPF ou CNPJ, e-mail, telefone, endereço, data de nascimento,
              renda ou faturamento informados, dados bancários para saque e os documentos exigidos na verificação de
              identidade.
            </li>
            <li>
              <strong>Compradores:</strong> nome, CPF ou CNPJ, e-mail, telefone, endereço e dados da compra. Os dados do
              cartão são enviados ao Asaas para tokenização, e a Paysi não armazena os dados completos do cartão.
            </li>
            <li>
              <strong>Dados técnicos:</strong> endereço IP, dispositivo, navegador, datas e horários de acesso e registros
              de uso, usados para segurança e prevenção a fraudes.
            </li>
          </ul>

          <h2>3. Para que usamos</h2>
          <ul>
            <li>Criar e manter a conta, executar o contrato e prestar o serviço contratado.</li>
            <li>Processar vendas, comissões, reembolsos e contestações.</li>
            <li>Verificar identidade, prevenir fraudes e lavagem de dinheiro e cumprir obrigações legais e regulatórias.</li>
            <li>Dar suporte e enviar comunicações sobre a conta e as compras.</li>
            <li>Melhorar a plataforma, com dados agregados sempre que possível.</li>
          </ul>
          <p>
            As bases legais são a execução de contrato, o cumprimento de obrigação legal ou regulatória, o legítimo
            interesse (segurança e prevenção a fraudes) e, quando necessário, o consentimento.
          </p>

          <h2>4. Com quem compartilhamos</h2>
          <ul>
            <li>
              <strong>Asaas</strong>, para abertura de conta, verificação de identidade, cobranças e repasses.
            </li>
            <li>
              <strong>Vendedor</strong>, que recebe os dados do comprador necessários para entregar o produto e dar
              suporte.
            </li>
            <li>
              <strong>Prestadores de serviço</strong> de infraestrutura, e-mail e segurança, sob contrato e com dever de
              confidencialidade.
            </li>
            <li>
              <strong>Autoridades</strong>, quando houver obrigação legal ou ordem judicial.
            </li>
          </ul>
          <p>A Paysi não vende dados pessoais.</p>

          <h2>5. Por quanto tempo guardamos</h2>
          <p>
            Guardamos os dados pelo tempo necessário para as finalidades acima e para cumprir prazos legais, fiscais e
            regulatórios e para o exercício regular de direitos. Depois disso, os dados são eliminados ou anonimizados.
          </p>

          <h2>6. Segurança</h2>
          <p>
            Usamos medidas técnicas e organizacionais para proteger os dados, como conexão criptografada, controle de
            acesso, registro de operações sensíveis, criptografia de credenciais e autenticação em dois fatores para
            operações críticas. Nenhum sistema é totalmente imune a falhas; em caso de incidente relevante, comunicaremos
            os afetados e a autoridade competente nos termos da lei.
          </p>

          <h2>7. Seus direitos</h2>
          <p>
            Você pode, a qualquer momento, solicitar: confirmação de que tratamos seus dados; acesso; correção;
            anonimização, bloqueio ou eliminação de dados desnecessários; portabilidade; informação sobre
            compartilhamentos; revogação do consentimento; e revisão de decisões automatizadas. Algumas solicitações
            podem ser limitadas por obrigação legal de guarda.
          </p>
          <p>
            Para exercer seus direitos, escreva para <a href="mailto:comercial@paysi.com.br">comercial@paysi.com.br</a>.
            Se tiver dúvidas sobre o tratamento feito pelo vendedor, fale com ele diretamente.
          </p>

          <h2>8. Cookies</h2>
          <p>
            Usamos cookies e tecnologias semelhantes essenciais ao funcionamento, à autenticação e à segurança. Você pode
            gerenciá-los nas configurações do navegador; desativar os essenciais pode impedir o uso do painel e do
            checkout.
          </p>

          <h2>9. Menores de idade</h2>
          <p>A plataforma não se destina a menores de 18 anos, e não coletamos dados deles de forma intencional.</p>

          <h2>10. Alterações</h2>
          <p>
            Esta política pode ser atualizada. A versão vigente fica nesta página, com a data de atualização. Mudanças
            relevantes serão comunicadas por e-mail ou no painel.
          </p>
        </article>
      </main>
      <Footer base="/" />
    </>
  );
}
