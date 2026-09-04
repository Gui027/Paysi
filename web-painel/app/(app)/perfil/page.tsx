import { EmptyState } from "../../../components/ui";

export const metadata = { title: "Perfil da conta" };

export default function Page() {
  return <>
    <header className="content-header">
      <div>
        <h1>Perfil da conta</h1>
        <p>Dados cadastrais, plano e configurações de repasse.</p>
      </div>
    </header>
    <EmptyState
      title="Ainda não disponível"
      description="Os dados de perfil (tipo de pessoa, documento, prazo de repasse, plano e status da conta) dependem de um endpoint que a API ainda não expõe. Assim que o contrato for alinhado com o backend, esta página passa a exibir e permitir a edição desses dados."
    />
  </>;
}
