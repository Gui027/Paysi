import { redirect } from "next/navigation";

// A conta de recebimento (chave Pix) fica na aba Dados bancários do Financeiro.
export default function Page() {
  redirect("/saldo?aba=dados");
}
