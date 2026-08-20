# UP Entregas V1.9 — Foco na Entrega

## Visual fiel ao protótipo aprovado
- Home simplificada: Online/Offline, resumo do dia e somente a próxima ação.
- Corridas virou tela própria e recebe foco automático quando chega uma oferta.
- Corrida ativa com hierarquia: Pedido -> Loja -> Entrega -> Pagamento -> Próxima ação.
- Linha de andamento enxuta: Loja / Em rota / Entrega.
- Botão principal roxo e grande; mapa, contato e problemas ficam como ações secundárias.
- Ganhos virou tela própria, sem AlertDialog: total de hoje e somente valores essenciais.
- Conta virou tela própria, sem janelão cinza: perfil e menus em níveis.
- Notificações, perfil/veículo e Pix ficam em telas de segundo nível.
- Diagnóstico técnico só aparece na Home quando existe algo realmente errado.
- Tema escuro passa a ser o padrão em instalações novas; tema claro continua disponível.

## Operação preservada
- Oferta direcionada continua manual.
- Aceite/recusa preservados.
- Código de retirada preservado.
- Código de entrega preservado.
- Rastreamento e navegação preservados.
- Ocorrências preservadas.
- Financeiro/acerto preservado.

## Atualização do APK
- O workflow passa a preservar uma chave de debug estável via GitHub Actions cache.
- A V1.9 pode exigir UMA última desinstalação porque versões anteriores foram assinadas por runners diferentes.
- Compilações posteriores feitas com a chave preservada podem atualizar por cima da V1.9.
