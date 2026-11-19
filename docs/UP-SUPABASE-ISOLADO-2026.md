# UP Entregas — isolamento do Supabase (plano de implantação)

Status: **preparado, não implantado**. Este arquivo não muda a produção.

## Regra de arquitetura
- **GADMINISTRA / loja** continuam donos do pedido, estoque, preço, pagamento e histórico.
- **UP Gestor / aplicativo UP** são donos de alocação, aceitação, despacho, GPS, ocorrências e acertos.
- **Site do cliente** recebe somente posição e progresso da própria entrega, não dados de outros motoboys.
- O novo projeto Supabase deve estar em uma **organização Free diferente** da organização `rodroguesacaiecia`, para isolar as cotas organizacionais. O limite total da conta Free ainda é de dois projetos ativos.
- **Não alterar** credenciais, endpoints, tabelas ou deploys existentes até a validação em homologação.

## Ponto de partida auditado em 04/11/2026
- Repositório `Rodriguesac/UpEntregas`: Android nativo; versão 2.8 com GPS dinâmico, serviço de localização e integração legada Firebase/Supabase.
- Projeto Supabase atual `rodriguesacaiecia2`: tabelas `couriers`, `courier_applications`, `courier_wallet_transactions`, `courier_payouts`, `tracking_points`, `orders`, `up_manager_settings`, entre outras.
- Edge Functions existentes: `driver-api`, `driver-registration`, `up-manager-data`. **Ainda apontam para o projeto atual**.
- `up-manager-data` atualmente usa autenticação baseada em PIN e um mecanismo de sincronização com Firestore que precisa ser reavaliado. Não copiar esse mecanismo de autorização para o backend novo.

## Modelo mínimo do projeto novo
Arquivo: `supabase/migrations/202611040001_up_isolated_core.sql`.
- `up_couriers`: entregadores e disponibilidade.
- `up_courier_applications`: cadastro e aprovação, de acesso exclusivamente administrativo.
- `up_deliveries`: **projeção logística** de pedidos (não uma cópia do pedido financeiro/comercial). Uma entrega por chave `(source_system, source_order_id)`.
- `up_delivery_events`: trilha de eventos de entrega.
- `up_courier_locations`: última posição do entregador, atualizada sem mexer no pedido.
- `up_location_history`: histórico opcional, desligado na configuração inicial.
- `up_inbox_events`: eventos recebidos da loja com deduplicação.
- `up_outbox_events`: mudanças operacionais a devolver à loja, com tentativa/reenvio.
- `up_wallet_entries` / `up_payouts`: acertos próprios do UP.
- `up_settings`: parâmetros econômicos de GPS.

## Integração Loja → UP
1. Pedido confirmado e preparado na loja permanece no Firebase/Supabase comercial (origem efetiva a ser verificada).
2. Ao liberar para despacho, backend confiável envia evento `delivery.requested` ao UP com `event_id`, `source_order_id`, código, endereço necessário e pagamento *a receber na entrega*, se aplicável.
3. UP faz upsert idempotente pela origem+ID; **nunca** usa número visível como chave primária.
4. Painel UP designa motoboy; app UP aceita/recusa; transições são validadas no servidor.
5. UP publica `delivery.assigned`, `delivery.picked_up`, `delivery.delivered` ou `delivery.failed` de volta à loja pelo outbox, com confirmação e tentativas posteriores.
6. A loja apresenta o status no histórico do pedido, preservando-a como origem comercial.

## Rastreamento para o cliente
- GPS é recebido apenas durante missão ativa, com permissão Android, filtros de precisão/distância/tempo e fila offline pequena.
- Rastrear para o cliente **somente após retirada**, quando `tracking_enabled=true`.
- Canal privado por entrega ou endpoint autenticado que valida Firebase UID do cliente e vínculo real ao pedido (não basta conhecer o código do pedido).
- O cliente lê somente coordenada atual, timestamp, precisão/estado e ETA calculado; nunca obtém a lista de motoboys ou histórico completo.
- Ao entregar, cancelar, trocar motoboy ou expirar o vínculo, revogar a assinatura. Mostrar **última atualização** e aviso de GPS desatualizado.
- Para múltiplas entregas na mesma rota, compartilhar posição somente com clientes cujas entregas estão em andamento e habilitadas, sem expor outras paradas.

## Segurança do novo backend
- Todas as tabelas do schema inicial têm RLS ligada e **nenhuma política de acesso público**.
- Somente Edge Functions com validação de Firebase JWT assinado, papel autorizado e relação pedido→cliente / pedido→entregador podem ler ou escrever dados.
- API keys privadas e service_role apenas no servidor (secrets), nunca Android ou site.
- Webhooks interprojetos assinados, com nonce/event_id, timestamp, limite de repetição, idempotência e auditoria.
- Configurar CORS para origens aprovadas, limitar consultas e evitar vazamento de dados de cliente.

## Ordem de execução e validação
1. Usuário cria segunda organização **UP Entregas** no Dashboard Supabase, em **Free** (a conexão atual não permite criar organizações).
2. Criar **um projeto novo** nessa organização pelo conector; usar a região `sa-east-1` preferencialmente para novo serviço no Brasil, sujeita a disponibilidade e confirmação da organização.
3. Aplicar migration no **novo** projeto; verificar estrutura, RLS e privilégios. Nenhuma migration no projeto atual.
4. Criar Edge Functions UP isoladas, com Firebase JWT e sincronização por eventos; testar status, falhas e reprocessamento.
5. Migrar apenas entregadores aprovados, cadastros necessários e saldos auditados; pedidos antigos permanecem onde estão.
6. Configurar staging Android + UP Gestor para novo projeto; testar pedido real de homologação com GPS e encerramento.
7. Conectar rastreio autorizado no site do cliente; executar teste de acesso cruzado entre contas e perda de GPS.
8. Migrar gradualmente operações ativas e desligar caminhos de GPS do projeto antigo **somente após comprovar equivalência**.
9. Confirmar métricas no painel de uso de **ambas organizações**, monitorar erros e manter rollback pronto.

## Critérios de aceite
- GADMINISTRA continua operando pedidos sem mudança visível além da atribuição/status da entrega.
- O UP recebe cada entrega uma única vez mesmo com reenvio de eventos.
- GPS não grava na base comercial; cliente vê apenas a própria entrega.
- Reabertura do app, bateria otimizada, GPS ausente, rede intermitente e entrega múltipla têm tratamento.
- Fechamento, devolução e cancelamento removem rastreamento público.
- Sem mudanças em produção antes de testes de ponta a ponta.
