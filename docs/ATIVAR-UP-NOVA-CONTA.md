# UP Entregas — ativação na conta Supabase independente

**Projeto já criado pelo proprietário:** `gkwmqwtktedarodvqqtn`, URL
`https://gkwmqwtktedarodvqqtn.supabase.co`.

**Situação atualizada — 08/11/2026:** As **duas contas Supabase estão conectadas**, e a estrutura oficial do UP foi implantada no projeto novo. O banco da loja e os aplicativos legados permanecem intactos. As credenciais privadas e as flags operacionais ainda não foram configuradas. Veja [status da implantação](STATUS-UP-INDEPENDENTE-2026-11-08.md).

## O que já foi construído

| Módulo | Preparado |
|---|---|
| Banco operacional UP, motorista, GPS, ofertas e status | Sim, no GitHub |
| UP Gestor — painel responsivo | Sim, separado do GADMINISTRA |
| UP Gestor Android | APK independente, com instalação separada |
| Login Firebase e perfis administrativos OWNER/ADMIN/DISPATCHER/VIEWER | Sim |
| Cadastro e aprovação dos entregadores | Sim |
| Fila de entrega de pedidos com ACK/retentativa e estados de corrida | Sim |
| Oferta enviada ao motoboy com push FCM autenticado | Preparado, requer credencial Firebase no servidor |
| Mapa do cliente com ticket de 75 segundos | Preparado, requer secrets nos dois servidores |
| Retorno de status à loja | Função `up-status-receiver` instalada **desativada** na loja |
| Emissão de ticket pelo site da loja | Função `up-customer-ticket` instalada **desativada** na loja |
| Projeto SUPABASE independente: tabelas e funções em produção | **Banco oficial instalado; 6 APIs operacionais implantadas; cadastro independente pendente por bloqueio de implantação** |

## Opção A: conectar a nova conta Supabase ao ChatGPT

Nas configurações de Plugins do ChatGPT, reconectar Supabase à conta proprietária do projeto independente. Confirmado o acesso, as tabelas e funções podem ser instaladas diretamente pelo assistente, sem computador.

Não fornecer senha, chaves privadas, access token ou DATABASE_URL pelo chat.

## Opção B: instalador manual GitHub Actions

Esta opção não depende da conta Supabase conectada ao ChatGPT. Requer que o proprietário configure **dois secrets** no repositório `Rodriguesac/UpEntregas`:

- `UP_DATABASE_URL`: URL Postgres privada da **conta nova**, encontrada em Supabase → projeto UP → Connect. A URL deve conter `gkwmqwtktedarodvqqtn` como projeto na hostname ou no username `postgres.gkwmqwtktedarodvqqtn`.
- `UP_SUPABASE_ACCESS_TOKEN`: token pessoal **da conta nova**, gerado no próprio Supabase, para publicar Edge Functions. Salvar somente como GitHub Actions Secret.

No celular: GitHub → repositório → Settings → Secrets and variables → Actions → New repository secret.

Depois: GitHub → Actions → **Ativar infraestrutura independente UP (manual)** → Run workflow. Informar:
- `project_ref`: `gkwmqwtktedarodvqqtn`
- `deploy_schema`: verdadeiro
- `deploy_functions`: verdadeiro

O workflow se recusa a instalar tabelas se a URL Postgres apontar para o banco antigo; cria as tabelas no projeto novo e publica as funções com flags de operação **desativadas** por padrão.

[Executar instalador pelo GitHub Actions](https://github.com/Rodriguesac/UpEntregas/actions/workflows/deploy-up-independent.yml)

## Ativação da operação depois do instalador

1. **Administrador:** conferir a conta real do proprietário em Firebase Authentication do projeto `rodrigues-d6566`; copiar o Firebase UID. No SQL Editor **do Supabase UP**, inserir exclusivamente o UID verificado:
   ```sql
   insert into public.up_manager_users(firebase_uid,display_name,role,active)
   values ('UID_REAL_VERIFICADO_NO_FIREBASE','Proprietário UP','OWNER',true)
   on conflict(firebase_uid) do update
   set role='OWNER',active=true;
   ```
   Não criar gestor com senha padrão nem aprovar com base no e-mail enviado pelo cliente.
2. **Servidor UP:** nas Edge Function Secrets, definir `UP_FIREBASE_PROJECT_ID=rodrigues-d6566`, `UP_MANAGER_ALLOWED_ORIGINS=https://appassets.androidplatform.net` e `UP_INDEPENDENT_MANAGER_ENABLED=true`. Se hospedar painel web também, acrescentar origem HTTPS exata à lista.
3. **Cadastro e motorista:** somente após migrar os cadastros autorizados e validar a operação, habilitar `UP_INDEPENDENT_REGISTRATION_ENABLED=true` e `UP_INDEPENDENT_DRIVER_ENABLED=true`. O APK legado não é trocado automaticamente.
4. **FCM:** guardar JSON da conta de serviço **da Firebase** somente em segredo do servidor `UP_FCM_SERVICE_ACCOUNT_JSON`, `UP_FIREBASE_PROJECT_ID`, `UP_PUSH_WORKER_TOKEN`. Após criar cron e permissões, habilitar `UP_PUSH_ENABLED=true`.
5. **Comunicação entre bancos:** gerar segredo aleatório de pelo menos 32 caracteres para `UP_BRIDGE_SECRET` em ambos os bancos. Na loja, configurar `UP_BRIDGE_ENDPOINT` para `https://gkwmqwtktedarodvqqtn.supabase.co/functions/v1/up-order-bridge`; não habilitar `UP_BRIDGE_ENABLED` sem testar primeiro.
6. **Retorno à loja:** `UP_STATUS_STORE_ENDPOINT` no UP aponta para `https://fdqqwdplprzpqufpgdrm.supabase.co/functions/v1/up-status-receiver`; na loja `UP_STATUS_CALLBACK_ENABLED=true` somente após integração aprovada. No UP `UP_STATUS_WORKER_TOKEN`, `UP_STATUS_ENABLED=true`.
7. **Mapa do cliente:** `UP_TRACKING_SECRET` aleatório e diferente do segredo de bridge nos dois Supabase. O UP recebe `UP_CUSTOMER_ALLOWED_ORIGINS=https://rodriguesacaiecia.web.app`. Habilitar `UP_CUSTOMER_TRACKING_ENABLED=true` em ambos somente quando os endpoints e o site estiverem integrados.
8. **Agenda automática (opcional):** após configurar segredos no Supabase Vault, executar somente no novo projeto `supabase/up-schedulers-optional.sql`; dispara notificações e status a cada minuto e remove GPS mais antigo conforme retenção de sete dias. Não instalar esse cron na loja.
9. **Virada controlada:** cadastrar motoboys de teste, verificar ofertas, GPS e progresso do cliente. Migrar os motoristas atuais por canal seguro; não exportar CPF, documentos ou posições para GitHub. Manter rollback para APK e UP Gestor antigos.

## APK do UP Gestor separado

[Baixar APK de prévia](https://github.com/Rodriguesac/UpEntregas/releases/download/up-gestor-independent-preview/UP-Gestor-Independente-Previa.apk)

O APK usa pacote `com.rodriguesacai.upgestor.independente`, não substitui o Gestor antigo. A prévia é instalada com `UP_MANAGER_CONFIG.enabled=false` enquanto o backend não estiver autorizado. Não é produção ativa.

**Importante:** publicar Functions não configura os segredos privados. Todos os recursos externos permanecem desativados por padrão até ativação consciente e validação com usuários fictícios.


## Migração privada de entregadores — sem levar GPS para a loja nova

A preparação da migração de pessoas e financeiro também possui operação independente em GitHub Actions:

[Iniciar migração de entregadores pelo GitHub Actions](https://github.com/Rodriguesac/UpEntregas/actions/workflows/migrate-up-private.yml)

O workflow `Migrar entregadores e financeiro ao UP independente (manual)` aceita:
- `target_project_ref` = `gkwmqwtktedarodvqqtn` (exato).
- `dry_run` = **verdadeiro por padrão**: somente simula contagens e verifica se a API nova está operacional.
- `dry_run` = falso **somente com autorização explícita** `AUTORIZO-MIGRAR-UP` no campo `approval`.

Antes de executar, salvar as credenciais **somente como GitHub Actions Secrets**, nunca no código:
- `UP_OLD_SERVICE_ROLE`: chave `sb_secret_` ou JWT legado service_role do Supabase da loja.
- `UP_NEW_SERVICE_ROLE`: chave `sb_secret_` ou JWT legado service_role do Supabase independente.

A chave pública `sb_publishable_` fornecida anteriormente **não** serve para migração. Chaves novas `sb_secret_` são enviadas no cabeçalho `apikey`, **nunca** como `Authorization: Bearer`; JWT legado service_role usa ambos. O migrador recusa chaves públicas e qualquer destino diferente.

**Nenhum registro é copiado na simulação**. Com aprovação, somente dados de entregadores, candidaturas, carteiras e repasses são copiados; a origem permanece intocada. O migrador não transfere pedidos, recibos de clientes nem coordenadas GPS e deixa os entregadores inicialmente offline. A transferência só ocorre após a estrutura SQL do UP já estar instalada e o destino não conter motoristas ativos ou dados operacionais de outra origem.

**Situação:** migrador versionado, porém ainda não executado porque as credenciais privadas da segunda conta não estão conectadas ao ambiente.
