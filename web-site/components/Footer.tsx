export function Footer() {
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
              <li><a href="#recursos">Recursos</a></li>
              <li><a href="#contato">Contato</a></li>
              <li><a href="https://app.paysi.com.br">Painel</a></li>
              <li><a href="https://checkout.paysi.com.br">Checkout</a></li>
            </ul>
          </div>
          <div className="footer-col">
            <h4>Siga nas redes</h4>
            <ul>
              <li><a href="#">Instagram</a></li>
              <li><a href="#">LinkedIn</a></li>
            </ul>
          </div>
          <div className="footer-col">
            <h4>Contato</h4>
            <ul>
              <li><a href="mailto:comercial@paysi.com.br">comercial@paysi.com.br</a></li>
            </ul>
          </div>
        </div>
        <div className="footer-bottom">
          <span>© {new Date().getFullYear()} Paysi. Todos os direitos reservados.</span>
        </div>
      </div>
    </footer>
  );
}
