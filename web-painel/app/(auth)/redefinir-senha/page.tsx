import { AuthBrand } from "../../../components/AuthBrand";
import { RedefinirSenhaForm } from "./RedefinirSenhaForm";

export default async function RedefinirSenhaPage({ searchParams }: { searchParams: Promise<{ token?: string }> }) {
  const { token = "" } = await searchParams;
  return (
    <div className="auth-shell">
      <AuthBrand />
      <main className="auth-main">
        <div className="auth-card">
          <h2>Defina uma nova senha</h2>
          <p>O link pode ser usado uma única vez e expira após 1 hora.</p>
          <RedefinirSenhaForm token={token} />
        </div>
      </main>
    </div>
  );
}
