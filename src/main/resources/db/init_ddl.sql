BEGIN;

CREATE SCHEMA core;
-- Пользователи
CREATE TABLE core.users (
                       id            BIGSERIAL PRIMARY KEY,
                       telegram_id   BIGINT NOT NULL UNIQUE,
                       username      VARCHAR(64),
                       role          VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN', 'MEMBER')),
                       balance       INTEGER NOT NULL DEFAULT 0 CHECK (balance >= 0),
                       created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Каталог вещей, на которые можно потратить баллы
CREATE TABLE core.items (
                       id          BIGSERIAL PRIMARY KEY,
                       title       VARCHAR(256) NOT NULL,
                       description TEXT,
                       price       INTEGER NOT NULL CHECK (price > 0),
                       quantity    INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
                       is_active   BOOLEAN NOT NULL DEFAULT TRUE,
                       created_by  BIGINT NOT NULL REFERENCES core.users(id),
                       created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Справочник "за что начисляются баллы" — только для отображения
CREATE TABLE core.earn_reasons (
                              id          BIGSERIAL PRIMARY KEY,
                              title       VARCHAR(256) NOT NULL,
                              description TEXT,
                              points      INTEGER,          -- ориентировочно; может быть NULL ("по договорённости")
                              is_active   BOOLEAN NOT NULL DEFAULT TRUE,
                              created_by  BIGINT NOT NULL REFERENCES core.users(id),
                              created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Покупки
CREATE TABLE core.purchases (
                           id            BIGSERIAL PRIMARY KEY,
                           user_id       BIGINT NOT NULL REFERENCES core.users(id),
                           item_id       BIGINT NOT NULL REFERENCES core.items(id),
                           price         INTEGER NOT NULL,   -- снапшот цены на момент покупки
                           status        VARCHAR(16) NOT NULL DEFAULT 'COMPLETED'
                               CHECK (status IN ('COMPLETED', 'REFUNDED')),
                           purchased_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                           refunded_at   TIMESTAMPTZ,
                           refunded_by   BIGINT REFERENCES core.users(id),
                           refund_reason TEXT
);

-- Ledger баллов
CREATE TABLE core.point_transactions (
                                    id            BIGSERIAL PRIMARY KEY,
                                    user_id       BIGINT NOT NULL REFERENCES core.users(id),
                                    amount        INTEGER NOT NULL,
                                    type          VARCHAR(16) NOT NULL CHECK (type IN ('CREDIT', 'DEBIT', 'REFUND')),
                                    purchase_id   BIGINT REFERENCES core.purchases(id),
                                    initiated_by  BIGINT NOT NULL REFERENCES core.users(id),
                                    comment       TEXT,
                                    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_point_transactions_user_id ON point_transactions(user_id);
CREATE INDEX idx_purchases_user_id ON purchases(user_id);

COMMIT;
