/** Ícones dos meios de pagamento, em SVG inline para herdar a cor do seletor. */

export function IconeCartao() {
  return (
    <svg className="payment-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="2.5" y="5" width="19" height="14" rx="2.5" />
      <path d="M2.5 9.5h19" />
      <path d="M6 15h4" />
    </svg>
  );
}

/** Marca do Pix (Banco Central): losango central com as duas "setas" em cima e embaixo. */
export function IconePix() {
  return (
    <svg className="payment-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="8" y="8" width="8" height="8" rx="1.6" transform="rotate(45 12 12)" />
      <path d="M6.8 6.8 12 1.6l5.2 5.2h2" />
      <path d="M17.2 17.2 12 22.4l-5.2-5.2h-2" />
    </svg>
  );
}

export function IconeBoleto() {
  return (
    <svg className="payment-icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M2 5h2v14H2zM5.5 5H7v14H5.5zM8.5 5h2.5v14H8.5zM12.5 5h1v14h-1zM15 5h2v14h-2zM18.5 5h1v14h-1zM20.5 5H22v14h-1.5z" />
    </svg>
  );
}
