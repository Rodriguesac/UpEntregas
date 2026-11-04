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
