# UP Entregas V2.1 — Central + Rotas Múltiplas

## Integração com UP Central
- Publica bateria e estado de carregamento em tempo real enquanto ONLINE ou em missão.
- Entregador informa se está com troco, valor disponível e se está com maquininha.
- Lê parâmetros operacionais de `up_config/master`.

## Rota múltipla
- Recebe rota múltipla direcionada manualmente pelo gestor.
- Oferta mostra mapa, quantidade de paradas, distância e repasse antes do aceite.
- Uma retirada pode conter vários pedidos.
- Código de retirada da rota antes de sair da loja.
- Depois da retirada, a rota fica fechada para novos pedidos.
- Entregas são executadas uma a uma: mapa e próxima ação focam somente na parada atual.
- Cada pedido mantém seu próprio código de entrega.
- Concluir uma parada libera automaticamente a próxima.
- Finalizar a última parada encerra a rota e gera o acerto.

## Complemento antes da retirada
- Enquanto a rota estiver aberta e o entregador ainda não retirou, o gestor pode oferecer +1 pedido.
- O app toca e vibra até o entregador aceitar ou recusar o complemento.
- Aceitar recalcula/aplica a rota proposta sem perder a missão atual.
- Recusar mantém a rota atual e devolve o pedido complementar para decisão do gestor.

## Mapa e foco
- Mapa nativo embutido via WebView + OpenStreetMap/Leaflet.
- Loja, posição do entregador e paradas numeradas no mapa.
- Tela muda conforme a etapa: loja → retirada → em rota → cliente → código → próxima parada.
- Navegação externa continua disponível para o endereço atual.

## Alertas
- Nova corrida/rota toca ringtone em loop e vibra até aceitar, recusar ou expirar.
- Se o app estiver aberto, muda para a tela Corridas imediatamente.
- Se estiver em segundo plano, a notificação abre a missão correta.
- Complemento de rota também alerta enquanto a missão está ativa.

## Identidade
- Ícone oficial azul/amarelo UP Entregas fornecido pelo usuário.
- Logo oficial branca/amarela UP Entregas usada no acesso e identidade.

## Regras Firestore
- `pedidos` pode ser lido pelo entregador já atribuído à missão.
- `rotas_entrega` permite atualizações operacionais necessárias à rota múltipla e complementos.
