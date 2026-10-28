import { PAINEL_BASE_URL } from "@/lib/urls";

export function Footer({ base = "" }: { base?: string }) {
  return (
    <footer className="site-footer">
      <div className="site-container">
        <div className="footer-grid">
          <div className="footer-brand">
            <img className="site-logo" src="/paysi-logo.svg" alt="Paysi" />
            <p>Pagamentos inteligentes e checkout de alta conversão para o seu negócio.</p>
          </div>
          <div className="footer-col">
            <h4>Navegue</h4>
            <ul>
              <li><a href={`${base}#recursos`}>Recursos</a></li>
              <li><a href={`${base}#contato`}>Contato</a></li>
              <li><a href={`${PAINEL_BASE_URL}/entrar`}>Painel</a></li>
              <li><a href="https://checkout.paysi.com.br">Checkout</a></li>
            </ul>
          </div>
          <div className="footer-col">
            <h4>Siga nas redes</h4>
            <ul>
              <li><span>Instagram — em breve</span></li>
              <li><span>LinkedIn — em breve</span></li>
            </ul>
          </div>
          <div className="footer-col">
            <h4>Contato e legal</h4>
            <ul>
              <li><a href="mailto:comercial@paysi.com.br">comercial@paysi.com.br</a></li>
              <li><a href="/termos">Termos de Uso</a></li>
              <li><a href="/privacidade">Política de Privacidade</a></li>
            </ul>
          </div>
        </div>
        <div className="footer-legal">
          <img
            className="asaas-seal"
            src="https://baas.asaas.com/selos/Servicos_financeiros_Asaas-Reduzida-Positivo.svg?id=a9d026e7-160f-4d17-8e71-14e274efb29b"
            alt="Serviços financeiros Asaas"
          />
          <p>
            A Paysi é uma plataforma de software e não é instituição financeira nem instituição de pagamento autorizada
            pelo Banco Central. Os serviços financeiros (cobranças, contas e repasses) são prestados pelo Asaas Gestão
            Financeira Instituição de Pagamento S.A., CNPJ 19.540.550/0001-21.
          </p>
        </div>
        <div className="footer-bottom">
          <span>© {new Date().getFullYear()} Paysi Inova Simples (I.S.) · CNPJ 69.200.600/0001-08. Todos os direitos reservados.</span>
        </div>
      </div>
    </footer>
  );
}
