# Preparação do APK UP independente — branch segura

Este ajuste **não altera o APK instalado**, não publica um novo APK e mantém
os endpoints da loja como padrão. A build oficial do UP Entregas só muda
de banco quando todas as propriedades forem fornecidas deliberadamente.

## Build isolada (quando a API de cadastro estiver disponível)

```bash
gradle :app:assembleDebug \
  -PupDriverApiUrl=https://gkwmqwtktedarodvqqtn.supabase.co/functions/v1/driver-api-independent \
  -PupRegistrationApiUrl=https://gkwmqwtktedarodvqqtn.supabase.co/functions/v1/driver-registration-independent \
  -PupPublishableKey=sb_publishable_-G8dzZ51cG9ZTdVviPAQdQ_9oSnwCyG
```

As APIs de corrida e cadastro devem pertencer ao **mesmo host HTTPS**;
a build falha se houver mistura de contas. A publishable key é pública,
mas precisa pertencer ao projeto escolhido.

**Não distribuir essa build para motoristas ainda.** O endpoint de
`driver-registration-independent` ainda não foi publicado; faltam os
secrets, autorização e migração controlada de entregadores.
O app antigo segue com os padrões originais e não é afetado.

Não incluir `service_role`, segredos HMAC, chave de gestão ou token privado no APK.
