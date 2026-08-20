# UP Entregas 2.0.0 — Profissional / Foco na Entrega

## Interface
- Home reduzida ao essencial: disponibilidade, resumo do dia e próxima entrega.
- Corrida ativa reorganizada por contexto: oferta, caminho da loja, retirada, rota e cliente.
- Informações sensíveis/operacionais aparecem somente quando são úteis naquela etapa.
- Nova navegação inferior com ícones reais e rótulo `Entrega` durante missão ativa.
- Diálogos genéricos do Android removidos das ações principais; retirada, entrega, recusa, ocorrência, Pix e logout usam painéis próprios do UP.
- Logo oficial Rodrigues Açaí incorporada no login e na retirada da loja.
- Tema escuro refinado com roxo UP, verde somente para sucesso/disponibilidade e melhor contraste.

## Fluxo da entrega
- Oferta: mostra distância, ganho, destino resumido e pagamento; aceitar/recusar com bloqueio de toque duplo.
- Aceita: foco em chegar à loja e abrir navegação.
- Loja: confirmação de retirada com validação do código quando exigido.
- Em rota: foco no endereço do cliente, pagamento, troco/maquininha e contato.
- Cliente: confirmação do código de entrega e valor recebido antes da finalização.
- Ações críticas não são confirmadas sem internet, evitando estado ambíguo.
- Tela permanece ativa enquanto houver missão em andamento.
- O entregador não consegue sair da conta durante uma entrega.

## Operação preservada
- Firestore em tempo real.
- Corrida direcionada/manual pelo Gestor.
- GPS durante missão.
- WhatsApp do cliente.
- Google Maps/navegação.
- Ocorrências sem cancelar automaticamente a corrida.
- Ganhos e acertos.
- Notificações e serviço ONLINE.
- Cadastro, aprovação e Pix sob aprovação da loja.
