import { redirect } from "next/navigation";

// A verificação de identidade agora é a aba Identidade do Financeiro; o ?next= continua valendo.
export default async function Page({ searchParams }: { searchParams: Promise<{ next?: string }> }) {
  const { next } = await searchParams;
  redirect(next ? `/saldo?aba=identidade&next=${encodeURIComponent(next)}` : "/saldo?aba=identidade");
}
