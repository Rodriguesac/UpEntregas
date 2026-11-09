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

## Implementado nesta branch

- App **novo** de pacote separado com Material 3, identidade visual UP e telas próprias.
- Login via Firebase, formulário de candidatura, aprovação pendente, estado online/offline.
- Ofertas reais por entregador, aceite/recusa, etapas de entrega e confirmação de código.
- Endereço de retirada/entrega e navegação ao Google Maps sob ação explícita.
- GPS de missão via foreground service, limites de atualização e recusa de acesso sem autorização.
- Push FCM com registro do token verificado no backend; consulta a cada 20 s como contingência.
- Histórico de corridas, ocorrências, leitura de lançamentos financeiros e repasses.
- Sessão e disponibilidade sincronizadas com o servidor; `driver-api-independent` v14.
- Cadastro independente via `driver-registration-independent` v2.
- Pacote antigo `:app` permanece intacto e não é substituído.
- Build de prévia em `.github/workflows/build-up-driver-next.yml`.

## Aceitação operacional (sem dados falsos)

- [ ] Instalar APK novo ao lado do antigo no Android.
- [ ] Registrar applicationId próprio e SHA da assinatura no Firebase; confirmar Auth e FCM.
- [ ] Verificar JWT do entregador e cadastrar documentação real para revisão do gestor.
- [ ] Conferir documentos e aprovar apenas o entregador de teste.
- [ ] Habilitar gradualmente as flags privadas da infraestrutura UP.
- [ ] Deixar online e confirmar presença correta após reinicializar o Android.
- [ ] Despachar somente um pedido de teste, pago/validado e com endereço de coleta ativo.
- [ ] Receber oferta, aceitar e conferir atribuição exclusiva.
- [ ] Concluir cinco etapas na ordem sem duplicar eventos, incluindo código e cobrança se aplicáveis.
- [ ] Confirmar GPS visível apenas na missão, permissão recusada e desligamento automático.
- [ ] Verificar histórico do entregador e extrato sem mostrar dados de outros motoristas.
- [ ] Confirmar callback assinado para a loja, push ao cliente e acompanhamento por ticket temporário.
- [ ] Assinar APK de lançamento com chave de produção (não reutilizar debug) e promover com rollback.

**Não expor segredos no APK. Não ativar corrida real sem essa validação ponta a ponta.**

## Configuração Firebase individual para habilitar FCM

O Android do novo UP precisa ser cadastrado no Firebase `rodrigues-d6566` com o
**package exato** `com.rodriguesacai.updriver.independente`.

Não reutilizar a entrada de app Firebase do entregador antigo. A compilação de
prévia funciona com o login Firebase da mesma conta, mas agora marca
`UP_FIREBASE_ANDROID_REGISTERED=false` e **não afirma que o FCM funciona**.

O build automatizado aceita o GitHub Actions secret
`UP_DRIVER_FIREBASE_GOOGLE_SERVICES_B64`, contendo o JSON fornecido pelo Firebase
codificado em base64. Antes de compilar, o workflow verifica **project_id,
project_number e Android package_name**, extrai somente App ID e API key públicos
e os injeta no APK. Sem configuração, gera prévia com notificações push bloqueadas.

Não subir service account, token privado, `service_role` ou HMAC nesse secret:
o valor esperado é o arquivo de configuração **Android google-services.json**,
que contém apenas metadados públicos da aplicação Firebase. O servidor FCM
usa outra credencial privada armazenada nas Edge Function Secrets.

Para distribuição, validar o Firebase via login/FCM e assinar com chave de release.
