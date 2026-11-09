# UP Entregador — aplicativo independente, novo

Este módulo é um Android **nativo feito do zero**, separado do APK legado `:app` e do Gestor.
Nenhuma rotina de pedido, GPS ou cadastro aponta para o Supabase da loja. O aplicativo usa apenas
`gkwmqwtktedarodvqqtn` e as Edge Functions independentes, com Firebase Authentication e FCM
para identidade e notificações.

## Princípios operacionais

- Login do entregador pelo Firebase (JWT verificado no servidor).
- Cadastro enviado para revisão do gestor, sem autoaprovação.
- HOME com online/offline, oferta direcionada, corrida e histórico reais (nunca dados inventados).
- Uma ação principal por etapa: aceitar → ir à loja → cheguei → retirada → ir ao cliente → cheguei → código e recebimento → concluir.
- GPS somente durante missão ativa, com notificação de serviço em primeiro plano, sem localização de fundo silenciosa.
- FCM para acordar app e consulta de ofertas como fallback; nunca depende de polling a cada poucos segundos.
- Navegação externa sob ação explícita do entregador; nenhum mapa simulado.
- Não aceita `service_role`, segredos HMAC ou tokens de gestão no APK.

## Para executar

O módulo `:up-driver-next` usa um **applicationId separado**:
`com.rodriguesacai.updriver.independente`, portanto não substitui o aplicativo anterior.

`gradle :up-driver-next:assembleDebug`

O build é de **prévia técnica**. Antes de liberar corridas reais:
1. Registrar este package como app Android no projeto Firebase `rodrigues-d6566` e baixar
   seu `google-services.json`, substituindo a inicialização Firebase temporária da prévia
   por configuração específica do package e confirmando FCM.
2. Publicar e auditar a Edge Function `driver-registration-independent` no Supabase do UP;
   manter a flag `UP_INDEPENDENT_REGISTRATION_ENABLED` desligada até validação.
3. Revisar e aprovar os motoboys reais no UP Gestor; não aprovar importados automaticamente.
4. Habilitar `UP_INDEPENDENT_DRIVER_ENABLED` somente após um teste de oferta/aceite/GPS/retorno
   com um motoboy autorizado e um pedido de teste deliberado.
5. Definir esquema de assinatura de release e distribuir uma versão assinada, não a APK debug.

A chave `sb_publishable_` do BuildConfig é pública; todas as credenciais privilegiadas
permanecem nas Edge Functions.
