# Guia de Deploy - SDR Antigravidade 🚀

Este documento orienta como subir o projeto para o GitHub e realizar o deploy no Render.

## 1. Subindo para o GitHub

1. Inicialize o repositório local:
   ```bash
   git init
   git add .
   git commit -m "Initial commit: SDR System with Antigravity"
   ```
2. Crie um repositório no seu GitHub.
3. Conecte o repositório local:
   ```bash
   git remote add origin https://github.com/SEU_USUARIO/NOME_DO_REPO.git
   git branch -M main
   git push -u origin main
   ```

## 2. Deploy no Render (Backend)

1. Crie um **Web Service** no [Render](https://dashboard.render.com/).
2. Conecte seu repositório do GitHub.
3. Configure:
   - **Runtime:** `Docker`
   - **Plan:** `Free` (ou o de sua escolha)
4. Em **Environment Variables**, adicione (CRUCIAL):
   - `GEMINI_API_KEY`: Sua chave do Gemini.
   - `GOOGLE_PLACES_API_KEY`: Sua chave do Google Maps.
   - `DATABASE_URL`: `sqlite:///./sdr_system.db` (para persistência no disco do Render, recomedamos adicionar um 'Disk' se usar o plano pago, ou usar um banco Postgres externo no Render).

## 3. Deploy no Render (Frontend)

1. Crie um **Static Site**.
2. Build Settings:
   - **Build Command:** `npm install && npm run build`
   - **Publish Directory:** `frontend/dist`
3. Environment Variables:
   - `VITE_API_URL`: A URL do seu Backend gerada pelo Render.

---

> [!IMPORTANT]
> Nunca versione o arquivo `.env`. Suas chaves de API devem ser configuradas diretamente no painel do Render.
