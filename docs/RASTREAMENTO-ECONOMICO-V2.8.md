# Rastreamento econômico — UP Entregas 2.8

A configuração remota fica em `couriers.metadata.tracking_config` no Supabase. Não exige nova tabela.

Exemplo:

```json
{
  "mode": "dynamic",
  "manual_interval_seconds": 30,
  "manual_min_distance_m": 50,
  "before_pickup_interval_seconds": 45,
  "before_pickup_min_distance_m": 50,
  "delivery_moving_interval_seconds": 20,
  "delivery_moving_min_distance_m": 20,
  "delivery_fast_interval_seconds": 15,
  "delivery_fast_min_distance_m": 35,
  "stationary_interval_seconds": 60,
  "stationary_min_distance_m": 15,
  "heartbeat_seconds": 90,
  "history_enabled": false,
  "order_mirror_interval_seconds": 30,
  "server_min_interval_seconds": 8
}
```

## Modos

### dynamic
O aplicativo decide quando transmitir usando fase da missão, velocidade, tempo e distância. É o padrão recomendado.

### manual
O Gerenciador define `manual_interval_seconds` e `manual_min_distance_m`. Mesmo no manual existe um heartbeat para evitar que o mapa pareça abandonado quando o entregador fica parado.

## Regras de economia

1. Sem missão: TrackingService desligado.
2. Na missão: GPS pode continuar localmente, mas nem todo ponto vai ao Supabase.
3. `couriers.current_lat/current_lng`: guarda somente a posição atual.
4. `tracking_points`: só recebe linhas quando `history_enabled=true`.
5. `orders.raw_payload.up_location`: só é atualizado para mapa do cliente e no intervalo de espelhamento.
6. Telemetria de bateria não acompanha cada coordenada.
7. Oferta/missão usam polling apenas como fallback; FCM permanece responsável pelos avisos imediatos.

O futuro Gerenciador UP deve editar apenas esse JSON, permitindo configuração global/per-entregador sem novo APK.
