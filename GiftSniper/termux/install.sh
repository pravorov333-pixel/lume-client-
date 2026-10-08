#!/data/data/com.termux/files/usr/bin/bash
# Установка Gift Sniper на Android-телефон через Termux (одной командой).
#
#   curl -sL https://raw.githubusercontent.com/pravorov333-pixel/lume-client-/ccr-ac390c81-vj2x5m/GiftSniper/termux/install.sh | bash
#
# Внутри Termux ставится Debian (proot-distro): там у всех библиотек есть готовые сборки под ARM.
set -e

REPO="https://github.com/pravorov333-pixel/lume-client-.git"
BRANCH="${GS_BRANCH:-ccr-ac390c81-vj2x5m}"

echo "==> 1/4 Пакеты Termux"
pkg update -y
pkg install -y proot-distro

echo "==> 2/4 Debian (первый раз ~5–10 минут)"
proot-distro install debian 2>/dev/null || echo "Debian уже установлен"

echo "==> 3/4 Python и бот"
proot-distro login debian -- bash -c "
  set -e
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -qq
  apt-get install -y -qq python3 python3-venv git ca-certificates >/dev/null
  rm -rf /root/gs
  git clone -q --depth 1 -b '$BRANCH' '$REPO' /root/gs
  python3 -m venv /root/gs-venv
  /root/gs-venv/bin/pip install -q --upgrade pip
  /root/gs-venv/bin/pip install -q -r /root/gs/GiftSniper/requirements.txt
"

echo "==> 4/4 Токен бота"
TOKEN=""
while [ -z "$TOKEN" ]; do
  read -r -p "Вставьте токен бота от @BotFather: " TOKEN < /dev/tty
done
proot-distro login debian -- bash -c "printf 'BOT_TOKEN=%s\nDB_PATH=/root/giftsniper.db\n' '$TOKEN' > /root/gs.env && chmod 600 /root/gs.env"

# Команда gs: gs — запустить, gs update — обновить код, gs token — сменить токен
cat > "$PREFIX/bin/gs" <<'EOF'
#!/data/data/com.termux/files/usr/bin/bash
case "$1" in
  update)
    proot-distro login debian -- bash -c "cd /root/gs && git pull -q && /root/gs-venv/bin/pip install -q -r GiftSniper/requirements.txt && echo 'Обновлено'"
    ;;
  token)
    read -r -p "Новый токен бота: " TOKEN
    proot-distro login debian -- bash -c "sed -i 's|^BOT_TOKEN=.*|BOT_TOKEN=$TOKEN|' /root/gs.env && echo 'Токен сохранён'"
    ;;
  *)
    termux-wake-lock 2>/dev/null || true   # не даём Android усыпить процесс
    echo "Gift Sniper запущен. Остановить — Ctrl+C (или закрыть сессию Termux)."
    proot-distro login debian -- bash -c "set -a; . /root/gs.env; set +a; cd /root/gs/GiftSniper && exec /root/gs-venv/bin/python -m giftsniper run"
    ;;
esac
EOF
chmod +x "$PREFIX/bin/gs"

echo
echo "✅ Готово! Запуск: gs"
echo "   Затем в Telegram откройте своего бота и отправьте /start"
