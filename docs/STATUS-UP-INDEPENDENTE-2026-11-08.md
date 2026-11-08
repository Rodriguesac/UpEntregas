# UP Entregas Independente — estado verificado de implantação

## Atualização operacional — 08/11/2026

- **Migração parcial aplicada, sem interrupção:** 3 registros de `public.couriers` e 2 candidaturas de `public.courier_applications` foram copiados do Supabase Primary para o UpEntrega. Todos permanecem `PENDING`; entregadores ficaram `online=false`. 2 candidaturas coincidem com Firebase UIDs já migrados. Não houve migração de GPS, documentos, CPF, Pix ou histórico de corridas. Os registros originais não foram alterados.
- **Aprovação ainda obrigatória:** o UP Gestor agora exige confirmação explícita para aprovar entregador importado. A documentação original deve ser conferida antes da ativação. O sistema continua sem administrador autorizado em `up_manager_users` e não libera corridas reais.
- **Gestor aprimorado:** aba "Integrações" adicionada com diagnóstico somente para `OWNER`/`ADMIN` com JWT Firebase verificado e allowlist. Retorna apenas indicadores booleanos; nenhum segredo ou token.
- **Backend publicado:** `up-manager-independent` v2 com diagnóstico privado de configuração. As funções de operação continuam protegidas pelas flags.
- **GPS privado:** função `up_prune_stale_location_data(7)` instalada somente no UP; acessível apenas via service role, remove pontos antigos de corridas encerradas (sem tocar em corridas ativas). **Ainda não foi agendada nem executada na operação.**
- **Android:** [PR #6 — preparação do APK independente](https://github.com/Rodriguesac/UpEntregas/pull/6) criada como rascunho; nenhuma APK independente foi promovida à distribuição.
- **Validação:** o teste de corrida completa, oferta, GPS e retorno já havia passado em transação revertida. Novos testes automáticos do UP Gestor e proteção de GPS adicionados ao GitHub Actions. Verificar o status das execuções antes de publicar.
- **Workflow antigo:** um workflow auto-modificador de cadastros foi aposentado e seu conteúdo foi arquivado em `docs/legacy`; o Gestor legado permanece inalterado.

### Bloqueios para virada de produção

1. Credenciais de Edge Functions no Supabase da loja e do UP e agendamentos autenticados.
2. Confirmação do UID Firebase real do responsável e inclusão na allowlist `up_manager_users`; ativação administrativa gradual.
3. Publicação da API independente de inscrição de motoristas, anteriormente bloqueada pela ferramenta; não contornar restrições de segurança.
4. Conferência manual de documentos e aprovação dos motoristas migrados.
5. Build Android independente, validação de assinatura e atualização controlada; push FCM.
6. Rastreio de GPS no site somente com ticket temporário assinado e flags configuradas.
7. Validação ponta a ponta real (pagamento confirmado, pedido pronto, despacho, evento assinado, retorno à loja), sem alteração dos clientes existentes.

**Nenhuma flag de tráfego real foi habilitada.** A operação antiga permanece a fonte de verdade até a virada aprovada.

---


Data: **08 de novembro de 2026**. Fonte principal do código: `Rodriguesac/UpEntregas` (branch `main`).
Destino único: projeto Supabase **UpEntrega** — `gkwmqwtktedarodvqqtn` (conta **NOVA ANDRADINA**).
A loja permanece no `fdqqwdplprzpqufpgdrm` (**Primary**).

## Implantação realizada

- Estrutura original provisória de seis tabelas tinha **zero linhas**; foi preservada em schema privado `up_transition_backup`, sem exclusão de dados.
- `public` agora contém o **schema oficial com treze tabelas**, incluindo
  `up_couriers`, `up_courier_applications`, `up_jobs`, `up_job_events`,
  `up_tracking_points`, `up_dispatch_offers`, `up_wallet_transactions`,
  `up_payouts`, `up_settings`, `up_audit_log`, `up_driver_devices`,
  `up_manager_users`, `up_offer_notifications`.
- Migrações executadas no novo projeto:
  `up_preserve_initial_empty_schema_before_official_install`,
  `up_official_schema_v1`, `up_official_driver_operations_v1`,
  `up_official_manager_v1`, `up_official_offers_v1`,
  `up_official_missing_foreign_key_indexes`.
- Todas as tabelas operacionais usam **RLS**, acesso REST de `anon` / `authenticated` revogado; Edge Functions fazem autenticação e autorização. O linter informa `RLS Enabled No Policy` intencionalmente.
- APIs do repositório **publicadas** no projeto UpEntrega:
  `up-order-bridge`, `up-status-relay`, `driver-api-independent`,
  `up-manager-independent`, `up-customer-tracking`, `up-offer-push`.
- `driver-registration-independent` **não foi publicada**: a ferramenta recusou a implantação por bloqueio de segurança. Não confundir com API ativa.
- O antigo `up-status-callback-worker` provisório foi **aposentado (HTTP 410)**;
  a fila oficial é baseada em `up_job_events` e `up-status-relay`.
- Banco UP no momento da verificação: 0 novas corridas, 0 entregadores migrados, 0 coordenadas. O banco antigo possui três registros de entregadores e duas candidaturas, sem cópia.
- As funções estão publicadas, mas o **despacho real e o GPS do cliente continuam desligados** até configurar autenticação, segredos e flags.

## Dependências externas antes de liberar operação real

1. **Supabase novo e Supabase da loja:** configurar em Edge Function Secrets o mesmo `UP_BRIDGE_SECRET` seguro (32+ caracteres); configurar separadamente `UP_TRACKING_SECRET` seguro compartilhado. Usar painel do Supabase (secrets de produção), nunca GitHub ou interface pública.
2. **Loja:** `UP_BRIDGE_ENDPOINT=https://gkwmqwtktedarodvqqtn.supabase.co/functions/v1/up-order-bridge`, `UP_WORKER_TOKEN` seguro, `UP_STORE_PICKUP_ADDRESS_JSON` do estabelecimento verificado. Só depois habilitar `UP_INDEPENDENT_HANDOFF_ENABLED`, `UP_BRIDGE_ENABLED`, `UP_STATUS_CALLBACK_ENABLED`, `UP_CUSTOMER_TRACKING_ENABLED` sob teste.
3. **UP:** `UP_STATUS_STORE_ENDPOINT=https://fdqqwdplprzpqufpgdrm.supabase.co/functions/v1/up-status-receiver`, `UP_STATUS_WORKER_TOKEN` privado, `UP_FIREBASE_PROJECT_ID=rodrigues-d6566`; manter `UP_STATUS_ENABLED` desligado até o ACK ponta a ponta.
4. **Gestão:** consultar Firebase Auth para obter UID verificado do proprietário; somente então adicionar `up_manager_users` com role `OWNER` e `active=true`. Configurar `UP_MANAGER_ALLOWED_ORIGINS` e habilitar `UP_INDEPENDENT_MANAGER_ENABLED` somente após o primeiro login validado. A página `web-gestor-independente/config.js` continua `enabled:false` por segurança.
5. **Motoristas:** habilitar `UP_INDEPENDENT_DRIVER_ENABLED` somente após migração segura das identidades e verificação do APK. A API de cadastro independente precisa ser implantada por canal autorizado antes de habilitar `UP_INDEPENDENT_REGISTRATION_ENABLED`.
6. **Ofertas com FCM:** segredos exclusivos `UP_FCM_SERVICE_ACCOUNT_JSON`, `UP_PUSH_WORKER_TOKEN`, `UP_FIREBASE_PROJECT_ID`; deixar `UP_PUSH_ENABLED` desligado até autorização e testes do push.
7. **Rastreio do cliente:** `UP_CUSTOMER_ALLOWED_ORIGINS=https://rodriguesacaiecia.web.app`, `UP_TRACKING_SECRET` compartilhado e flags de rastreio nos dois projetos. O website ainda mantém `UP_INDEPENDENT_GPS_ENABLED=false` e deve permanecer assim até o teste.
8. **Workers:** criar agendamentos autenticados de envio, relay e ofertas apenas depois que os secrets estiverem disponíveis. Não colocar token de trabalhador no navegador, nem agendar requisições públicas.
9. **Migração de pessoas:** não foi realizada. O roteiro existente requer aprovação explícita `AUTORIZO-MIGRAR-UP` e usa dois service roles guardados em GitHub Secrets para migração controlada, sem GPS nem pedidos.

## Fluxo esperado antes da ativação

`PRONTO` na loja → `gadm-up-handoff` → outbox da loja → HMAC → `up-order-bridge` → `up_jobs` (`WAITING_DRIVER`) → painel do UP → oferta ao motorista → aceite autenticado → retirada → status de rota → `up-status-relay` → callback HMAC ao Supabase da loja → aviso no site. GPS permanece **apenas no UP**, repassado em janelas curtas e autorizado pelo recibo do pedido.

## Limitações e critérios de aceite

- Um mesmo `store_order_id` não pode gerar corrida duplicada; reenvio do mesmo evento deve ser idempotente.
- Pedido já pago nunca recebe cobrança duplicada. A loja é responsável por validar o pagamento antes do handoff.
- Entregador não aprovado não pode operar; gerente não autorizado não pode consultar corridas.
- Coordenadas somente durante a etapa de entrega ao cliente e somente com ticket assinado e ainda válido.
- Sem importação de entregadores, sem liberação das flags e sem automação até integrar todas as credenciais com segurança.
- Novas APIs no Supabase UP não substituem automaticamente o APK antigo nem o servidor `driver-api` da loja.

**Segurança:** não criar UID proprietário ou senha fictícia, não publicar `sb_secret_`, `service_role`, HMAC ou credenciais privadas no GitHub. Contas reais, pedidos reais e GPS antigos permanecem intocados.
