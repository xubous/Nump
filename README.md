# NUMP

NUMP é um aplicativo leve e multiplataforma desenvolvido com **Electron**, focado em organizar tarefas e utilidades do dia a dia em um único lugar.

O projeto reúne ferramentas essenciais de produtividade e está estruturado para crescer com integrações futuras.

## Funcionalidades

- Transferência de arquivos via servidor local  
- Gerenciamento de tarefas (To-Do List)  
- Cronômetro Pomodoro  
- Anotações diárias  
- Estrutura preparada para integração com backend  
- Consumo de notícias via API (planejado)

## Tecnologias utilizadas

- Electron  
- Node.js  
- JavaScript  
- HTML  
- CSS  
- npm  

## Requisitos

Antes de rodar o projeto, certifique-se de ter instalado:

- Node.js (versão LTS recomendada)
- npm

## Estrutura do projeto

Estrutura geral do repositório:

NUMP/
├── src/
│   ├── main/        # Processo principal do Electron
│   ├── renderer/   # Interface (HTML, CSS, JS)
│   └── assets/     # Imagens, ícones e arquivos estáticos
├── build/          # Arquivos gerados após o build
├── package.json
├── package-lock.json
└── README.md
Instalação

Clone o repositório:

git clone <url-do-repositorio>
cd NUMP


Instale as dependências:

npm install

Executar em modo desenvolvimento
npm start


Esse comando inicia o Electron em modo desenvolvimento.

Build do aplicativo

O projeto utiliza scripts npm para gerar os builds.

Build geral
npm run build

Build por sistema operacional

Dependendo do sistema, o Electron irá gerar os binários correspondentes.

Windows
npm run build -- --win


Gera arquivos .exe ou instalador para Windows.

Linux
npm run build -- --linux


Gera AppImage, .deb ou .rpm, conforme configuração.

macOS
npm run build -- --mac


Gera aplicativo .app ou instalador para macOS.

Os arquivos finais ficarão disponíveis na pasta build/.

Status do projeto
Projeto em desenvolvimento ativo. Funcionalidades adicionais e integrações estão em planejamento.
