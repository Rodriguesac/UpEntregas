# UP Entregas v1.3 — permissões e segundo plano

## Estratégia sem Blaze

O entregador decide ficar ONLINE. Nesse momento o app inicia `OnlineService`, um Foreground Service visível ao usuário. O serviço mantém o listener de ofertas direcionadas no Firestore e uma notificação permanente.

Ao receber uma oferta nova, o app publica uma notificação de alta prioridade com som/vibração e abre a corrida quando o entregador toca no alerta.

Quando a corrida é aceita, o `OnlineService` é encerrado e o `TrackingService` de localização é iniciado com o tipo `location`. Após concluir a entrega, o serviço de rastreamento para e, se o entregador continuar ONLINE, o serviço ONLINE volta.

## Por que não usar ACCESS_BACKGROUND_LOCATION

A localização da missão é iniciada a partir da tela visível do app, depois do aceite do entregador. O app não precisa rastrear o entregador o dia inteiro nem iniciar localização escondida no boot.

## Por que não existe Full Screen Intent

O app usa notificação heads-up de alta prioridade. Full Screen Intent não é tratado como mecanismo normal de alerta para aplicativo de entrega e possui restrições de plataforma/política para casos como chamadas e alarmes.

## Fabricantes Android

Alguns aparelhos encerram serviços em segundo plano de forma agressiva. A Central de Permissões contém atalhos para bateria e configurações do app. O usuário deve manter notificações liberadas e evitar `Forçar parada`.
