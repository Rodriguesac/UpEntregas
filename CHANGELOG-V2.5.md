# UP Entregas 2.5.0 — Rastreamento opcional

## Mudanças
- O GPS de alta precisão permanece ativo somente enquanto existe uma missão aceita.
- A missão é observada em tempo real; conclusão, cancelamento ou remoção encerram o serviço de localização.
- O mapa do cliente respeita `rastreamentoClienteHabilitado` e nunca é liberado antes da retirada.
- Desativar o mapa não desativa a telemetria interna necessária à operação da entrega.
- Em rotas múltiplas, coordenadas são espelhadas para cada pedido que autorizou o mapa.
- A notificação permanente diferencia GPS interno de mapa compartilhado com o cliente.
- Regras do Firestore passam a permitir os campos de coordenadas realmente gravados pelo app, sem dar ao entregador permissão para alterar a preferência definida pelo Gestor.

## Compatibilidade
- Protocolo UP V3 preservado.
- `applicationId` preservado: `com.rodriguesacai.entregador`.
- `versionCode`: 250.
- `versionName`: `2.5.0-rastreamento-opcional`.
