import { OfferForm } from "./OfferForm";

export const metadata = { title: "Nova oferta" };

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <OfferForm productId={id} />;
}
