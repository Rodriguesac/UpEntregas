# Teste obrigatório — UP Entregas v1.7.0

1. Entrar com entregador aprovado e ficar ONLINE.
2. Gestor enviar uma corrida direcionada.
3. Confirmar alerta de oferta e aceitar.
4. Conferir nome/endereço/WhatsApp/pagamento/valor a cobrar.
5. Chegar à loja e tentar código de retirada errado: deve bloquear.
6. Informar código de retirada correto: deve mudar para SAIU_PARA_ENTREGA.
7. Conferir no Firestore/pedido: `aguardandoCodigoEntrega=true`.
8. Abrir mapa do cliente e marcar chegada.
9. Tentar código de entrega errado: deve bloquear.
10. Informar código correto e valor recebido: deve finalizar.
11. Conferir `rides`, `pedidos`, entregador e `acertosEntregadores`.
12. Em Ganhos e acerto, abrir “VER PENDÊNCIAS”.
13. Em Conta e veículo, confirmar CPF mascarado, placa correta e Pix.
14. Testar “CADASTRAR/ALTERAR PIX” e confirmar criação em `suporteEntregador`.
