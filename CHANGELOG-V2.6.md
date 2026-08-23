# UP Entregas 2.6.0 — Métricas de entrega

## Distância percorrida

- Início automático após a retirada (`TO_CUSTOMER`).
- Acúmulo persistente por missão, inclusive se o Android recriar o serviço.
- Leituras com baixa precisão são descartadas.
- Ruído de GPS parado e saltos incompatíveis com velocidade real não entram no total.

## Compatibilidade

- A distância é gravada em `localizacaoEntregador.distanciaPercorridaEntregaMetros`.
- O campo fica dentro do mapa de localização já aceito pelo contrato atual do Firestore.
- Funciona em corrida simples e rota múltipla, sem alterar a regra de complementos antes da retirada.

## Build

- `versionCode`: 260.
- `versionName`: `2.6.0-metricas-entrega`.
