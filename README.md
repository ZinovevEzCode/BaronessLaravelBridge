# 🚀 LimboAuth Integration — Laravel ↔ Minecraft Auth Bridge

**LimboAuth Integration** — это готовое решение для синхронизации пользователей между  
🔹 Minecraft-плагином **LimboAuth** и  
🔹 сайтом / панелью на **Laravel**.

---

## 🧩 Что это даёт

- 🔐 Общая система аккаунтов между сайтом и сервером Minecraft  
- 🔁 Двусторонняя синхронизация (регистрация / смена пароля / удаление)  
- ⚙️ Redis-очередь для фоновой обработки вебхуков  
- 🧠 Кеширование и fallback при недоступности LimboAuth  
- 🪶 Простое REST API: `/api/auth/login`, `/api/auth/register`, `/api/auth/user/{name}`  
- 🔔 Discord / Slack уведомления о сбоях  
- 🪵 Таблица логов вебхуков с Artisan-командой мониторинга  

---

## 🏗️ Архитектура

```
+-------------------+        HTTP/Webhooks        +--------------------+
|  LimboAuth Plugin |  <----------------------->  |   Laravel Backend  |
|  (Minecraft)      |                            |  (API + Database)  |
+-------------------+                            +--------------------+
         ↑                                                  ↑
         | REST API                                         | Redis Queue
         ↓                                                  ↓
  +-------------------+                           +-------------------+
  |  Player Login /   |                           | Background Jobs / |
  |  Password Change  |                           | Webhook Handlers  |
  +-------------------+                           +-------------------+
```

---

## 📦 Состав репозитория

```
limboauth-integration/
├── plugin/                # Java плагин для Minecraft (LimboAuthAPI)
│   ├── src/main/java/com/example/limboauthapi/
│   │   ├── LimboAuthAPI.java
│   │   ├── Main.java
│   │   └── DatabaseManager.java
│   ├── src/main/resources/
│   │   ├── plugin.yml
│   │   └── config.yml
│   └── pom.xml
│
└── laravel/               # Laravel API интеграция
    ├── app/
    │   ├── Http/Controllers/
    │   │   ├── AuthController.php
    │   │   └── LimboAuthWebhookController.php
    │   ├── Jobs/ProcessLimboAuthWebhook.php
    │   ├── Models/LimboAuthWebhook.php
    │   └── Services/LimboAuthService.php
    ├── database/migrations/xxxx_create_limboauth_webhooks_table.php
    ├── routes/api.php
    ├── .env.example
    ├── composer.json
    └── README.md
```

---

## ⚙️ Установка

### 🧱 1. Minecraft-плагин

#### Требования
- Java 17+
- Paper / Spigot / Purpur 1.19+
- Maven

#### Установка
```bash
cd plugin
mvn clean package
```
После сборки файл появится в:
```
plugin/target/limboauth-api-1.0.0.jar
```

Скопируй `.jar` в папку `plugins/` сервера Minecraft.

#### Конфигурация (`config.yml`)
```yaml
api:
  key: supersecretkey
  url: "https://your-laravel-site.com/api"
database:
  type: "sqlite"
  path: "plugins/LimboAuthAPI/users.db"
webhook:
  url: "https://your-laravel-site.com/api/limboauth/webhook"
  secret: "my-webhook-secret"
```

---

### 🌐 2. Laravel-сайт

#### Установка
```bash
cd laravel
composer install
cp .env.example .env
php artisan key:generate
```

Настрой `.env`:
```env
APP_URL=https://your-laravel-site.com
DB_CONNECTION=mysql
DB_DATABASE=limboauth
DB_USERNAME=root
DB_PASSWORD=secret

LIMBOAUTH_API_URL=http://mc-server:8080/api
LIMBOAUTH_API_KEY=supersecretkey
LIMBOAUTH_WEBHOOK_SECRET=my-webhook-secret

QUEUE_CONNECTION=redis
REDIS_HOST=127.0.0.1
```

Запусти миграции:
```bash
php artisan migrate
```

---

## 🔁 Взаимодействие Laravel ↔ LimboAuth

### 📤 Laravel → LimboAuth API
| Метод | URL | Описание |
|--------|-----|-----------|
| `POST /api/auth/login` | Проверка логина/пароля на сервере Minecraft |
| `POST /api/auth/register` | Регистрация пользователя через LimboAuth |
| `GET /api/auth/user/{name}` | Получить информацию о пользователе |
| `PUT /api/auth/password` | Обновить пароль через API |

Laravel вызывает эти методы, обращаясь к Minecraft API (через HTTP или локальный порт).

---

### 📥 LimboAuth → Laravel Webhook
LimboAuth плагин отправляет запросы на:
```
POST /api/limboauth/webhook
```

**Пример:**
```json
{
  "event": "user.registered",
  "data": {
    "username": "Steve",
    "password": "hashed_pass_here"
  }
}
```

Laravel:
1. Проверяет HMAC-подпись заголовка `X-LimboAuth-Signature`
2. Ставит задачу в очередь (`ProcessLimboAuthWebhook`)
3. В фоне добавляет/обновляет пользователя в БД
4. Логирует событие в таблице `limboauth_webhooks`

---

## ⚙️ Очереди и фоновые задачи

Запускаем воркеры:
```bash
php artisan queue:work --queue=default --sleep=3
```

Laravel обрабатывает события LimboAuth асинхронно,  
и пишет результаты в таблице `limboauth_webhooks`:

| ID | Event | Status | Created | Error |
|----|--------|---------|----------|--------|
| 12 | user.password_changed | success | 14:05:22 | - |
| 11 | user.registered | success | 14:03:17 | - |
| 10 | user.deleted | failed | 13:59:04 | Unknown player username |

---

## 📊 Мониторинг и диагностика

### 🔍 Просмотр последних вебхуков
```bash
php artisan limboauth:webhooks:list --limit=10
```

### 🔁 Повтор неудачных заданий
```bash
php artisan queue:retry all
```

### 💬 Уведомления
Можно настроить `.env` для Discord уведомлений:
```env
DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/XXXX/YYY
```

---

## 🧠 Fallback-режим
Если Minecraft-сервер недоступен:
- Laravel может использовать локальный кэш пользователя (Redis / DB)
- Разрешить временный вход при совпадении хеша пароля

Это обеспечивает стабильную работу сайта даже при оффлайне сервера.

---

## 🧰 Команды разработчика

| Команда | Назначение |
|----------|------------|
| `php artisan queue:work` | запуск фоновых заданий |
| `php artisan limboauth:webhooks:list` | просмотр последних событий |
| `php artisan tinker` | отладка API вручную |
| `php artisan migrate:fresh` | очистка БД и миграция заново |

---

## 🧱 Примеры запросов

**1️⃣ Регистрация**
```bash
curl -X POST https://your-laravel-site.com/api/auth/register  -H "Content-Type: application/json"  -d '{"username":"Steve","password":"12345"}'
```

**2️⃣ Авторизация**
```bash
curl -X POST https://your-laravel-site.com/api/auth/login  -H "Content-Type: application/json"  -d '{"username":"Steve","password":"12345"}'
```

**3️⃣ Получение пользователя**
```bash
curl https://your-laravel-site.com/api/auth/user/Steve
```

**4️⃣ Webhook от LimboAuth**
```bash
curl -X POST https://your-laravel-site.com/api/limboauth/webhook  -H "Content-Type: application/json"  -H "X-LimboAuth-Signature: <подпись>"  -d '{"event":"user.registered","data":{"username":"Steve","password":"abc"}}'
```

---

## 🔒 Безопасность

- Все запросы между Laravel и плагином подписываются через `HMAC-SHA256`
- API-ключ в заголовке `X-API-Key`
- Webhook-секрет для проверки целостности
- Все пароли хешируются (bcrypt / Argon2)
- Рекомендуется HTTPS для всех соединений

---

## 📘 Лицензия
MIT © 2025 — LimboAuth Integration Project

---

## ❤️ Авторы и благодарности
Создано как шаблон интеграции для Minecraft-аутентификации между LimboAuth и Laravel.  
Идеально подходит для игровых порталов, магазинов и систем доната, где игроки должны  
иметь общую учётную запись на сайте и в игре.
