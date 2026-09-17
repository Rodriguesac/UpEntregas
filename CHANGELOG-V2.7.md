# UP Entregas 2.7.0 — Supabase operacional

## Entregas

- Consulta de oferta e missão ativa pelo Supabase.
- Aceite atômico: a primeira confirmação válida reserva o pedido para um único entregador.
- Recusa e expiração impedem que a mesma oferta volte imediatamente para o entregador.
- Chegada à loja, retirada, chegada ao cliente, conclusão e ocorrência passam pela API protegida.
- Histórico combina entregas novas do Supabase com corridas legadas.

## Operação e rastreamento

- Presença online, bateria, carregamento, disponibilidade de troco e maquininha são mantidos em `couriers`.
- Token de notificação é registrado em `fcm_devices`.
- GPS da missão é gravado em `tracking_points` e na posição atual do entregador.
- Eventos de cada etapa são registrados em `order_status_events`.

## Segurança e compatibilidade

- A Edge Function valida assinatura, emissor e audiência do token Firebase.
- Somente um cadastro de entregador ativo e aprovado pode acessar a API.
- As operações verificam a atribuição da entrega no servidor.
- O app mantém leitura e operação de corridas/rotas antigas do Firestore durante a migração.
- Mesmo `applicationId` e assinatura do fluxo de build anterior, permitindo instalar por cima da 2.6.0.

## Build

- `versionCode`: 270.
- `versionName`: `2.7.0-supabase-operacional`.
