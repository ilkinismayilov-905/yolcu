# 05 — Backend roadmap (Java Spring Boot)

İki backend developer: **BE-A** (domen: auth, şikayət, sorğu, coin, səfər) və **BE-B** (data, simulyator, SSE, doluluq, analitika, demo). Saatlar `09_24H_PLAN_AND_DEMO.md` ilə uyğundur (H = başlanğıcdan saat).

İşarələr: **MUST** demo üçün vacibdir · **SHOULD** vaxt qalsa · etiketlər `01_SCOPE`-dan.

---

## BE-0 Skelet — H0–H2 (BE-A) · MUST

- [x] **BE-0.1** start.spring.io: Java 21, Maven (mvnw), Web, Data JPA, Validation, Security, PostgreSQL, Flyway, Lombok (istəyə görə). `springdoc-openapi-starter-webmvc-ui`, `jjwt-api/impl/jackson`.
- [x] **BE-0.2** `docker-compose.yml` (postgres:16), `application.yml` (`02_ARCHITECTURE` §10), `h2` profili ehtiyat üçün.
- [x] **BE-0.3** `V1__init.sql` — bütün cədvəllər (`02_ARCHITECTURE` §3).
- [x] **BE-0.4** `common/`: `ApiError`, `GlobalExceptionHandler` (kodlar `03_API` cədvəlindən), `PageResponse<T>`, `GeoUtil.distanceMeters`, `AppClock` (demo saatı).
- [x] **BE-0.5** `security/`: JWT (HS256, 12 saat), `JwtFilter` (header və `?token=` query), rol qaydaları, CORS.
- [x] **BE-0.6** `auth/`: `/auth/login`, `/auth/demo-login`, `/me`.

**Qəbul:** `docker compose up -d db && ./mvnw spring-boot:run` xətasız başlayır; Swagger açılır; `curl -X POST /api/v1/auth/demo-login` token qaytarır; tokensiz `/me` → 401 və `{"error":{"code":"UNAUTHORIZED"}}`.

## BE-1 Data və seed — H1–H4 (BE-B) · MUST

- [x] **BE-1.1** `data/processed`-i hazırla (`04_DATA` §2), yoxlama nəticəsini yaz.
- [x] **BE-1.2** `GeoStore` + `GET /geo/layers/{name}`.
- [x] **BE-1.3** `SeedRunner`: users, operators, buses, route_config, surveys (+ cavablar), reports (+ events), coin tarixçəsi, demo passenger səfərləri (vərdiş üçün 12+5).
- [x] **BE-1.4** `GET /stops`, `GET /places` (mocks-dan).
- [x] **BE-1.5** (SHOULD) 5 000 sintetik səfər + 300 rəy generatoru.

**Qəbul:** təmiz DB-də başlatma → `select count(*) from buses` = 66; `/geo/layers/bike-lanes` GeoJSON qaytarır; `/stops?lat=40.3770&lon=49.8180&radius=600` ən azı Elmlər və Nizami-ni qaytarır.

> **Qeyd (2026-10-17):** real `metro_exits` datasına görə Nizami metrosunun çıxışı (40.37919, 49.830063) bu nöqtədən **1050 m** uzaqdadır, `stops_seed.json`-dakı Nizami də (1062 m). 600 m radiusda yalnız Ş. Mehdiyev (222 m) və Elmlər (290 m) çıxır; Nizami `radius=1100`-də görünür. Koordinatlar düzgündür, meyarın radiusu düzəldilməlidir.

## BE-2 SSE və EventBus — H2–H4 (BE-B) · MUST

- [x] **BE-2.1** `stream/EventBus`: emitter reyestri (userId, rol), `publishAll/ToRole/ToUser`, 20 san `ping`, təmizləmə.
- [x] **BE-2.2** `GET /stream?token=` (`SseEmitter(0L)`).
- [x] **BE-2.3** `EventBridge`: Spring domain event-lərini SSE-yə çevirir.

**Qəbul:** iki brauzer tabında `new EventSource('/api/v1/stream?token=..')` → `ping` hər 20 saniyədə gəlir; tab bağlananda reyestrdən silinir (log).

## BE-3 Simulyator və canlı data — H4–H8 (BE-B) · MUST

- [x] **BE-3.1** `SimulationService`: marşrut həndəsəsi (GeoStore və ya `sim_routes.json`), virtual avtobuslar (`buses` bortları ilə), 2 san tick, `bus.positions` SSE.
- [x] **BE-3.2** Doluluq random walk (`04_DATA` §5), interval dəyişəndə baza yenilənir.
- [x] **BE-3.3** Hub sayları, 5 san tick, `hub.counts` SSE; trend və 15 dəq proqnoz.
- [x] **BE-3.4** Tıxac seqmentləri (`traffic_segments.json`), `traffic.updated`.
- [x] **BE-3.5** Skuterlər (SIM) + velo parkları (REAL) → `GET /live/micromobility`.
- [x] **BE-3.6** `GET /live/buses`, `/live/traffic`, `/live/hubs`.
- [x] **BE-3.7** ETA: dayanacağa ən yaxın gələn avtobusun məsafəsi / marşrut sürəti.

**Qəbul:** `/live/buses?route=88` 6 avtobus qaytarır və koordinatlar 2 saniyədə dəyişir; SSE-də `bus.positions` axır; `/live/micromobility` Elmlər ətrafında velo parkı və skuter qaytarır.

## BE-4 Şikayətlər — H3–H8 (BE-A) · MUST

- [x] **BE-4.1** `POST /me/reports` (validasiya, kateqoriya siyahısı, rate limit 3/10 dəq, `code` generatoru `A-XXXX`), `CREATED` event, `report.created` SSE.
- [x] **BE-4.2** `GET /me/reports`, `GET /me/reports/{code}`.
- [x] **BE-4.3** `GET /admin/reports` (status/kateqoriya/operator/q filtrləri, səhifələmə, `created_at desc`), `GET /admin/reports/stats`.
- [x] **BE-4.4** `GET /admin/reports/{code}`, `PATCH .../status` (keçid qaydaları), `POST .../forward`, `POST .../resolve`, `POST .../share`.
- [x] **BE-4.5** Hər status dəyişimində: `report_events` sətri + sərnişinə `notification` + `report.updated` SSE (dispetçerlərə və sahibə).
- [x] **BE-4.6** `late` hesabı (`FORWARDED` + 24 saat).
- [x] **BE-4.7** `GET /admin/reports/export.csv` (eyni filtrlər, səhifələməsiz, BOM + `;` + CRLF, formula-injection qoruması, `X-Total-Count`).

**Qəbul:** mobil token ilə şikayət göndər → dispetçer SSE-də `report.created` 1 saniyədən tez gəlir; `forward` → sərnişinin SSE-sində `notification.created` və `report.updated`; `resolve` → sərnişinə +15 coin.

## BE-5 Coin və bildirişlər — H6–H10 (BE-A) · MUST

- [ ] **BE-5.1** `CoinService.award/spend` (`02_ARCHITECTURE` §9), `@Transactional`, unique ilə təkrar qoruması, `coin.updated` SSE.
- [ ] **BE-5.2** `GET /me/wallet`.
- [ ] **BE-5.3** `POST /me/wallet/conversions` (min 100, 100-ə bölünən) + `BankSimJob` (60 san sonra `COMPLETED` + bildiriş).
- [ ] **BE-5.4** `NotificationService.create(userId, ...)` + `notification.created` SSE; `GET /me/notifications`, `read`, `read-all`.
- [ ] **BE-5.5** `GET /admin/coins/summary`, `GET /admin/coins/conversions`.

**Qəbul:** 245 coin-lik istifadəçi 300 coin çevirmək istəyəndə `COINS_INSUFFICIENT`; 200 çevirəndə balans 45 olur, 60 saniyə sonra bildiriş gəlir; eyni sorğuya iki dəfə mükafat verilmir.

## BE-6 Səfər, rəy, planlayıcı, izləmə — H8–H13 (BE-A) · MUST

- [ ] **BE-6.1** `POST /trips` (`COIN` → −60), `POST /trips/{id}/end` (`detected`, ən yaxın dayanacaq → `alight_stop`, rəy bildirişi).
- [ ] **BE-6.2** `POST /trips/{id}/review` (+5 coin, `ALREADY_DONE`).
- [ ] **BE-6.3** `TripPlannerService` + `WalkService` → `GET /trip/plan` (`02_ARCHITECTURE` §8). Skuter variantı, `accessible` filtri, `why` mətni.
- [ ] **BE-6.4** `GET /me/home` (yaxın dayanacaqlar, ETA, doluluq, `micro`, tıxac banneri).
- [ ] **BE-6.5** `PatternService` → `GET /me/suggestion`, `GET /me/patterns`, `GET /me/trips`; `PUT /me/settings`.
- [ ] **BE-6.6** (SHOULD) Piyada həndəsəsi üçün açıq OSRM piyada servisi, 1,5 san timeout, xəta olarsa L-forma.

**Qəbul:** demo istifadəçi ilə səhər 08:12-də `/me/suggestion` 88 Elmlər → Nərimanov qaytarır; `tracking_consent=false` olduqda `204`; `/trip/plan?to=nerimanov` ən azı 3 variant qaytarır, ən sürətlisi 88-dir; `walk.steps` boş deyil.

## BE-7 Sorğular — H10–H13 (BE-A) · MUST

- [ ] **BE-7.1** `GET /admin/surveys?status`, `GET /admin/surveys/{id}/results` (faiz hesabı).
- [ ] **BE-7.2** `POST /admin/surveys` (`publish` ilə və ya qaralama), `publish`, `close`.
- [ ] **BE-7.3** Publish → auditoriyadakı hər sərnişinə bildiriş (`target: survey:{id}`) + SSE.
- [ ] **BE-7.4** `GET /me/surveys/{id}`, `POST /me/surveys/{id}/answers` (bir dəfə, +coin).

**Qəbul:** dashboard-dan sorğu göndər → telefonda 2 saniyədə bildiriş; cavabla → +10 coin, nəticələr səhifəsində cavab sayı +1.

## BE-8 Doluluq, interval, hub-lar, Biletim, lent — H8–H14 (BE-B) · MUST

- [x] **BE-8.1** `IntervalEngine` + JUnit test vektorları (`02_ARCHITECTURE` §7; eyni vektorlar `packages/shared` testində).
- [x] **BE-8.2** `GET /admin/occupancy/routes`, `/routes/{r}/buses`, `/simulate`, `POST /routes/{r}/interval`, `PUT /auto`.
- [x] **BE-8.3** Topa yığılma detektoru + `GET /admin/occupancy/bunching`, `POST .../message`.
- [ ] **BE-8.4** `GET /admin/hubs` (+ tövsiyə qaydası), `POST /admin/hubs/{id}/apply`.
- [ ] **BE-8.5** `BiletimAdapter` interfeysi + `MockBiletimAdapter` → `GET /admin/biletim/arrivals`.
- [x] **BE-8.6** `FeedService` (`alerts` cədvəli) + `GET /admin/feed`, `GET /admin/kpis`.

**Qəbul:** `POST /admin/occupancy/routes/88/interval {8}` → 30 saniyə ərzində 88-in orta doluluğu 60–70%-ə enir; `route.updated` SSE gəlir; avtomatik rejimdə tövsiyələr özü tətbiq olunur və lentdə görünür.

## BE-9 Analitika, hub təklifi, operatorlar, axtarış, demo — H12–H17 (BE-B) · MUST

- [ ] **BE-9.1** `GET /admin/analytics/kpis`, `/od`, `/alighting` (sintetik + real səfərlər).
- [ ] **BE-9.2** `HubProposalRule` (`02_ARCHITECTURE` §6.2) + `GET /admin/hub-proposals`, `accept`, `reject`, `proposal.created` SSE.
- [ ] **BE-9.3** `GET /admin/operators` (rəylərdən orta, şikayət / 10 min), `GET /admin/buses` (filtrlər, səhifələmə, doluluq simulyatordan).
- [ ] **BE-9.4** `GET /admin/search`.
- [ ] **BE-9.5** `DemoController`: bütün ssenarilər (`03_API` §10) + `reset`. Yalnız `demo` profili.

**Qəbul:** `POST /demo/trigger/artificial-jam` → 4 saniyə ərzində `traffic.updated` (babek=JAM), `alert.created` və `proposal.created` gəlir; `/demo/reset` sonrası demo istifadəçinin balansı 245-dir.

## BE-10 Sabitləşdirmə — H16–H20 · MUST

- [ ] **BE-10.1** Bütün endpoint-ləri Swagger-dən bir dəfə keç; `03_API` ilə fərqləri düzəlt.
- [ ] **BE-10.2** Xəta halları: boş siyahılar, səhv ID, səhv status keçidi — hamısı düzgün kodla.
- [ ] **BE-10.3** Log: hər SSE publish (debug), hər demo trigger (info).
- [ ] **BE-10.4** `demo` profili ilə tam ssenarini 2 dəfə keç (`09_24H_PLAN` demo ssenarisi).
- [ ] **BE-10.5** Laptop LAN IP-si ilə telefondan əlçatanlıq (firewall, CORS).

## Claude Code üçün nümunə tapşırıqlar

```
05_BACKEND_ROADMAP.md BE-4.1–BE-4.2 bəndlərini et. 02_ARCHITECTURE.md §3 cədvəllərindən
və 03_API_CONTRACT.md §6 formalarından istifadə et. Əvvəlcə plan yaz.
Bitəndə curl ilə qəbul meyarını yoxla.
```

```
IntervalEngine-i yaz (02_ARCHITECTURE §7). JUnit testi həmin bölmədəki 5 test vektorunun
hamısını yoxlasın. Test keçməyincə davam etmə.
```
