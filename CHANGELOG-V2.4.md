# UP Entregas 2.4.0 — Rota Premium

## Objetivo
Levar a execução de rotas múltiplas ao padrão visual premium definido para o UP Entregas, sem misturar status de rota, pedido, pagamento e repasse.

## Principais mudanças
- Tela de rota múltipla ativa redesenhada com hierarquia Material 3.
- Cabeçalho próprio `Rota ativa`, resumo de quantidade e código da rota.
- Progresso explícito `Loja → parada atual → próximas paradas`.
- Mapa em destaque, com parada atual azul e próximas paradas em cinza.
- Em modo escuro, mapa usa base escura; rota atual e trecho futuro têm tratamentos visuais diferentes.
- Cartão da parada atual mostra somente os dados necessários naquele momento.
- Pagamento separado do status da rota: método, valor a receber, troco ou maquininha.
- Pedidos pagos online exibem `Não cobrar`.
- Valor ausente não é tratado automaticamente como `pago online`; aparece `Confirmar valor`.
- Quando houver identificação da maquininha na missão, o nome/terminal aparece na parada.
- Próximas entregas ficam recolhidas em linhas compactas, sem poluir a tela.
- Botões de ação mudam por etapa: ir à loja, chegar à loja, retirar, ir ao cliente, chegar ao cliente e confirmar entrega.
- `Ver rota completa` abre painel interno do UP, sem diálogo de sistema.
- Mantida a correção da V2.3.1: rota múltipla não possui código extra de retirada.

## Compatibilidade
- Protocolo UP V3 preservado.
- applicationId preservado: `com.rodriguesacai.entregador`.
- versionCode: 240.
- versionName: `2.4.0-premium-route`.
