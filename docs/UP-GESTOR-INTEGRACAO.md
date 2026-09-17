# Integração UP Entregas ↔ UP Gestor Web

Fluxo operacional integrado:

1. O entregador cria ou reenviа o cadastro no aplicativo UP Entregas.
2. O aplicativo salva o perfil operacional no Firebase e envia os dados administrativos ao Supabase pela Edge Function `driver-registration`, autenticada pelo token Firebase do próprio entregador.
3. O cadastro aparece na área **Cadastros** do UP Gestor Web.
4. O gestor pode aprovar, solicitar correção, reprovar ou bloquear.
5. Ao abrir ou atualizar a situação no aplicativo, o UP consulta a decisão administrativa e sincroniza o status para o cadastro operacional Firebase.
6. Somente o cadastro aprovado e ativo segue para presença, ofertas, corridas e rastreamento.
7. Entregas concluídas alimentam o financeiro/repasse do entregador no Gestor Web.

O aplicativo não possui ação para se autoaprovar; a decisão administrativa permanece no Gestor Web.
