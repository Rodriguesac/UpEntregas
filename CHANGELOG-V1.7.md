# UP Entregas v1.7.0 — operação + códigos

- Corrida ativa mostra cliente, endereço, WhatsApp, pagamento e valor a cobrar.
- Fluxo de retirada reforçado com validação do código da loja.
- Ao sair para entrega, `aguardandoCodigoEntrega=true` também é sincronizado no pedido para o Cliente.
- Finalização exige o código do cliente sempre que houver código/obrigatoriedade configurada.
- CPF do perfil não mostra mais o UID do Firebase; usa o CPF cadastrado e mascarado.
- Veículo foi organizado; bicicleta mostra placa como “Não se aplica”.
- Chave Pix passa a ler `pixChave` e pode ser cadastrada/alterada por solicitação para aprovação da loja.
- “Ganhos e acerto” foi reorganizado e agora permite abrir as pendências de conferência.
- Texto “aguardando o GADM” substituído por “aguardando novas corridas”.
- Mantidos despacho manual, sem reenvio automático, GPS por missão, FCM e serviço em primeiro plano.
