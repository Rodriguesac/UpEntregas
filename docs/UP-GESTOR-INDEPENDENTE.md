# UP Gestor independente — versão de preparação

**Situação em 08/11/2026:** painel, API e funções SQL implementados e testados em PostgreSQL 17 descartável. **NÃO PUBLICADOS EM PRODUÇÃO.**

O GADMINISTRA cuida de **pedido, pagamento e preparo**. O **UP Gestor** cuida de entregador, candidatura, oferta de corrida, GPS, andamento, ocorrências, carteira e repasse. O banco novo do UP **ainda não foi criado** porque a conta Supabase está no limite de projetos gratuitos; a loja está preservada.

## Arquivos

- `web-gestor-independente/`: nova interface web mobile-first, sem atalhos redundantes e sem atualização de formulários durante a digitação; senha Firebase não fica gravada no navegador.
- `supabase/functions/up-manager-independent/index.ts`: API administrativa, autenticação Firebase JWT RS256 e lista de permissões lida a cada solicitação do banco UP.
- `supabase/up-manager-independent.sql`: conta de gestor autorizada, revisão de cadastro, ofertas exclusivas, suspensão, solicitação e confirmação manual de repasse, tudo registrado em auditoria.
- `supabase/up-independent-schema.sql` e `supabase/up-driver-operations.sql`: banco e funções do entregador necessários antes de implantar o painel.
- `scripts/verify-up-manager-postgres.sh`: testes reais em PostgreSQL de aprovação, oferta única, concorrência com entregadores, pagamento e privilégios.

## Áreas do UP Gestor

1. **Visão geral:** corrida ativa, esperando entregador, entregador online, cadastro pendente.
2. **Corridas:** lista, andamento, detalhes, eventos e envio de oferta para um entregador disponível.
3. **Entregadores:** situação, disponibilidade, GPS recente e suspensão/reativação (bloqueada se a corrida está ativa).
4. **Cadastros:** listar, revisar documentos, aprovar, solicitar correções e reprovar. O novo entregador **não** é aprovado por preencher cadastro.
5. **Financeiro:** consulta de saldos, solicitações de repasse e registro de pagamento **já efetuado** com referência. A API não realiza transferência Pix. Impede repasse acima do saldo.
6. **Ocorrências:** registros enviados pelo aplicativo do entregador.
7. **Auditoria:** acessível somente ao proprietário, com trilha de alteração.

## Perfis e segurança

| Perfil | Consulta geral | Oferta de corrida | Revisão e suspensão | Financeiro | Auditoria |
| --- | --- | --- | --- | --- | --- |
| OWNER | sim | sim | sim | sim | sim |
| ADMIN | sim | sim | sim | sim | não |
| DISPATCHER | sim | sim | não | não | não |
| VIEWER | sim | não | não | não | não |

- Autenticação Firebase via usuário real; servidor valida JWT e consulta `up_manager_users` no banco independente. **Nenhum usuário administrador padrão é criado.**
- A tabela `up_manager_users` tem RLS habilitado e não permite leitura ou edição por `anon` ou `authenticated`.
- O painel web **não recebe** chaves privadas, `service_role`, PIN compartilhado ou segredo HMAC.
- A API administrativa começa desativada com `UP_INDEPENDENT_MANAGER_ENABLED` ausente ou diferente de `true`.
- A variável de servidor `UP_MANAGER_ALLOWED_ORIGINS` restringe as origens web autorizadas.
- As atualizações de dados só ocorrem após confirmar as ações. Não há atualização automática periódica.
- Os cadastros não têm aprovação automática e o financeiro não dispara transferência real.

## Implantação futura (apenas depois de criar o Supabase exclusivo)

1. Criar `up-entregas` em outra vaga gratuita ou forma aprovada pelo proprietário.
2. Aplicar **somente no projeto UP** os SQLs nesta ordem:
   - `supabase/up-independent-schema.sql`
   - `supabase/up-driver-operations.sql`
   - `supabase/up-manager-independent.sql`
3. No Firebase do UP, criar/confirmar a conta de administrador. Copiar **somente o UID** verificado.
4. Inserir o primeiro gestor **manualmente** no novo Supabase usando o editor SQL restrito:
   ```sql
   insert into public.up_manager_users
       (firebase_uid,display_name,role,active)
   values ('FIREBASE_UID_DO_PROPRIETARIO','Proprietário UP','OWNER',true);
   ```
   Nunca colocar UID verdadeiro nem credenciais no repositório.
5. Implantar a Edge Function `up-manager-independent` **apenas no novo projeto** com verificação JWT de plataforma configurada para a autenticação Firebase validada pela própria função. Definir `UP_FIREBASE_PROJECT_ID`, `UP_MANAGER_ALLOWED_ORIGINS` (origem HTTPS exata) e `UP_INDEPENDENT_MANAGER_ENABLED=true` somente após os testes.
6. Em `web-gestor-independente/config.js`, preencher **somente** a Web API key pública do Firebase e o endereço HTTPS do endpoint próprio do UP.
7. Implantar `web-gestor-independente/` em hospedagem HTTPS independente, sem substituir o painel antigo. O pipeline gera um ZIP de prévia, mas **não publica automaticamente**.
8. Testar perfis e permissões, aprovação de cadastro, oferta e aceite, etapas de corrida, GPS, pagamentos simulados, callbacks para a loja e rollback antes de liberar entregadores reais.
9. Só depois, apontar APK e painel UP para o novo Supabase. O GADMINISTRA continua sem módulo Entregas.

## Validação

O workflow `Validar arquitetura UP independente` verifica o TypeScript das Edge Functions com Deno, a sintaxe do JavaScript do navegador e executa as funções SQL e cenários de segurança em PostgreSQL 17 descartável. O ZIP `UP-Gestor-Independente-Previa` pode ser obtido na página da execução do GitHub Actions.

**Não interpretar testes de código como teste de ponta a ponta de Firebase, notificações, cobrança ou rastreamento em produção.** A criação do projeto próprio permanece o bloqueio para esse tipo de validação.


## Novo projeto informado para ativação — 08/11/2026

- **Referência Supabase UP:** `gkwmqwtktedarodvqqtn`.
- **URL pública:** `https://gkwmqwtktedarodvqqtn.supabase.co`.
- A chave **publishable** recebida do proprietário está registrada **somente** na configuração pública de pré-ativação `web-gestor-independente/config.js`. Não confundir com `service_role`; não lhe concede acesso administrativo.
- Chave pública do Firebase pré-preenchida a partir de `app/google-services.json` do projeto `rodrigues-d6566`. **O uso desta chave no login web ainda precisa ser testado**, pois podem existir restrições para Android.
- `enabled:false` mantém o painel **inativo** até a instalação e a validação das funções no projeto novo.
- Para autenticar com Supabase no ChatGPT, autorizar a **conta e organização que possuem o projeto acima**. Em 08/11 a conexão administrativa existente lista somente `fdqqwdplprzpqufpgdrm` (banco da loja); a consulta direta ao novo projeto foi recusada por falta de permissão.
- **Nenhuma migration, tabela, Edge Function, segredo privado ou usuário administrador foi criado no projeto novo**; também não houve mudança no banco antigo nem no APK.

Após conceder acesso administrativo, aplicar scripts SQL na ordem documentada, implantar Edge Functions com validação Firebase interna, configurar origins e segredo privado, inserir o primeiro administrador por UID verificado, validar login web, oferta e aceite, e só depois trocar a infraestrutura usada pelos entregadores.

Validação pré-ativação: [CI GitHub Actions #42461410122](https://github.com/Rodriguesac/UpEntregas/actions/runs/42461410122) — Deno, PostgreSQL descartável, frontend e pacote de prévia passaram.
