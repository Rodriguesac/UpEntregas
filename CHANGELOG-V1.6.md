# UP Entregas V1.6 — Cadastro & Aprovação

- login migrado para Firebase Authentication (e-mail + senha);
- criação de conta diretamente no aplicativo;
- cadastro em 6 etapas: conta, dados pessoais, veículo, documentos, Pix e revisão;
- foto/documento enviados ao Cloudinary; Firestore armazena apenas as URLs;
- novos cadastros entram como PENDENTE / AGUARDANDO_APROVACAO;
- entregador não consegue receber corridas antes da aprovação do GADM;
- tela de situação cadastral e correção solicitada;
- regras Firestore atualizadas para contas de entregador baseadas no UID do Firebase Auth;
- fluxo operacional de corridas da V1.5 preservado.

ATENÇÃO: publique firestore.rules antes de testar o cadastro novo.
