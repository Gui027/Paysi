import { IconMessage } from "./icons";
import { Reveal } from "./Reveal";

export function ContactBanner() {
  return (
    <Reveal>
      <div className="contact-banner">
        <div className="contact-banner-text">
          <span className="icon-badge">
            <IconMessage size={20} />
          </span>
          <div>
            <h3>Ficou alguma dúvida?</h3>
            <p>Não encontrou a resposta que está procurando? Entre em contato com a Paysi.</p>
          </div>
        </div>
        <a className="btn btn-primaria" href="mailto:comercial@paysi.com.br">
          Entre em contato
        </a>
      </div>
    </Reveal>
  );
}
