-- Contato e origem da compra, mostrados ao vendedor no detalhe da venda:
--  * buyer_phone: celular informado no checkout (opcional), só dígitos;
--  * buyer_ip: IP de quem criou o pedido (mesmo dado que o limitador de tentativas já vê).
ALTER TABLE orders
  ADD COLUMN buyer_phone text CHECK (buyer_phone IS NULL OR buyer_phone ~ '^[0-9]{10,13}$'),
  ADD COLUMN buyer_ip text CHECK (buyer_ip IS NULL OR length(buyer_ip) <= 64);
