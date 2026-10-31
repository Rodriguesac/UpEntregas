# UP Entregas — avisos FCM de ofertas direcionadas

## Arquitetura existente aproveitada

- O Android recebe mensagens **data-only** em `UPMessagingService.onMessageReceived` e mostra notificações em `NotificationHelper`, canal `up_rides_v23`.
- O app salva e atualiza seu token no Supabase pelo `SupabaseDriverApi.token()`; o servidor mantém dispositivos em `public.fcm_devices` com `owner_type='courier'`, `owner_id` igual ao ID interno do motoboy e `app='up_entregas'`.
- O Gadministra só envia FCM **depois de persistir** uma oferta dirigida a um motoboy disponível e autorizado.
- O token, o servidor e a conta de serviço não ficam no HTML, GitHub ou APK. O FCM serve para despertar o aplicativo; o aceite e a identidade devem ser validados novamente pela Edge Function `driver-api`.

## Protocolo de mensagem

```json
{
  "message": {
    "token": "<FCM TOKEN PRIVADO>",
    "data": {
      "rideId": "<UUID DO PEDIDO>",
      "mission_id": "<UUID DO PEDIDO>",
      "missionType": "rides",
      "targetDriverId": "<UID FIREBASE DO MOTOBOY>",
      "codigoPedido": "RAC-28",
      "source": "gadministra"
    },
    "android": {
      "priority": "HIGH",
      "ttl": "180s",
      "restricted_package_name": "com.rodriguesacai.entregador"
    }
  }
}
```

Nenhum endereço, telefone ou preço é enviado pelo FCM. O UP carrega dados da missão pela API autenticada.

## Configuração ainda necessária antes da homologação real

1. Criar **ambiente de homologação de banco e Edge Functions isolado dos pedidos reais**.
2. Cadastrar a conta de serviço com permissão Firebase Cloud Messaging API como segredo **`FIREBASE_SERVICE_ACCOUNT_JSON`** nesse ambiente. Nunca colar credenciais em chats, fontes públicas ou commits.
3. Garantir que a API FCM HTTP v1 esteja habilitada no projeto Firebase de destino `rodrigues-d6566`. Conta de serviço de outro projeto precisa das permissões IAM de envio ao destino.
4. Implantar **coordenadamente** `gadm-admin` do Cliente e `driver-api` do UP. Não usar funções de produção para testes.
5. Usar dois telefones de teste, confirmar token ativo, oferta direcionada ao aparelho correto, aviso em primeiro/segundo plano, aceite, recusa, recolhimento, expiração e fallback de polling.
6. Na API de administração, status `notification = sent` significa que FCM aceitou a chamada, **não prova entrega no dispositivo**. `not_configured`, `no_device`, `failed` e `partial` não cancelam a oferta no banco.
7. Nunca enviar avisos para pedidos cancelados ou motoboys não autenticados; a `driver-api` deve negar aceite quando a oferta não pertence àquele usuário.

## Testes automatizados

- `supabase/functions/_shared/up-fcm-client.test.mjs`: token Supabase, validação de sessão, recepção Android.
- No repositório do cliente: `supabase/functions/_shared/courier-offer-fcm.test.mjs`: assinatura OAuth, endpoint FCM v1, envio sem credenciais e duplicidade de tokens.
- O teste local usa credencial RSA descartável e transporte FCM simulado. **Nenhum push real é disparado por CI.**

## Compatibilidade e custos

Usar FCM como mecanismo de aviso, mantendo a consulta econômica à fila Supabase como fallback. Push não é garantia de entrega imediata (restrições de bateria, aparelho offline, permissões). A conta de serviço nunca deve ser registrada em log nem devolvida à interface.
