import { OfferForm } from "../OfferForm";

export const metadata = { title: "Editar oferta" };

export default async function Page({ params }: { params: Promise<{ id: string; offerId: string }> }) {
  const { id, offerId } = await params;
  return <OfferForm productId={id} offerId={offerId} />;
}
