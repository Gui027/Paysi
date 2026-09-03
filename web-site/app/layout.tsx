import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-inter",
  display: "swap",
});

export const metadata: Metadata = {
  title: { default: "Paysi — Pagamentos e checkout de alta conversão", template: "%s · Paysi" },
  description:
    "Substitua seu checkout padrão pela Paysi: pagamentos inteligentes, checkout de alta conversão e uma experiência de compra sem fricção para o seu negócio.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="pt-BR" className={inter.variable}>
      <body>{children}</body>
    </html>
  );
}
