# Pineapple Shop

Русский · [English](#pineapple-shop-english)

Telegram-бот для двух пользователей: администратор начисляет баллы участнику, участник тратит их на вещи из каталога. Баллы, остатки товаров и возвраты учитываются в PostgreSQL, все операции с балансом атомарны.

## Содержание

- [Возможности](#возможности)
- [Стек](#стек)
- [Архитектура](#архитектура)
- [База данных](#база-данных)
- [Конфигурация](#конфигурация)
- [Запуск](#запуск)
- [Docker](#docker)
- [Бэкапы](#бэкапы)
- [Нюансы и типичные проблемы](#нюансы-и-типичные-проблемы)
- [Что не реализовано](#что-не-реализовано)

## Возможности

В системе ровно два пользователя. Роль определяется при первом `/start`: если Telegram id совпадает с `ADMIN_TELEGRAM_ID`, пользователь становится администратором (`ADMIN`), иначе участником (`MEMBER`).

### Участник (MEMBER)

Меню: `💰 Баланс`, `🎯 Как заработать`, `🛍 Каталог товаров`.

- Баланс: текущее количество баллов.
- Как заработать: справочник способов получения баллов с описанием и ориентировочной ценой. Справочник информационный, на начисление он не влияет.
- Каталог: список доступных товаров (название, цена, остаток) → карточка товара → покупка с подтверждением. Обрабатываются ситуации «недостаточно баллов» и «товар только что закончился».

### Администратор (ADMIN)

Меню: `💸 Начислить`, `🛍 Каталог товаров`, `➕ Добавить товар`, `🎯 Способы заработка`, `📜 История покупок`, `🧾 История баллов`.

- Начисление баллов участнику: сумма → комментарий → подтверждение.
- Добавление товара: название → описание → цена → остаток → подтверждение.
- Управление товаром из карточки каталога: пополнение остатка, деактивация (товар исчезает из каталога).
- Справочник способов заработка: добавление, изменение описания и баллов, удаление. Удаление мягкое (`is_active = false`), запись остаётся в БД.
- История покупок участника с кнопкой возврата у каждой незавершённой покупки. Возврат восстанавливает баллы и остаток товара.
- История баллов: журнал начислений, покупок и возвратов, по 10 записей на страницу.

Любая кнопка главного меню и команда `/start` прерывают текущий многошаговый сценарий.

### Уведомления участнику

Бот сам пишет участнику, когда:

- администратор начислил баллы;
- администратор вернул покупку;
- в каталоге появился новый товар (с остатком больше нуля);
- добавлен новый способ заработка.

Ошибка отправки (например, участник заблокировал бота) пишется в лог и не откатывает операцию администратора.

## Стек

- Java 25, Spring Boot, Spring Data JPA
- PostgreSQL 17.9
- [java-telegram-bot-api](https://github.com/pengrad/java-telegram-bot-api) (pengrad), получение апдейтов через long polling
- Gradle, Docker, Docker Compose

## Архитектура

Монолит с чётким разделением слоёв: Telegram-слой ничего не знает о JPA-запросах, сервисы ничего не знают о Telegram.

```
ru.dogernout.pineapple_shop
├── config/           настройки (свойства, бин TelegramBot), запуск polling после старта контекста
├── bot/
│   ├── PineappleShopDispatcher    единая точка входа для апдейтов, централизованная обработка ошибок
│   ├── router/                    MessageRouter (текст, меню), CallbackRouter (inline-кнопки)
│   ├── conversation/              ConversationManager и flow многошаговых диалогов (in-memory FSM)
│   ├── menu/                      reply-клавиатуры по ролям
│   └── support/                   TelegramSender, CallbackData, MemberNotifier
├── domain/           JPA-сущности и enum'ы
├── repository/       Spring Data JPA, атомарные bulk-update запросы
├── service/          бизнес-логика, здесь живут транзакции
└── exception/        доменные исключения
```

### Как устроен бот

- **Меню.** Постоянная reply-клавиатура зависит от роли. Динамические списки и подтверждения идут через inline-кнопки; данные кнопок хранятся в `callback_data` в формате `action:param`.
- **Сценарии с вводом текста** (начисление, добавление товара и т.д.) реализованы как `ConversationFlow`: один экземпляр на диалог, шаг и накопленные данные хранятся в полях объекта. Реестр активных диалогов лежит в памяти процесса, поэтому при перезапуске бота незавершённый диалог теряется, пользователь просто начинает заново.
- **Сценарии без ввода текста** (каталог, покупка, деактивация) полностью stateless: все нужные идентификаторы передаются в `callback_data`.
- **Ошибки.** Доменные исключения (`InsufficientBalanceException`, `OutOfStockException` и др.) ловятся в диспетчере и превращаются в понятное пользователю сообщение.
- **Уведомления** отправляются из бот-слоя уже после успешного завершения операции, сервисы о них не знают.
- **Запуск polling** выполняется отдельным компонентом по событию `ApplicationReadyEvent`, после полной сборки контекста. Если регистрировать слушатель прямо в `@Bean`-методе, получится циклическая зависимость.

### Транзакционность

Каждый публичный метод `PointsService` (`credit`, `buy`, `refund`) выполняется в одной транзакции целиком: либо все изменения, либо ничего.

- Баланс пользователя и остаток товара меняются только атомарными запросами вида `UPDATE ... SET balance = balance - :amount WHERE id = :id AND balance >= :amount`. Условие в `WHERE` само проверяет достаточность средств, а число обновлённых строк (0 или 1) сообщает об успехе. Отдельный `SELECT ... FOR UPDATE` не нужен.
- На уровне БД стоят ограничения `balance >= 0` и `quantity >= 0` как последняя линия защиты.
- Такие запросы идут в обход persistence context. Поэтому после них актуальный баланс нужно читать заново из БД (`getBalance`), а не из ранее загруженной сущности.
- Telegram-вызовы внутри транзакций не делаются, чтобы не держать соединение с БД на время сетевых запросов.

## База данных

Таблицы:

- `users` — пользователи, роль, материализованный баланс
- `items` — каталог (цена, остаток, признак активности)
- `earn_reasons` — справочник способов заработка, только для чтения участником
- `purchases` — покупки: снимок цены на момент покупки, статус `COMPLETED`/`REFUNDED`, данные возврата
- `point_transactions` — журнал движения баллов (`CREDIT`, `DEBIT`, `REFUND`), знаковая сумма, ссылка на покупку для `DEBIT`/`REFUND`

Покупка порождает запись в `purchases` и запись `DEBIT` в журнале; возврат меняет статус покупки и добавляет запись `REFUND`. Так всегда есть два независимых представления: что купили и как менялся баланс.

Актуальная схема БД находится в файле `src/main/resources/db/init_ddl.sql`.

Миграций (Flyway/Liquibase) в проекте пока нет, а в приложении `ddl-auto: none`: схему оно само не создаёт. На пустой БД (новый сервер, `docker compose down -v`) таблицы нужно создать вручную скриптом `src/main/resources/db/init_ddl.sql`, иначе бот упадёт на первом запросе.

## Конфигурация

### Переменные окружения

Секреты хранятся в файле `.env` в корне проекта рядом с `docker-compose.yml`. Файл не должен попадать в git и в образ (он есть в `.gitignore` и `.dockerignore`). В репозитории лежит шаблон `.env.example`.

| Переменная | Назначение |
|---|---|
| `POSTGRES_USER` | пользователь БД (и для контейнера Postgres, и для приложения) |
| `POSTGRES_PASSWORD` | пароль БД |
| `BOT_TOKEN` | токен бота от @BotFather |
| `ADMIN_TELEGRAM_ID` | Telegram id администратора |
| `POSTGRES_URL` | JDBC URL, задаётся в compose (`jdbc:postgresql://postgres:5432/pineapple-shop-db`) |
| `TZ` | часовой пояс контейнера приложения |

`TZ` важен: в контейнере по умолчанию UTC, а время в «🧾 История баллов» берётся из системной зоны.

Postgres применяет `POSTGRES_USER` и `POSTGRES_PASSWORD` только при первой инициализации тома. Смена значений в `.env` на уже существующую БД не действует, пароль нужно менять внутри БД (`ALTER USER ...`).

### application.yaml

- Префикс настроек бота `pineapple-shop` (`token`, `admin-telegram-id`) должен совпадать с `@ConfigurationProperties` в классе свойств.
- `spring.jpa.open-in-view: false` — для бота это корректно, вебзапросов нет.
- `logging.level.org.hibernate.SQL: DEBUG` пишет каждый SQL-запрос в лог. Для отладки удобно, для постоянной работы лучше убрать. Включённый `orm.jdbc.bind: TRACE` выведет значения параметров, включая комментарии, так что в рабочем окружении его не включайте.

## Запуск

### Подготовка (один раз)

1. В корне проекта: `docker-compose.yml`, `Dockerfile`, `.dockerignore`, `backup.sh`, `.env`.
2. Заполнить `.env` (по образцу `.env.example`).
3. Убедиться, что в БД есть таблицы (см. [База данных](#база-данных)).
4. Остановить локальный экземпляр бота, если он запущен. С одним токеном может работать только один процесс, иначе Telegram отвечает `409 Conflict`.
5. На Windows добавить в `.gitattributes`:

   ```
   gradlew text eol=lf
   *.sh text eol=lf
   ```

   Иначе скрипты с переводами строк CRLF не запустятся в Linux-контейнере (`/bin/sh^M: bad interpreter`).

### Команды

```
docker compose up -d --build        # первый запуск: сборка, БД, приложение, бэкапер
docker compose ps                   # состояние
docker compose logs -f app          # логи приложения
docker compose up -d --build app    # пересборка после изменений кода (БД не трогается)
docker compose restart app          # перезапуск только приложения
docker compose stop                 # остановить, не удаляя контейнеры
docker compose down                 # удалить контейнеры, данные БД сохраняются
docker compose down -v              # ВНИМАНИЕ: удаляет тома, то есть все данные БД
```

Изменения `.env` и `environment:` подхватываются командой `docker compose up -d`, пересборка образа не нужна.

### Сборка без compose

```
docker build -t pineapple-shop-app .      # только образ, ничего не запускается
gradlew bootJar                           # только jar, результат в build/libs/
```

После `docker build` образ хранится внутри Docker (`docker images`), в папке проекта файлов не появляется. Достать jar из образа можно так: `docker create --name tmp pineapple-shop-app`, затем `docker cp tmp:/app/app.jar .` и `docker rm tmp`. Образ файлом: `docker save -o pineapple-shop-app.tar pineapple-shop-app`.

## Docker

### Dockerfile

Multi-stage сборка из двух этапов:

1. `build` — JDK и Gradle, собирается `bootJar` (кэш зависимостей через `--mount=type=cache`).
2. Итоговый образ — только JRE и один jar, приложение запускается не от root.

Из первого этапа во второй копируется только jar (`COPY --from=build`), исходники и инструменты сборки в итоговый образ не попадают. Собирать нужно именно `bootJar`: задача `build` создаёт ещё и `*-plain.jar`, и маска в `COPY` перестанет находить единственный файл. Порты у образа не объявлены: бот работает через long polling и сам ходит наружу.

### Лимиты ресурсов

Для приложения заданы `mem_limit: 512m` и `cpus: 1.0` (допустима и запись через `deploy.resources.limits`, но не обе сразу). Флаг `-XX:MaxRAMPercentage=75` в `ENTRYPOINT` отдаёт под кучу JVM три четверти лимита контейнера. Если контейнер убивается за память, уменьшайте процент, а не только поднимайте лимит.

```
docker stats pineapple-shop-app
docker inspect pineapple-shop-app --format "{{.State.OOMKilled}} {{.State.ExitCode}}"
```

`true 137` означает, что процесс убит за превышение лимита.

## Бэкапы

За ежедневные копии отвечает сервис `backup` в `docker-compose.yml`. Он использует тот же образ `postgres:17.9` (версия `pg_dump` совпадает с версией сервера), читает подключение из стандартных `PG*`-переменных и запускает `backup.sh`.

Поведение скрипта:

- дамп в формате `-Fc` в папку `./backups` с именем `daily-ГГГГ-ММ-ДД_ЧЧ-ММ.dump`;
- дамп создаётся при старте контейнера и затем примерно раз в 24 часа (не в фиксированный час), пока контейнер работает;
- если в папке уже есть автоматический дамп моложе 22 часов, лишний не создаётся, поэтому перезапуски не вытесняют старые копии;
- дамп пишется во временный `.tmp` и переименовывается только после успеха;
- хранятся `BACKUP_KEEP` самых новых файлов (по умолчанию 7), остальные удаляются, и только после успешного дампа, чтобы при недоступной БД хорошие копии не пропали;
- файлы, имя которых не подходит под `daily-*.dump`, скрипт не трогает и в лимит не считает.

```
docker compose logs backup     # что делает бэкапер
ls -lh backups                 # файлы
```

Рекомендации:

- Храните копии не только на этом же диске: периодически переносите `backups/` на внешний диск или в облако.
- Не копируйте папку тома Postgres вместо дампа: файлы работающей БД копируются в несогласованном состоянии.
- Если компьютер или Docker выключен в момент запуска, дамп пропускается и делается при следующем старте контейнера.

## Нюансы и типичные проблемы

- **Один токен — один polling.** Два запущенных экземпляра (IDE и контейнер) дают `409 Conflict`.
- **Новая кнопка меню не появилась.** Reply-клавиатура хранится на стороне Telegram-клиента и обновляется только вместе с новым сообщением. После изменения меню нужно отправить боту `/start`.
- **Бот пишет только тем, кто нажал `/start`.** Поэтому уведомления участнику работают только после его регистрации.
- **Лимит сообщения Telegram — 4096 символов.** При очень длинных справочниках список придётся разбивать на части или делать кнопками.
- **Ленивые связи JPA.** Связи вроде `Purchase.item` помечены `LAZY`, а `open-in-view` выключен. Обращаться к ним нужно внутри транзакции или подгружать через `@EntityGraph`, иначе будет `LazyInitializationException`.
- **`password authentication failed`** после запуска из контейнера: том Postgres создан с другим паролем, см. раздел «Переменные окружения».
- **`relation ... does not exist`:** в БД нет таблиц, схема не создаётся автоматически.
- **Контейнер приложения постоянно перезапускается:** причина в `docker compose logs app`.

## Что не реализовано

- **Списание за бездействие** (100 баллов за 7 дней без активности) бот не делает. Эти списания администратор выполняет вручную напрямую в БД. В боте нет ручного списания баллов, поэтому такие операции не отражаются в уведомлениях и не создают записей в журнале.
- **Миграции схемы** (Flyway/Liquibase).
- **Ротация логов и внешний мониторинг.**

---

# Pineapple Shop (English)

[Русская версия](#pineapple-shop) · English

A Telegram bot for two users: the administrator awards points to the participant, and the participant spends them on items from a catalog. Points, item stock and refunds are tracked in PostgreSQL, and all balance operations are atomic.

## Contents

- [Features](#features)
- [Stack](#stack)
- [Architecture](#architecture)
- [Database](#database)
- [Configuration](#configuration)
- [Running](#running)
- [Docker](#docker)
- [Backups](#backups)
- [Notes and troubleshooting](#notes-and-troubleshooting)
- [Not implemented](#not-implemented)

## Features

The system has exactly two users. The role is determined on the first `/start`: if the Telegram id equals `ADMIN_TELEGRAM_ID`, the user becomes the administrator (`ADMIN`), otherwise the participant (`MEMBER`).

The button labels in the bot are in Russian; English translations are given in parentheses.

### Participant (MEMBER)

Menu: `💰 Баланс` (Balance), `🎯 Как заработать` (How to earn), `🛍 Каталог товаров` (Item catalog).

- Balance: the current number of points.
- How to earn: a reference list of ways to get points, with descriptions and approximate amounts. The list is informational only and does not affect crediting.
- Catalog: a list of available items (name, price, stock) → item card → purchase with confirmation. The cases "not enough points" and "the item has just run out" are handled.

### Administrator (ADMIN)

Menu: `💸 Начислить` (Credit points), `🛍 Каталог товаров` (Item catalog), `➕ Добавить товар` (Add item), `🎯 Способы заработка` (Ways to earn), `📜 История покупок` (Purchase history), `🧾 История баллов` (Points history).

- Crediting points to the participant: amount → comment → confirmation.
- Adding an item: name → description → price → stock → confirmation.
- Managing an item from its catalog card: restocking, deactivation (the item disappears from the catalog).
- Ways-to-earn reference list: add, edit the description and points, delete. Deletion is soft (`is_active = false`), the row stays in the database.
- The participant's purchase history, with a refund button on every purchase that has not been refunded. A refund restores the points and the item stock.
- Points history: a log of credits, purchases and refunds, 10 entries per page.

Any main menu button and the `/start` command interrupt the current multi-step scenario.

### Notifications to the participant

The bot messages the participant when:

- the administrator credits points;
- the administrator refunds a purchase;
- a new item with stock greater than zero appears in the catalog;
- a new way to earn points is added.

A delivery failure (for example, the participant blocked the bot) is logged and does not roll back the administrator's operation.

## Stack

- Java 25, Spring Boot, Spring Data JPA
- PostgreSQL 17.9
- [java-telegram-bot-api](https://github.com/pengrad/java-telegram-bot-api) (pengrad), updates received via long polling
- Gradle, Docker, Docker Compose

## Architecture

A monolith with clearly separated layers: the Telegram layer knows nothing about JPA queries, and the services know nothing about Telegram.

```
ru.dogernout.pineapple_shop
├── config/           settings (properties, TelegramBot bean), polling start after the context is up
├── bot/
│   ├── PineappleShopDispatcher    single entry point for updates, centralized error handling
│   ├── router/                    MessageRouter (text, menu), CallbackRouter (inline buttons)
│   ├── conversation/              ConversationManager and multi-step dialog flows (in-memory FSM)
│   ├── menu/                      reply keyboards per role
│   └── support/                   TelegramSender, CallbackData, MemberNotifier
├── domain/           JPA entities and enums
├── repository/       Spring Data JPA, atomic bulk-update queries
├── service/          business logic, transactions live here
└── exception/        domain exceptions
```

### How the bot works

- **Menus.** A persistent reply keyboard depends on the role. Dynamic lists and confirmations use inline buttons; button data is stored in `callback_data` in the `action:param` format.
- **Scenarios that need text input** (crediting, adding an item, etc.) are implemented as `ConversationFlow`: one instance per dialog, with the step and collected data kept in the object's fields. The registry of active dialogs lives in process memory, so an unfinished dialog is lost on restart and the user simply starts over.
- **Scenarios without text input** (catalog, purchase, deactivation) are fully stateless: all required identifiers are passed in `callback_data`.
- **Errors.** Domain exceptions (`InsufficientBalanceException`, `OutOfStockException`, etc.) are caught in the dispatcher and turned into a user-friendly message.
- **Notifications** are sent from the bot layer after the operation completes successfully; the services do not know about them.
- **Polling start** is performed by a separate component on the `ApplicationReadyEvent`, after the context is fully assembled. Registering the listener directly in a `@Bean` method creates a circular dependency.

### Transactionality

Every public method of `PointsService` (`credit`, `buy`, `refund`) runs entirely in one transaction: either all changes are applied or none.

- A user's balance and an item's stock are changed only by atomic queries such as `UPDATE ... SET balance = balance - :amount WHERE id = :id AND balance >= :amount`. The `WHERE` condition itself checks that funds are sufficient, and the number of updated rows (0 or 1) reports success. A separate `SELECT ... FOR UPDATE` is not needed.
- The database enforces `balance >= 0` and `quantity >= 0` as the last line of defense.
- Such queries bypass the persistence context. After them, the current balance must be re-read from the database (`getBalance`) rather than taken from a previously loaded entity.
- No Telegram calls are made inside transactions, so a database connection is not held during network requests.

## Database

Tables:

- `users` — users, role, materialized balance
- `items` — catalog (price, stock, active flag)
- `earn_reasons` — ways-to-earn reference list, read-only for the participant
- `purchases` — purchases: price snapshot at purchase time, status `COMPLETED`/`REFUNDED`, refund details
- `point_transactions` — points movement log (`CREDIT`, `DEBIT`, `REFUND`), signed amount, link to the purchase for `DEBIT`/`REFUND`

A purchase produces a row in `purchases` and a `DEBIT` row in the log; a refund changes the purchase status and adds a `REFUND` row. This way there are always two independent views: what was bought and how the balance changed.

The current database schema is in `src/main/resources/db/init_ddl.sql`.

The project has no migrations (Flyway/Liquibase) yet, and the application uses `ddl-auto: none`: it does not create the schema itself. On an empty database (a new server, `docker compose down -v`) the tables must be created manually with the script `src/main/resources/db/init_ddl.sql`, otherwise the bot fails on the first query.

## Configuration

### Environment variables

Secrets are stored in the `.env` file in the project root next to `docker-compose.yml`. The file must not end up in git or in the image (it is listed in `.gitignore` and `.dockerignore`). A `.env.example` template is kept in the repository.

| Variable | Purpose |
|---|---|
| `POSTGRES_USER` | database user (for both the Postgres container and the application) |
| `POSTGRES_PASSWORD` | database password |
| `BOT_TOKEN` | bot token from @BotFather |
| `ADMIN_TELEGRAM_ID` | Telegram id of the administrator |
| `POSTGRES_URL` | JDBC URL, set in compose (`jdbc:postgresql://postgres:5432/pineapple-shop-db`) |
| `TZ` | time zone of the application container |

`TZ` matters: a container uses UTC by default, and the time in "🧾 История баллов" (Points history) comes from the system zone.

Postgres applies `POSTGRES_USER` and `POSTGRES_PASSWORD` only when the volume is first initialized. Changing the values in `.env` has no effect on an existing database; the password has to be changed inside the database (`ALTER USER ...`).

### application.yaml

- The bot settings prefix `pineapple-shop` (`token`, `admin-telegram-id`) must match `@ConfigurationProperties` in the properties class.
- `spring.jpa.open-in-view: false` is correct for a bot, since there are no web requests.
- `logging.level.org.hibernate.SQL: DEBUG` writes every SQL query to the log. It is handy for debugging but better removed for permanent operation. Enabling `orm.jdbc.bind: TRACE` prints parameter values, including comments, so do not enable it in a production environment.

## Running

### Preparation (once)

1. In the project root: `docker-compose.yml`, `Dockerfile`, `.dockerignore`, `backup.sh`, `.env`.
2. Fill in `.env` (use `.env.example` as a template).
3. Make sure the database has its tables (see [Database](#database)).
4. Stop any local instance of the bot. Only one process can use a given token, otherwise Telegram responds with `409 Conflict`.
5. On Windows, add the following to `.gitattributes`:

   ```
   gradlew text eol=lf
   *.sh text eol=lf
   ```

   Otherwise scripts with CRLF line endings will not run in the Linux container (`/bin/sh^M: bad interpreter`).

### Commands

```
docker compose up -d --build        # first run: build, database, application, backup service
docker compose ps                   # status
docker compose logs -f app          # application logs
docker compose up -d --build app    # rebuild after code changes (the database is not touched)
docker compose restart app          # restart the application only
docker compose stop                 # stop without removing containers
docker compose down                 # remove containers, database data is kept
docker compose down -v              # WARNING: removes volumes, i.e. all database data
```

Changes to `.env` and `environment:` are picked up by `docker compose up -d`; no image rebuild is needed.

### Building without compose

```
docker build -t pineapple-shop-app .      # image only, nothing is started
gradlew bootJar                           # jar only, the result is in build/libs/
```

After `docker build` the image is stored inside Docker (`docker images`); no files appear in the project folder. To extract the jar from the image: `docker create --name tmp pineapple-shop-app`, then `docker cp tmp:/app/app.jar .` and `docker rm tmp`. To get the image as a file: `docker save -o pineapple-shop-app.tar pineapple-shop-app`.

## Docker

### Dockerfile

A multi-stage build with two stages:

1. `build` — JDK and Gradle, runs `bootJar` (dependency cache via `--mount=type=cache`).
2. The final image — only a JRE and a single jar; the application does not run as root.

Only the jar is copied from the first stage to the second (`COPY --from=build`); sources and build tools do not end up in the final image. The build must use `bootJar` specifically: the `build` task also produces a `*-plain.jar`, and the glob in `COPY` would stop matching a single file. The image declares no ports: the bot works through long polling and makes outgoing connections itself.

### Resource limits

The application has `mem_limit: 512m` and `cpus: 1.0` (the `deploy.resources.limits` form also works, but not both at once). The `-XX:MaxRAMPercentage=75` flag in `ENTRYPOINT` gives the JVM heap three quarters of the container limit. If the container is killed for memory, lower the percentage rather than only raising the limit.

```
docker stats pineapple-shop-app
docker inspect pineapple-shop-app --format "{{.State.OOMKilled}} {{.State.ExitCode}}"
```

`true 137` means the process was killed for exceeding the limit.

## Backups

Daily copies are handled by the `backup` service in `docker-compose.yml`. It uses the same `postgres:17.9` image (so the `pg_dump` version matches the server), reads the connection from the standard `PG*` variables and runs `backup.sh`.

Script behavior:

- a dump in `-Fc` format is written to the `./backups` folder with the name `daily-YYYY-MM-DD_HH-MM.dump`;
- a dump is created when the container starts and then roughly every 24 hours (not at a fixed hour) while the container is running;
- if the folder already contains an automatic dump younger than 22 hours, an extra one is not created, so restarts do not push out older copies;
- the dump is written to a temporary `.tmp` file and renamed only on success;
- the `BACKUP_KEEP` newest files are kept (7 by default) and the rest are deleted, and only after a successful dump, so good copies are not lost while the database is unavailable;
- files whose names do not match `daily-*.dump` are not touched by the script and do not count towards the limit.

```
docker compose logs backup     # what the backup service is doing
ls -lh backups                 # files
```

Recommendations:

- Do not keep copies only on the same disk: periodically move `backups/` to an external drive or to the cloud.
- Do not copy the Postgres volume folder instead of using a dump: the files of a running database are copied in an inconsistent state.
- If the computer or Docker is off at the scheduled time, the dump is skipped and is made the next time the container starts.

## Notes and troubleshooting

- **One token, one polling process.** Two running instances (the IDE and the container) cause `409 Conflict`.
- **A new menu button did not appear.** The reply keyboard is stored on the Telegram client side and is updated only together with a new message. After changing the menu, send `/start` to the bot.
- **The bot can message only users who pressed `/start`.** That is why notifications to the participant work only after they have registered.
- **Telegram message limit is 4096 characters.** With very long reference lists, split the list into several messages or use buttons.
- **Lazy JPA associations.** Associations such as `Purchase.item` are marked `LAZY` and `open-in-view` is off. Access them inside a transaction or fetch them with `@EntityGraph`, otherwise you get `LazyInitializationException`.
- **`password authentication failed`** when running from the container: the Postgres volume was created with a different password, see "Environment variables".
- **`relation ... does not exist`:** the database has no tables; the schema is not created automatically.
- **The application container keeps restarting:** the reason is in `docker compose logs app`.

## Not implemented

- **Penalty for inactivity** (100 points for 7 days without activity) is not performed by the bot. The administrator applies these deductions manually, directly in the database. The bot has no manual deduction of points, so such operations do not appear in notifications and do not create rows in the log.
- **Schema migrations** (Flyway/Liquibase).
- **Log rotation and external monitoring.**
