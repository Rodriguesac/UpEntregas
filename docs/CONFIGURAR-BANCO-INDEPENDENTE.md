# Preparação para trocar o Supabase do UP Entregas

**Estado em 04/11/2026:** projeto `up-entregas` ainda não provisionado — a conta atingiu o limite global de projetos Free. Não usar credenciais do Supabase da loja como se fossem independentes.

## O que já está preparado

- `supabase/up-independent-schema.sql`: esquema isolado, com acesso direto às tabelas bloqueado para `anon` e `authenticated`. **Não aplicar no banco da loja.**
- `supabase/functions/up-order-bridge/index.ts`: receptor servidor-servidor de pedidos com assinatura HMAC e deduplicação.
- `bridge/handoff-contract.mjs`: modelo mínimo de pedido e assinatura. Não contém credenciais.
- `scripts/test-up-independence.mjs` e `scripts/test-up-handoff-contract.mjs`: testes executados no GitHub Actions.
- `app/build.gradle` e `SupabaseDriverApi.java`: URL da API e chave pública configuráveis na compilação.

## Compilar Android apontando para o projeto atual (padrão)

O comando `./gradlew assembleDebug` continua usando o endpoint público atual de `rodriguesacaiecia2`. A alteração recente **não migra os usuários** nem altera a infraestrutura em produção.

## Preparar build de testes para o novo projeto, quando estiver disponível

No ambiente privado de compilação:

```bash
./gradlew assembleDebug \
  -PupDriverApiUrl="https://SEU-NOVO-PROJETO.supabase.co/functions/v1/driver-api" \
  -PupPublishableKey="SUA-CHAVE-PUBLICA"
```

Esses dois valores são **públicos**. Nunca passar chaves secret/service_role, `UP_BRIDGE_SECRET` ou tokens de usuários pelo Gradle, GitHub ou APK.

## Dependências da virada

1. Liberar vaga de projeto Free, escolhendo um projeto antigo seguro para pausar, ou aprovar outra modalidade de hospedagem.
2. Criar `up-entregas`, preferencialmente na região São Paulo (`sa-east-1`) se disponível.
3. Aplicar e testar o SQL **somente no projeto novo**; revisar permissões e políticas RLS.
4. Adaptar e publicar as Edge Functions `driver-api`, `driver-registration` e `up-order-bridge` ao novo esquema.
5. Configurar segredos privados entre os dois Supabase exclusivamente no servidor.
6. Migrar motoristas, documentos e dados operacionais de modo protegido, sem expor coordenadas e documentos no GitHub.
7. Publicar APK de teste com URL e chave pública novas, validar Firebase Auth, fluxo de corrida e GPS.
8. Adaptar o painel web do UP, atualmente conectado à API antiga.
9. Implantar retorno seguro de status ao GADMINISTRA e rastreamento autorizado ao site do cliente.
10. Fazer virada controlada e manter rota de rollback até confirmar dados, integrações e notificações.

**Importante:** publicar apenas o receptor de pedidos não é suficiente para migrar o app. Sem as funções próprias do UP e a integração de status, a entrega ainda dependeria do banco antigo.


## Preparação do handoff confiável — atualização de 06/11/2026

Foi testado em PostgreSQL 17 descartável um mecanismo de envio transacional da loja para o UP:

1. O servidor da loja cria um evento imutável via `public.up_store_outbox_enqueue`. O banco valida novamente `PRONTO`, modalidade entrega, código e pagamento. Um pedido só possui uma entrada de envio.
2. `public.up_store_outbox_claim` verifica o estado do pedido novamente antes da tentativa, retira pedidos cancelados e reserva pequenos lotes com trava e vencimento.
3. `bridge/store-outbox-worker/index.ts` é o **trabalhador ainda não implantado**. Somente aceita chamada servidor-servidor, token privado, segredo HMAC, `UP_BRIDGE_ENABLED=true` e `UP_BRIDGE_ENDPOINT` apontando para Supabase diferente do banco da loja.
4. O receptor independente `up-order-bridge` confirma a criação da corrida, validando a assinatura e impedindo duplicação.
5. Somente após resposta positiva com ID da corrida, `public.up_store_outbox_finish(...,'ack',...)` muda o status da loja para `AGUARDANDO_ENTREGADOR` e registra `raw_payload.up_bridge`. A confirmação e o avanço do pedido acontecem na **mesma transação Postgres**.
6. Se o UP recebeu, mas o pedido da loja mudou antes do ACK, o registro fica `RECONCILE`, com ID da corrida para conferência; não é apagado nem retransmitido cegamente.
7. Se o envio falha, pode repetir com o **mesmo event_id e mesmo payload**. Exceder o limite de tentativas também envia para `RECONCILE`.

### Variáveis privadas para implantação FUTURA

| No Supabase da loja | Finalidade |
|---|---|
| `UP_BRIDGE_ENABLED` | `false` por padrão; habilitar apenas após virada autorizada |
| `UP_BRIDGE_ENDPOINT` | URL HTTPS da Edge Function `up-order-bridge` do projeto UP, nunca da loja |
| `UP_BRIDGE_SECRET` | Segredo longo e aleatório, igual apenas nos dois servidores |
| `UP_WORKER_TOKEN` | Token privado para disparar o processamento da fila |
| `SUPABASE_URL` / `SUPABASE_SERVICE_ROLE_KEY` | Fornecidos pelo runtime Supabase; exclusivamente servidor |

**Não passar essas variáveis a aplicativos, clientes, GitHub ou páginas web.** O worker e o SQL foram apenas versionados. Não foram ativados no projeto da loja.

### Dependências ainda faltantes antes de produção

- Novo projeto Supabase criado (bloqueado pelo limite de projetos gratuitos).
- API do entregador e painel UP operando sobre o banco independente.
- Enfileiramento integrado ao fluxo administrativo com sessão revalidada.
- Disparo seguro do trabalhador por um serviço agendado.
- Retorno de estados UP → loja e rastreamento autorizado no site do cliente.
- Testes reais com pedidos fictícios, notificações e rollback, antes do primeiro pedido de cliente.

Verificar resultados em `https://github.com/Rodriguesac/UpEntregas/actions`.


## Implementação do backend independente do entregador — 07/11/2026

Arquivos adicionados, preparados **sem instalação em produção**:

- `supabase/up-driver-operations.sql`: operações transacionais `up_driver_accept`, `up_driver_advance` e `up_driver_location` e tabela protegida `up_driver_devices`. O aceite exige oferta **destinada ao motorista e ainda válida**; a corrida não pode ser atribuída duas vezes. As etapas e códigos de confirmação são verificados no servidor. GPS fica no banco UP com bloqueio de atualizações excessivas.
- `supabase/functions/driver-api-independent/index.ts`: Endpoint compatível com comandos do Android existente: `offer`, `mission`, `history`, `presence`, `telemetry`, `equipment`, `token`, `accept`, `reject`, `expire`, `stage`, `pickup`, `finish`, `location` e `occurrence`. Autentica Firebase JWT pela assinatura pública do Google e consulta apenas tabelas do UP.
- `supabase/functions/driver-registration-independent/index.ts`: endpoint separado `submit`, `documents_update` e `status_sync`, com validação Firebase. O usuário pode apresentar e corrigir documentos, mas não consegue autoconceder o estado `APPROVED`.
- `scripts/verify-up-driver-postgres.sh`: teste real PostgreSQL 17 de ofertas e bloqueio de outros entregadores, progressão sequencial da corrida, GPS, throttling e restrição de privilégios.
- CI com `deno check` das funções e bateria de verificações de segurança dos endpoints.

Última validação: https://github.com/Rodriguesac/UpEntregas/actions/runs/42404823722

**Proteção de produção:** `UP_INDEPENDENT_DRIVER_ENABLED` e `UP_INDEPENDENT_REGISTRATION_ENABLED` precisam estar explicitamente em `true` para habilitar as novas funções. O APK atual segue utilizando `driver-api` legada e o banco anterior.

**Dependências ainda abertas:** o novo Supabase não foi criado por limite de projetos gratuitos; o painel UP precisa migrar para o schema novo, e as permissões/gestão de revisão cadastral precisam ser finalizadas. Após provisionamento, testar o APK de ponta a ponta com usuários fictícios, Firebase, notificações, coleta, entrega, retorno à loja e rastreamento. Não distribuir APK apontando ao projeto independente antes desses testes.

**Nota funcional:** o envelope atual do pedido transmite `collection_amount` (o que deve ser recebido no ato da entrega), não necessariamente o valor total da compra. O APK deve exibir os valores corretamente após validação financeira específica antes da virada.
