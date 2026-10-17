create table users (
  id            bigserial primary key,
  email         varchar(120) unique,
  phone         varchar(30),
  password_hash varchar(100),
  full_name     varchar(120) not null,
  role          varchar(20) not null,          -- PASSENGER | DISPATCHER | ADMIN
  coin_balance  int not null default 0,
  accessibility boolean not null default false,
  tracking_consent boolean not null default true,
  created_at    timestamp with time zone not null default now()
);

create table operators (
  id          bigserial primary key,
  code        varchar(10) unique not null,       -- bb, xf, ct, 6d
  name        varchar(120) not null,             -- BakuBus MMC ...
  fleet_total int not null,
  punctuality int not null                       -- %, seed
);

create table buses (
  id          bigserial primary key,
  bort        varchar(20) unique not null,       -- 10-BB-482
  operator_id bigint not null references operators(id),
  route       varchar(10) not null,
  type        varchar(10) not null,              -- ELECTRIC | DIESEL
  model       varchar(60),
  capacity    int not null,
  status      varchar(10) not null               -- LINE | DEPOT | REPAIR
);
create index on buses(operator_id); create index on buses(route);

create table route_config (
  route            varchar(10) primary key,
  interval_min     int not null,
  bus_count        int not null,
  cycle_min        int not null,                 -- dövr vaxtı
  auto_mode        boolean not null default false,
  updated_at       timestamp with time zone not null default now()
);

create table trips (
  id          bigserial primary key,
  user_id     bigint not null references users(id),
  route       varchar(10) not null,
  bort        varchar(20),
  from_stop   varchar(60),                       -- minmə dayanacağı
  to_place    varchar(60),
  alight_stop varchar(60),                       -- düşmə dayanacağı (aşkarlanan)
  alight_lat  double precision, alight_lon double precision,
  alight_detected varchar(8),                    -- AUTO | MANUAL
  synthetic   boolean not null default false,    -- analitika seed-i
  paid_with   varchar(20) not null,              -- BAKIKART | CARD | COIN
  started_at  timestamp with time zone not null default now(),
  ended_at    timestamp with time zone
);

create table reviews (
  id            bigserial primary key,
  trip_id       bigint unique not null references trips(id),   -- 1 səfər = 1 rəy
  user_id       bigint not null references users(id),
  route         varchar(10) not null,
  bort          varchar(20),
  operator_id   bigint references operators(id),
  driver_rating smallint not null check (driver_rating between 1 and 5),
  road_rating   smallint not null check (road_rating between 1 and 5),
  bus_rating    smallint not null check (bus_rating between 1 and 5),
  crowd_level   varchar(12) not null,             -- EMPTY | NORMAL | FULL | OVERFULL
  tags          text,                             -- vergüllə
  comment       text,
  created_at    timestamp with time zone not null default now()
);

create table reports (
  id            bigserial primary key,
  code          varchar(12) unique not null,      -- A-2042
  user_id       bigint references users(id),
  route         varchar(10) not null,
  bort          varchar(20),
  operator_id   bigint references operators(id),
  category      varchar(40) not null,
  text          text,
  has_photo     boolean not null default false,
  place         varchar(60),
  lat double precision, lon double precision,
  status        varchar(12) not null,             -- NEW | REVIEW | FORWARDED | ANSWERED | RESOLVED
  answer_text   text,
  created_at    timestamp with time zone not null default now(),
  forwarded_at  timestamp with time zone,
  updated_at    timestamp with time zone not null default now()
);
create index on reports(status); create index on reports(created_at desc);

create table report_events (
  id         bigserial primary key,
  report_id  bigint not null references reports(id),
  type       varchar(20) not null,                -- CREATED | STATUS | FORWARDED | SHARED | ANSWERED
  note       text,
  actor      varchar(60),
  created_at timestamp with time zone not null default now()
);

create table surveys (
  id           bigserial primary key,
  title        varchar(200) not null,
  status       varchar(10) not null,              -- DRAFT | ACTIVE | CLOSED
  audience     varchar(60) not null,              -- ALL | AVTOVAGZAL_2KM | ROUTE_88_4
  coin_reward  int not null,
  created_at   timestamp with time zone not null default now(),
  published_at timestamp with time zone, closed_at timestamp with time zone
);
create table survey_questions (
  id        bigserial primary key,
  survey_id bigint not null references surveys(id),
  ord       int not null,
  text      text not null,
  options   text not null                          -- JSON massiv: ["Hər gün","Xeyr"]
);
create table survey_answers (
  id         bigserial primary key,
  survey_id  bigint not null references surveys(id),
  user_id    bigint not null references users(id),
  answers    text not null,                        -- JSON: {"<questionId>":"<option>"}
  created_at timestamp with time zone not null default now(),
  unique (survey_id, user_id)
);

create table coin_tx (
  id         bigserial primary key,
  user_id    bigint not null references users(id),
  amount     int not null,                         -- + qazanc, − xərc
  reason     varchar(30) not null,                 -- REVIEW | SURVEY | REPORT | REPORT_VERIFIED | FARE | CONVERSION | SEED
  ref_id     varchar(40),
  label      varchar(120) not null,
  created_at timestamp with time zone not null default now(),
  unique (user_id, reason, ref_id)                 -- eyni şeyə iki dəfə mükafat yoxdur
);

create table coin_conversions (
  id         bigserial primary key,
  user_id    bigint not null references users(id),
  coins      int not null,
  azn        numeric(10,2) not null,
  card_mask  varchar(30) not null,
  status     varchar(12) not null,                 -- PENDING | COMPLETED
  created_at timestamp with time zone not null default now(),
  completed_at timestamp with time zone
);

create table notifications (
  id         bigserial primary key,
  user_id    bigint not null references users(id),
  type       varchar(20) not null,                 -- SURVEY | REPORT | TRIP_REVIEW | COIN | INFO
  title      varchar(160) not null,
  body       text,
  target     varchar(60),                          -- mobil route: "survey:5", "report:A-2042", "review:trip:12", "wallet"
  read_at    timestamp with time zone,
  created_at timestamp with time zone not null default now()
);
create index on notifications(user_id, created_at desc);

create index on trips(user_id, started_at desc);

create table hub_proposals (
  id          bigserial primary key,
  name        varchar(120) not null,               -- Babək pr. – Gənclik metrosu
  lat double precision not null, lon double precision not null,
  jam_minutes int not null, routes_count int not null, metro_m int not null,
  waiting int not null, pax_per_hour int not null,
  confidence int not null,                         -- %
  items       text not null,                       -- JSON: nə qurulmalı
  impact      text not null,                       -- JSON: gözlənilən təsir
  status      varchar(10) not null default 'NEW',  -- NEW | ACCEPTED | REJECTED
  created_at  timestamp with time zone not null default now()
);

create table alerts (                              -- dashboard hadisələr lenti
  id         bigserial primary key,
  category   varchar(12) not null,                 -- HUB | OCC | AI | BILETIM | REPORT | EVENT | WEATHER
  title      varchar(200) not null,
  body       text,
  target     varchar(30),                          -- web səhifə: "p-occ"
  created_at timestamp with time zone not null default now()
);
