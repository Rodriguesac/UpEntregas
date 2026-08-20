# Régua visual — Rota Premium V2.4

A tela de missão múltipla deve responder rapidamente a quatro perguntas:

1. Quantas entregas existem na rota?
2. Qual é a parada atual?
3. Onde o entregador deve ir agora?
4. O que deve ser cobrado do cliente desta parada?

## Ordem visual
1. Marca + `Rota ativa`.
2. Resumo da rota e status aberto/fechado.
3. Progresso da retirada/paradas.
4. Mapa.
5. Parada atual.
6. Pagamento da parada atual.
7. Próximas paradas recolhidas.
8. Uma ação principal e ações secundárias contextuais.

## Regras
- Pedido, rota, pagamento e repasse são estados independentes.
- Nunca inferir `pago online` apenas porque o valor a receber está ausente ou zerado.
- Troco só aparece quando existe `trocoPara`.
- Maquininha só aparece quando a parada exige cartão/terminal.
- A próxima parada nunca compete visualmente com a atual.
- Nenhuma confirmação operacional usa AlertDialog do sistema.

A imagem `REFERENCIA-ROTA-ATIVA-V2.4.png` é referência de composição e nível de acabamento; não é usada como tela do aplicativo.
