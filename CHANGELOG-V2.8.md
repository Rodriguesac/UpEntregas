# UP Entregas 2.8.0 — Rastreamento econômico

## Objetivo
Reduzir consumo de cota do Supabase sem perder o acompanhamento da entrega.

## Rastreamento
- GPS continua nativo e ativo somente durante missão.
- Envio ao backend passa por filtro local de tempo + distância.
- Modo `dynamic`: adapta a frequência conforme fase e velocidade.
- Modo `manual`: intervalo e distância mínima configuráveis.
- Configuração é recebida da API e pode ser alterada futuramente pelo Gerenciador UP.
- Histórico detalhado em `tracking_points` fica desligado por padrão.
- A posição atual continua sendo sobrescrita em `couriers`.
- O pedido recebe espelho da posição apenas quando o mapa do cliente está ativo e respeitando intervalo próprio.

## Economia adicional
- Polling de oferta/missão Supabase passa de 6–7 s para 20 s como fallback.
- FCM continua sendo o caminho preferencial para avisos.
- Telemetria de bateria passa para janela de 10 min e só grava com mudança relevante.
- A API deixa de regravar o cadastro do entregador em toda chamada quando os dados não mudaram.
- Atualização de GPS não altera `orders.updated_at`.

## Compatibilidade
- Mesmo `applicationId`: `com.rodriguesacai.entregador`.
- Mantém Protocolo UP V3 e compatibilidade com fluxo legado durante transição.
- `versionCode`: 280.
- `versionName`: `2.8.0-rastreamento-economico`.
