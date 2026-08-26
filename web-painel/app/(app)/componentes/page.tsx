"use client";

import { useState } from "react";
import { Botao, Campo, Cartao, Checkbox, Dialog, EmptyState, Etiqueta, Radio, Select, Skeleton, Tabela, Toast } from "../../../components/ui";

export default function ComponentesPage() {
  const [dialogOpen, setDialogOpen] = useState(false);
  return <div className="component-showcase">
    <header className="content-header"><div><h1>Componentes do painel</h1><p>Estados e padrões reutilizáveis do design system.</p></div><Etiqueta tone="success">Acessível</Etiqueta></header>
    <Cartao><h2>Formulário</h2><div className="field-grid"><Campo label="Nome" placeholder="Nome completo" hint="Como aparece no cadastro" /><Select label="Perfil" defaultValue=""><option value="" disabled>Selecione</option><option>Vendedor</option><option>Afiliado</option></Select></div><Campo label="E-mail com erro" defaultValue="email-invalido" error="Informe um e-mail válido" /><Checkbox label="Aceito os termos" /><Radio name="mode-example" label="Vender" defaultChecked /><Radio name="mode-example" label="Divulgar" /><div className="ui-actions"><Botao onClick={() => setDialogOpen(true)}>Continuar</Botao><Botao variant="secondary">Cancelar</Botao><Botao variant="danger">Excluir</Botao></div></Cartao>
    <Cartao><h2>Dados e estados</h2><div className="ui-labels"><Etiqueta>Rascunho</Etiqueta><Etiqueta tone="warning">Pendente</Etiqueta><Etiqueta tone="success">Aprovado</Etiqueta><Etiqueta tone="danger">Falhou</Etiqueta></div><Tabela caption="Exemplo de vendas" headers={["Pedido", "Status", "Total"]} rows={[["#1001", <Etiqueta key="paid" tone="success">Pago</Etiqueta>, "R$ 100,00"]]} /><Skeleton /><Toast>Alterações salvas com sucesso.</Toast><Toast tone="danger">Não foi possível salvar.</Toast></Cartao>
    <Cartao><EmptyState title="Nenhum resultado" description="Altere os filtros ou crie o primeiro item." action={<Botao>Criar item</Botao>} /></Cartao>
    <Dialog open={dialogOpen} title="Confirmar operação" onClose={() => setDialogOpen(false)}><p>Revise os dados antes de continuar.</p></Dialog>
  </div>;
}
