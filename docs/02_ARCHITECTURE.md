# 02 — Arxitektura

## 1. Ümumi şəkil

```
 ┌──────────────┐     REST + SSE      ┌──────────────────────────────────────┐
 │ mobile (Expo)│ ──────────────────▶ │ backend (Spring Boot, :8080)          │
 └──────────────┘                     │                                      │
 ┌──────────────┐     REST + SSE      │  auth · report · survey · coin ·     │
 │ web (Next.js)│ ──────────────────▶ │  trip · review · notification ·      │
 └──────────────┘                     │  fleet · geo · occupancy · hub ·     │
                                      │  biletim · feed · stream · demo      │
                                      │                                      │
                                      │  SimulationService (@Scheduled)      │
                                      │  ├─ avtobus mövqeləri və doluluq     │
                                      │  ├─ hub insan sayı                   │
                                      │  ├─ tıxac profili                    │
                                      │  └─ topa yığılma aşkarlanması        │
                                      └───────────────┬──────────────────────┘
                                                      │ JPA / Flyway
                                               ┌──────▼──────┐
                                               │ PostgreSQL  │
                                               └─────────────┘
 data/processed/*.geojson  ──(startup)──▶ GeoStore (yaddaşda)
 mocks/*.json              ──(startup)──▶ seed (DB) və SIM mənbələri
```

Bir monolit, bir DB, bir SSE kanalı. Sadəlik sürətdir.

## 2. Backend paket strukturu

```
backend/src/main/java/az/yolcu/
├── YolcuApplication.java
├── config/         # CorsConfig, JacksonConfig, SchedulingConfig, OpenApiConfig, YolcuProperties
├── security/       # SecurityConfig, JwtService, JwtFilter, CurrentUser
├── common/         # ApiError, GlobalExceptionHandler, PageResponse<T>, GeoUtil, Clock
├── auth/           # AuthController (login, demo-login, me)
├── user/           # User entity, UserRepository
├── geo/            # GeoStore (fayldan yükləmə), GeoController, Stop (seed)
├── fleet/          # Operator, Bus, FleetController, OperatorStatsService
├── report/         # Report, ReportEvent, ReportController (mobile + admin), ReportService
├── review/         # Review, ReviewService
├── trip/           # Trip, TripController (start/end), TripPlannerService, WalkService
├── survey/         # Survey, SurveyQuestion, SurveyAnswer, SurveyController, SurveyResultsService
├── coin/           # CoinTx, CoinConversion, CoinService (qaydalar), CoinController, BankSimJob
├── notification/   # Notification, NotificationService, NotificationController
├── occupancy/      # RouteConfig, IntervalEngine, OccupancyController
├── hub/            # HubController (SIM rəqəmləri)
├── biletim/        # BiletimAdapter (interface), MockBiletimAdapter, BiletimController
├── traffic/        # TrafficController (SIM)
├── feed/           # Alert, FeedService, FeedController
├── stream/         # StreamController, EventBus (SseEmitter reyestri)
├── sim/            # SimulationService, SimBus, SimHub, BunchingDetector
├── analytics/      # AnalyticsController (OD, düşmə nöqtələri, KPI)
├── proposal/       # HubProposal, HubProposalRule, HubProposalController
├── micro/          # MicromobilityController (velo parkları REAL, skuterlər SIM)
└── demo/           # DemoController (trigger/reset), DemoScenarios
```

Hər domen öz `XxxController`, `XxxService`, `XxxRepository`, `dto/` alt paketinə malikdir.

## 3. Verilənlər bazası (Flyway `V1__init.sql`)

```sql
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
  created_at    timestamptz not null default now()
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
  updated_at       timestamptz not null default now()
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
  started_at  timestamptz not null default now(),
  ended_at    timestamptz
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
  created_at    timestamptz not null default now()
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
  created_at    timestamptz not null default now(),
  forwarded_at  timestamptz,
  updated_at    timestamptz not null default now()
);
create index on reports(status); create index on reports(created_at desc);

create table report_events (
  id         bigserial primary key,
  report_id  bigint not null references reports(id),
  type       varchar(20) not null,                -- CREATED | STATUS | FORWARDED | SHARED | ANSWERED
  note       text,
  actor      varchar(60),
  created_at timestamptz not null default now()
);

create table surveys (
  id           bigserial primary key,
  title        varchar(200) not null,
  status       varchar(10) not null,              -- DRAFT | ACTIVE | CLOSED
  audience     varchar(60) not null,              -- ALL | AVTOVAGZAL_2KM | ROUTE_88_4
  coin_reward  int not null,
  created_at   timestamptz not null default now(),
  published_at timestamptz, closed_at timestamptz
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
  created_at timestamptz not null default now(),
  unique (survey_id, user_id)
);

create table coin_tx (
  id         bigserial primary key,
  user_id    bigint not null references users(id),
  amount     int not null,                         -- + qazanc, − xərc
  reason     varchar(30) not null,                 -- REVIEW | SURVEY | REPORT | REPORT_VERIFIED | FARE | CONVERSION | SEED
  ref_id     varchar(40),
  label      varchar(120) not null,
  created_at timestamptz not null default now(),
  unique (user_id, reason, ref_id)                 -- eyni şeyə iki dəfə mükafat yoxdur
);

create table coin_conversions (
  id         bigserial primary key,
  user_id    bigint not null references users(id),
  coins      int not null,
  azn        numeric(10,2) not null,
  card_mask  varchar(30) not null,
  status     varchar(12) not null,                 -- PENDING | COMPLETED
  created_at timestamptz not null default now(),
  completed_at timestamptz
);

create table notifications (
  id         bigserial primary key,
  user_id    bigint not null references users(id),
  type       varchar(20) not null,                 -- SURVEY | REPORT | TRIP_REVIEW | COIN | INFO
  title      varchar(160) not null,
  body       text,
  target     varchar(60),                          -- mobil route: "survey:5", "report:A-2042", "review:trip:12", "wallet"
  read_at    timestamptz,
  created_at timestamptz not null default now()
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
  created_at  timestamptz not null default now()
);

create table alerts (                              -- dashboard hadisələr lenti
  id         bigserial primary key,
  category   varchar(12) not null,                 -- HUB | OCC | AI | BILETIM | REPORT | EVENT | WEATHER
  title      varchar(200) not null,
  body       text,
  target     varchar(30),                          -- web səhifə: "p-occ"
  created_at timestamptz not null default now()
);
```

`V2__seed.sql` (və ya `SeedRunner` CommandLineRunner): istifadəçilər, 4 operator, `mocks/buses.json`, `route_config` (88, 4, 65, 33, M1), `mocks/surveys.json`, 40 köhnə şikayət, coin tarixçəsi.

## 4. SSE (real vaxt)

**Endpoint:** `GET /api/v1/stream?token=<JWT>` (brauzerdə `EventSource` header göndərə bilmir, ona görə token query-də).

**EventBus:** `ConcurrentHashMap<Long userId, List<SseEmitter>>` + rol məlumatı. `publishAll(type, data)`, `publishToRole(role, type, data)`, `publishToUser(userId, type, data)`. Hər 20 saniyədə `ping` hadisəsi (bağlantı qırılmasın). Emitter timeout: 0 (sonsuz), `onCompletion/onTimeout/onError` ilə reyestrdən sil.

| Hadisə | Kimə | Data | Kim yaradır |
|---|---|---|---|
| `bus.positions` | hamı | `[{bort,route,lat,lon,bearing,occupancy,capacity,type}]` | Sim, hər 2 san |
| `hub.counts` | DISPATCHER | `[{hubId,count,trend,forecast15}]` | Sim, hər 5 san |
| `alert.created` | DISPATCHER | `Alert` | Sim, servislər, demo |
| `report.created` | DISPATCHER | `ReportListItem` | ReportService |
| `report.updated` | DISPATCHER + sahib | `ReportListItem` | ReportService |
| `notification.created` | istifadəçi | `Notification` | NotificationService |
| `coin.updated` | istifadəçi | `{balance}` | CoinService |
| `route.updated` | hamı | `RouteOccupancy` | OccupancyController |
| `proposal.created` | DISPATCHER | `HubProposal` | HubProposalRule |
| `traffic.updated` | hamı | `TrafficSegment[]` | Sim, dəyişəndə |
| `ping` | hamı | `{}` | 20 san |

Domen servisləri SSE-ni birbaşa çağırmır: Spring `ApplicationEventPublisher` ilə `ReportCreatedEvent` və s. yayır, `stream/EventBridge` onları SSE-yə çevirir.

## 5. Təhlükəsizlik (minimal, amma düzgün)

- `POST /api/v1/auth/login` (email+şifrə, BCrypt) və `POST /api/v1/auth/demo-login` (passenger) → JWT (HS256, 12 saat, claim: `sub`, `role`).
- `/api/v1/admin/**` → `DISPATCHER` və ya `ADMIN`. `/api/v1/me/**` → hər giriş etmiş istifadəçi. `/api/v1/geo/**`, `/api/v1/stops/**` → açıq.
- CORS: `http://localhost:3000`, `exp://*`, LAN IP-lər (`yolcu.cors-origins` property-si ilə).
- Rate limit (sadə, yaddaşda): şikayət — istifadəçi başına 10 dəqiqədə 3.

## 6. Simulyator (`sim/SimulationService`)

- **Marşrutlar:** `route_config`-dəki 5 marşrut (88, 4, 65, 33, M1). Həndəsə `GeoStore`-dan (trajectory adı uyğun gəlməsə, `mocks/sim_routes.json`-dakı koordinat siyahısı).
- **Avtobuslar:** hər marşrutda `bus_count` qədər virtual avtobus, `buses` cədvəlindən bort nömrələri ilə. Xətt boyunca bərabər paylanır. Sürət: marşrutun tıxac səviyyəsinə görə (4: 9 km/s, 88: 22 km/s ...).
- **Doluluq:** hər avtobus üçün random walk, marşrutun bazası ətrafında (88: ~85%, 33: ~30%). Interval qısaldılanda baza `occ × new/old` qədər azalır (İnterval mühərriki ilə eyni formula).
- **Hub sayı:** hər hub üçün baza + sinus pik (08:00–09:30) + səs-küy. `trend` = son 10 dəqiqənin fərqi, `forecast15` = `count + trend × 1.5`.
- **Topa yığılma:** eyni marşrutda ardıcıl iki avtobus arası < 3 dəq (məsafə/sürət) → `alert.created` (OCC). Demo üçün `demo/trigger/bunching` iki 88-i bir-birinə yaxınlaşdırır.
- **Avtomatik rejim:** `auto_mode=true` olan marşrutda hər 30 san tövsiyə edilən interval tətbiq olunur və `alert.created` (AI) yayılır.
- Tick: avtobuslar 2 san, hub-lar 5 san. Hamısı yaddaşda, DB-yə yazılmır.

## 6.1 Səfər izləmə və vərdiş aşkarlanması (`trip/PatternService`) — REAL

- `POST /trips/{id}/end` `{ detected, lat, lon }` → ən yaxın dayanacaq `alight_stop` kimi yazılır.
- Vərdiş sorğusu (yalnız `tracking_consent=true`):

```sql
select from_stop, alight_stop, route, count(*) n
from trips
where user_id = :uid and alight_stop is not null and synthetic = false
  and started_at > now() - interval '30 days'
  and extract(isodow from started_at) between :d1 and :d2          -- iş günü və ya həftəsonu
  and abs(extract(epoch from started_at::time - :now::time)) < 2700 -- ±45 dəq
group by 1,2,3 having count(*) >= 3 order by n desc limit 1;
```
- Nəticə varsa `GET /me/suggestion` həmin marşrutun indiki ETA-sı ilə qaytarılır. Demo istifadəçisinə seed-də 12 belə səfər yazılır.

## 6.2 Yeni hub təklifi qaydası (`proposal/HubProposalRule`) — REAL, sadə

Simulyator hər 30 saniyədə hər tıxac seqmentini yoxlayır. Seqment ≥ 20 dəq `JAM` vəziyyətindədirsə, ondan 300 m daxilindəki dayanacaq üçün:

```
routes   = dayanacaqdan keçən marşrut sayı        ≥ 3
metroM   = ən yaxın metro çıxışına məsafə         ≤ 400
waiting  = dayanacaqda gözləyən (hub sim)         ≥ 40
mövcud hub-a məsafə                               > 500 m
confidence = 50 + min(20, routes×5) + (metroM<300 ? 15 : 8) + min(15, waiting/5)
```
Şərtlər ödənilirsə `hub_proposals`-a yazılır, `alert.created` (AI) və `proposal.created` SSE yayılır. "Nə qurulmalı" siyahısı qayda ilə: velo parkı yoxdursa → "Velo parkinq, 20 yer"; metro ≤ 400 m → "Metroya işıqlı piyada keçidi"; həmişə → "Avtobus cibi" və "Hub kamerası və gəliş ekranı". Demo trigger `artificial-jam` seqmentin JAM müddətini birbaşa 35 dəqiqəyə qoyur ki, təklif 3–4 saniyəyə yaransın.

## 7. İnterval mühərriki (`occupancy/IntervalEngine`) — REAL

Eyni formula `packages/shared/src/headway.ts`-də TS-də də var (test vektorları ilə uyğunlaşdırılır).

```
occNew(iv)   = min(130, occ × iv / currentIv)
wait(iv)     = iv / 2
busesNeeded  = ceil(busCount × currentIv / iv)          // dövr vaxtı sabit
sat(occ,wait)= clamp(2.5, 4.8, 4.6 − max(0, occ−65)×0.022 − max(0, wait−3)×0.08)

TARGET       = 65                                           // hədəf doluluq, %
raw          = clamp(floor(currentIv × TARGET / occ), 6, 16)
recommended  = |raw − currentIv| ≤ 1 ? currentIv : raw         // kiçik fərqdə dəyişmə
satDelta     = sat(occNew(rec), rec/2) − sat(occ, currentIv/2)
```

Test vektorları (Java və TS eyni nəticə verməlidir):

| Marşrut | occ | iv | n | rec | occNew | buses | satDelta |
|---|---|---|---|---|---|---|---|
| 88 | 92 | 12 | 6 | 8 | 61 | 9 | +0,7 |
| 4 | 81 | 10 | 5 | 8 | 65 | 7 | +0,4 |
| M1 | 76 | 10 | 3 | 8 | 61 | 4 | +0,3 |
| 65 | 58 | 12 | 4 | 12 | 58 | 4 | 0 |
| 33 | 31 | 12 | 5 | 16 | 41 | 4 | −0,2 |

## 8. Marşrut planlayıcı (`trip/TripPlannerService`) — REAL, sadə

Girdi: `from {lat,lon}`, `to` (demo yer adı və ya koordinat), `filter`, `accessible`.

1. `from`-dan 600 m daxilində dayanacaqları tap (seed dayanacaqlar + metro çıxışları).
2. `to`-ya 600 m daxilində dayanacaqları tap. Demo yerləri `mocks/places.json`-dadır (Nərimanov, Avtovağzal, 28 May, Gənclik, Koroğlu, Ev).
3. Hər başlanğıc dayanacağı üçün oradan keçən marşrutlar ∩ son dayanacaqdan keçən marşrutlar → variant.
4. Metro: hər iki tərəfdə metro çıxışı varsa → metro variantı (stansiyalar arası sabit 2 dəq/stansiya, `mocks/metro_times.json`).
5. Skuter variantı: ilk piyada hissə > 150 m-dirsə, eyni variantın skuter versiyası (sürət 12 km/s).
6. Vaxt: `walk = məsafə / 1.3 m/s`, `wait = simulyatorda növbəti avtobusun ETA-sı`, `ride = xətt məsafəsi / marşrut sürəti`, `walkEnd = 2 dəq`.
7. Sıralama: `fast` → cəm vaxt, `walk` → piyada dəqiqə, `empty` → doluluq, `access` → yalnız `accessible=true` olanlar.
8. `why` mətni: əgər daha sürətli variant tıxac seqmentindən qaçırsa, qayda əsaslı izah.

**Piyada yol (`WalkService`):** başlanğıc → dayanacaq üçün L-formalı 3 nöqtəli xətt (əvvəl şaquli, sonra üfüqi), sonra addımlar: istiqamət (azimut → "şimala/cənuba/şərqə/qərbə"), dönmə (azimut fərqi → "sağa/sola dönün"), məsafə 5 m-ə yuvarlaqlaşdırılır. İstəyə görə (SHOULD): `routing.openstreetmap.de` piyada OSRM servisindən real həndəsə, xəta olarsa L-formaya qayıt.

## 9. Coin qaydaları (`coin/CoinService`) — REAL

| Hadisə | Məbləğ | ref_id (təkrarlanmama üçün) |
|---|---|---|
| Rəy göndərildi | +5 | `trip:{id}` |
| Sorğu cavablandı | +survey.coin_reward | `survey:{id}` |
| Şikayət göndərildi | +5 | `report:{code}` (10 dəqiqədə 3-dən çoxsa 0) |
| Şikayət RESOLVED oldu | +15 | `report-ok:{code}` |
| Gediş coin ilə | −60 | `trip:{id}` |
| Banka çevirmə | −coins | `conv:{id}` |

`@Transactional`: `users.coin_balance` yenilənir + `coin_tx` yazılır. Balans < 0 olarsa `COINS_INSUFFICIENT`. Unique pozulsa — səssiz keç (artıq verilib). Hər dəyişiklikdən sonra `coin.updated` SSE.

## 10. Konfiqurasiya (`application.yml`)

```yaml
yolcu:
  jwt-secret: ${JWT_SECRET:change-me-32-bytes-minimum-secret-key!!}
  cors-origins: http://localhost:3000,http://192.168.1.10:3000
  data-dir: ../data/processed
  mocks-dir: ../mocks
  coin-rate: 100                 # 100 coin = 1 AZN
  demo:
    auto-advance-reports: false  # true: dashboard-da adam yoxdursa şikayət öz-özünə irəliləyir
    bank-delay-seconds: 60
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/yolcu
    username: yolcu
    password: yolcu
  jpa.hibernate.ddl-auto: validate
  jpa.open-in-view: false
```

`docker-compose.yml`:

```yaml
services:
  db:
    image: postgres:16
    environment: { POSTGRES_DB: yolcu, POSTGRES_USER: yolcu, POSTGRES_PASSWORD: yolcu }
    ports: ["5432:5432"]
```

**Ehtiyat:** Docker problem olarsa `--spring.profiles.active=h2` (H2 in-memory, `MODE=PostgreSQL`).

## 11. Frontend arxitekturası (qısa)

- **API client** (`packages/shared/src/client.ts`): `fetch` wrapper, token, xəta formatı, `API_MODE=mock` olduqda `mocks/` faylından cavab.
- **SSE hook** (`useStream(handlers)`): web-də `EventSource`, mobildə `react-native-sse`. Bağlantı qırılsa 3 san sonra yenidən qoşul, yuxarıda "Bağlantı bərpa olunur" zolağı.
- **Server state:** TanStack Query. SSE hadisəsi gələndə müvafiq query-ni `setQueryData` ilə yenilə (məs. `report.created` → şikayət siyahısının başına əlavə et).
- **Client state:** web-də URL (filtrlər query string-də), mobildə Zustand (seçilmiş variant, səfər).
