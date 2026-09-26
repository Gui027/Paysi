import { redirect } from "next/navigation";

// O saque agora é uma janela dentro do Financeiro.
export default function Page() {
  redirect("/saldo?sacar=1");
}
