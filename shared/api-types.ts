export type Segmento = "DIGITAL" | "SAAS";
export type MetodoPagamento = "PIX" | "BOLETO" | "CARD";

export interface OfertaPublica {
  slug: string;
  nome: string;
  descricao: string;
  segmento: Segmento;
  valorCentavos: number;
  metodos: MetodoPagamento[];
  vendedor: { nome: string; logoUrl?: string };
}

