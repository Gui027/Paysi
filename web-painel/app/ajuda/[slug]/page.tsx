import Link from "next/link";
import { notFound } from "next/navigation";
import { findCategory } from "../../../lib/ajuda";

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  return { title: findCategory(slug)?.title ?? "Central de Ajuda" };
}

export default async function Page({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const category = findCategory(slug);
  if (!category) notFound();
  return <div className="aj-wrap aj-category">
    <nav aria-label="Você está em"><Link href="/ajuda">Central de Ajuda</Link> <span aria-hidden="true">›</span> <span>{category.title}</span></nav>
    <h1>{category.title}</h1>
    <p>{category.description}</p>
    <p className="aj-empty">Os artigos desta categoria serão publicados em breve.</p>
  </div>;
}
