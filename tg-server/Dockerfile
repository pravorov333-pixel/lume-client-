# Playwright's official image ships a matching headless Chromium + all the system
# libs Chromium needs (this is exactly the piece a bare Node/Nixpacks image lacks —
# see the "why Electron doesn't just deploy" note in main.js's header comment).
FROM mcr.microsoft.com/playwright:v1.61.1-jammy

WORKDIR /app

COPY package*.json ./
RUN npm install --omit=dev

COPY main.js ./

ENV NODE_ENV=production
EXPOSE 8077

CMD ["node", "main.js"]
