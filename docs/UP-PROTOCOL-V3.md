# UP Protocol V3 — estado operacional único

Este pacote usa a mesma máquina de estados em UP Entregas, UP Central e Rodrigues Gestor.

| Estado | Significado | Rota aceita complemento? | Ação principal do entregador |
|---|---|---:|---|
| OFFER_PENDING | oferta aguardando resposta | não | aceitar/recusar |
| TO_STORE | entregador indo para a loja | sim | navegar / cheguei à loja |
| AT_STORE | entregador na loja, sem retirada confirmada | sim | confirmar retirada |
| TO_CUSTOMER | pedidos retirados, indo ao cliente | não | navegar / cheguei ao cliente |
| AT_CUSTOMER | entregador no endereço do cliente | não | código + concluir entrega |
| DELIVERED | missão concluída | não | voltar à disponibilidade |
| CANCELED | missão cancelada/expirada | não | aguardar nova decisão |

Regra estrutural: a retirada confirmada é a fronteira. Antes dela, `upRouteOpen=true`; depois dela, `upRouteOpen=false` e `pickupConfirmed=true`.

Campos canônicos: `upProtocolVersion=3`, `upState`, `upRouteOpen`, `pickupConfirmed`. No documento do entregador: `upMissionState`, `canReceiveRouteComplement`, `upOpenRouteId`.
