FROM python:3.12-slim
WORKDIR /app
ENV PYTHONUNBUFFERED=1 DB_PATH=/data/giftsniper.db
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY giftsniper ./giftsniper
RUN mkdir -p /data
# Нужны переменные: BOT_TOKEN (обязательно). Остальное настраивается в самом боте.
CMD ["python", "-m", "giftsniper", "run"]
