export function Recusado({ onTentarPix }: { onTentarPix?: () => void }) {
  return (
    <div className="error-panel" role="alert">
      <h2>Pagamento recusado</h2>
      <p>Seu banco não aprovou esta cobrança. Verifique os dados do cartão ou tente outro meio de pagamento.</p>
      {onTentarPix && (
        <button type="button" className="pay-button" onClick={onTentarPix}>
          Pagar com Pix em vez disso
        </button>
      )}
    </div>
  );
}
