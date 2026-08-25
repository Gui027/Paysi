import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "Paysi", template: "%s · Paysi" },
  description: "Pagamentos inteligentes para o seu negócio.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="pt-BR">
      <body>{children}</body>
    </html>
  );
}

