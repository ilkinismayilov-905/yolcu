# 03 — API müqaviləsi

**Bu fayl qanundur.** Backend bu formaları qaytarır, frontend bu formaları gözləyir. Dəyişiklik lazımdırsa əvvəl bu faylı yenilə, sonra komandaya de.

Baza URL: `/api/v1`. Bütün cavablar JSON, sahə adları `camelCase`, vaxt ISO-8601 (`2026-10-17T08:12:00+04:00`).

İlk saatda Claude Code bu fayldan `packages/shared/src/api.ts` (TS tipləri) və backend-də `dto` record-larını yaradır.

## Ümumi formalar

```jsonc
// Səhifələnmiş siyahı
{ "items": [], "page": 0, "size": 20, "total": 46 }

// Xəta (HTTP 4xx/5xx)
{ "error": { "code": "COINS_INSUFFICIENT", "message": "Yolçu Coin kifayət etmir: 40 var, 300 lazımdır" } }
```

| Xəta kodu | HTTP | Nə vaxt |
|---|---|---|
| `UNAUTHORIZED` | 401 | Token yoxdur və ya etibarsızdır |
| `FORBIDDEN` | 403 | Rol uyğun deyil |
| `NOT_FOUND` | 404 | |
| `VALIDATION` | 400 | `message`-də ilk sahə xətası |
| `COINS_INSUFFICIENT` | 409 | Balans azdır |
| `ALREADY_DONE` | 409 | Rəy və ya sorğu artıq göndərilib |
| `RATE_LIMIT` | 429 | Şikayət limiti |

---

## 1. Auth

**POST** `/auth/login` — `{ "email": "dispatcher@yolcu.az", "password": "demo123" }`
**POST** `/auth/demo-login` — body yoxdur, passenger tokeni qaytarır

```json
{ "token": "eyJ...", "user": { "id": 1, "fullName": "Leyla Məmmədova", "email": "dispatcher@yolcu.az", "role": "DISPATCHER", "coinBalance": 0 } }
```

**GET** `/me` → `User`
**POST** `/me/password` — `{ "currentPassword": "demo123", "newPassword": "demo1234" }` → `{ "ok": true }`. `newPassword` 6–100 simvol. Hazırkı şifrə yanlışdırsa, yeni şifrə qısadırsa və ya hazırkı ilə eynidirsə → `400 VALIDATION`. Köhnə tokenlər etibarlı qalır.

## 2. Geo və yerlər (açıq)

**GET** `/geo/layers/{name}` — `name`: `terminals | routes | bike-parkings | bike-lanes | metro-exits` → GeoJSON `FeatureCollection`
`name` əlavə olaraq: `routes-lite` (xəritə üçün bunu istifadə edin) — `routes`-dan Douglas–Peucker (tolerans 0,00008° ≈ 8–9 m) ilə sadələşdirilmiş eyni 243 feature, `properties` yalnız `{ "route", "name" }`, koordinatlar 5 onluq rəqəm (≈ 650 KB, gzip ≈ 175 KB; `routes` 8,9 MB / gzip 2 MB). `metro-ridership` (stansiya nöqtələri) də verilir.
Cavab başlıqları: `Content-Type: application/geo+json`, `Cache-Control: max-age=3600`, zəif `ETag` (`If-None-Match` → `304`), `Accept-Encoding: gzip` → gzip.

**GET** `/stops?lat=40.379&lon=49.832&radius=600`

```json
[{ "id": "elmler", "name": "Elmlər dayanacağı", "lat": 40.3755, "lon": 49.8156, "routes": ["88","65"], "kind": "BUS", "accessible": true, "distanceMeters": 290 }]
```

Məsafəyə görə sıralanır. `radius` default 600 m; `lat`/`lon` yoxdursa 400 `VALIDATION`.

**GET** `/places` → `[{ "id": "nerimanov", "name": "Nərimanov", "lat": 40.4029, "lon": 49.8711 }]`

## 3. Mobil: əsas ekran

**GET** `/me/home?lat=..&lon=..`

```json
{
  "banner": { "text": "Koroğlu ətrafında tıxac. 4 nömrəli avtobus 15 dəq gecikir", "level": "JAM" },
  "stops": [{
    "stop": { "id": "elmler", "name": "Elmlər dayanacağı", "lat": 40.3755, "lon": 49.8156, "kind": "BUS" },
    "walkMin": 4, "walkM": 180, "crowd": 12,
    "arrivals": [
      { "route": "88", "type": "ELECTRIC", "etaMin": 6, "towards": "Nərimanov", "occupancyLevel": "MEDIUM", "delayed": false },
      { "route": "65", "type": "DIESEL", "etaMin": 9, "towards": "Koroğlu", "occupancyLevel": "FULL", "delayed": false }
    ]
  }]
}
```

`occupancyLevel`: `EMPTY` (<50%) · `MEDIUM` (50–85%) · `FULL` (>85%).

## 4. Mobil: marşrut planlayıcı

**GET** `/trip/plan?fromLat=..&fromLon=..&to=nerimanov&filter=fast&accessible=false`

```json
{
  "to": { "id": "nerimanov", "name": "Nərimanov" },
  "options": [{
    "id": "opt-88",
    "label": "Ən sürətli",
    "totalMin": 31,
    "firstLeg": { "mode": "WALK", "min": 4, "meters": 180 },
    "lines": [{ "route": "88", "type": "ELECTRIC", "kind": "BUS" }],
    "occupancyLevel": "MEDIUM",
    "accessible": true,
    "boardStop": { "id": "elmler", "name": "Elmlər", "lat": 40.3755, "lon": 49.8156 },
    "busEtaMin": 6,
    "rideMin": 22,
    "why": "4 nömrəli marşrut Yusif Səfərov küçəsindəki tıxacdan keçir...",
    "walk": {
      "polyline": [[40.3770,49.8180],[40.3757,49.8181],[40.3755,49.8156]],
      "steps": [
        { "icon": "STRAIGHT", "title": "Binadan çıxıb cənuba gedin", "detail": "Azadlıq prospektinə doğru", "meters": 80 },
        { "icon": "RIGHT", "title": "Azadlıq prospektində sağa dönün", "detail": "Tbilisi prospektinə qədər", "meters": 95 },
        { "icon": "STOP", "title": "Elmlər dayanacağı", "detail": "88-i mavi stendin yanında gözləyin", "meters": 0 }
      ],
      "catchable": true, "spareMin": 2
    }
  }]
}
```

`firstLeg.mode`: `WALK | SCOOTER`. `lines[].kind`: `BUS | METRO`. `icon`: `STRAIGHT | LEFT | RIGHT | CROSS | STOP | METRO | SCOOTER`.

## 5. Mobil: səfər, ödəniş, rəy

**POST** `/trips` — `{ "route": "88", "bort": "10-BB-482", "fromStop": "elmler", "toPlace": "nerimanov", "paidWith": "COIN" }`
→ `{ "trip": { "id": 12, "route": "88", "bort": "10-BB-482", "operator": "BakuBus MMC", "paidWith": "COIN", "startedAt": "..." }, "coinBalance": 185 }`
`paidWith`: `BAKIKART | CARD | COIN`. `COIN` → 300 coin çıxılır (0,60 ₼ × kurs 500), azdırsa `COINS_INSUFFICIENT`.

**POST** `/trips/{id}/end` → `{ "trip": {...}, "reviewNotificationId": 88 }` (həmçinin `notification.created` SSE)

**POST** `/trips/{id}/review`

```json
{ "driverRating": 5, "roadRating": 3, "busRating": 4, "crowdLevel": "NORMAL", "tags": ["Nəzakətli sürücü"], "comment": "" }
```
→ `{ "coinsAwarded": 5, "coinBalance": 190 }` (ikinci dəfə → `ALREADY_DONE`)

### Səfər izləmə və proaktiv təklif

**POST** `/trips/{id}/end` — `{ "detected": "AUTO", "lat": 40.4029, "lon": 49.8707 }` → `{ "trip": { ..., "alightStop": "Nərimanov", "endedAt": "..." }, "reviewNotificationId": 88 }`

**GET** `/me/suggestion?lat=..&lon=..` → vərdiş yoxdursa `204 No Content`

```json
{ "title": "İş günləri bu saatda Nərimanova gedirsiniz", "route": "88", "type": "ELECTRIC",
  "fromStop": { "id": "elmler", "name": "Elmlər" }, "toPlace": "nerimanov", "etaMin": 6,
  "reason": "Son 3 həftədə 12 dəfə 08:00–08:30 arası Elmlər → Nərimanov getmisiniz" }
```

**GET** `/me/trips?page=0&size=20` → `Page<{ id, route, type, fromStop, alightStop, startedAt, endedAt, alightDetected }>`
**GET** `/me/patterns` → `[{ "from": "Elmlər", "to": "Nərimanov", "route": "88", "timeLabel": "08:10", "dayType": "WEEKDAY", "count": 13 }]`
**PUT** `/me/settings` — `{ "trackingConsent": false, "accessibility": true }` → `User`

### Velo və skuter

**GET** `/live/micromobility?lat=..&lon=..&radius=800`

```json
{ "bikeParkings": [{ "id": "bp-12", "lat": 40.3757, "lon": 49.8150, "capacity": 8 }],
  "scooters": [{ "id": "sc-3", "lat": 40.3752, "lon": 49.8158, "battery": 76 }],
  "bikeLanesLayer": "/api/v1/geo/layers/bike-lanes" }
```
`/me/home` cavabında hər dayanacağa `"micro": { "scooters": 3, "bikeParkingSpaces": 8, "bikeLane": true }` əlavə olunur.

## 6. Mobil: şikayət

**POST** `/me/reports`

```json
{ "route": "88", "bort": "10-BB-482", "category": "Dayanacaqda saxlamadı", "text": "...", "hasPhoto": true, "lat": 40.3755, "lon": 49.8156 }
```
→ `ReportDetail` + `coinsAwarded` (həmişə `0`: şikayət coin qazandırmır, sahə uyğunluq üçün qalır). Dashboard-a `report.created` SSE.

**GET** `/me/reports?page=0&size=20` → `{ items: ReportListItem[], page, size, total }` · **GET** `/me/reports/{code}` → `ReportDetail`

```jsonc
// ReportListItem
{ "code": "A-2042", "createdAt": "...", "route": "88", "bort": "10-BB-482", "operator": "BakuBus MMC",
  "category": "Dayanacaqda saxlamadı", "textPreview": "Elmlər dayanacağında...", "status": "NEW", "late": false, "place": "Elmlər" }
// ReportDetail = ReportListItem + 
{ "text": "...", "hasPhoto": true, "coinsGiven": 0, "answerText": null,
  "events": [{ "type": "CREATED", "note": null, "actor": "Sərnişin", "createdAt": "..." }] }
```

Kateqoriyalar (sabit siyahı, `packages/shared`): `Dayanacaqda saxlamadı`, `Kobud davranış`, `Təhlükəli sürüş`, `Telefonla danışırdı`, `Kondisioner işləmir`, `Çirkli salon`, `Çox uzun gözləmə`, `Əlçatanlıq problemi`, `Ödəniş problemi`, `Doluluq`.

## 7. Mobil: sorğu, bildiriş, cüzdan

**GET** `/me/surveys/{id}` → `{ "id": 1, "title": "...", "status": "ACTIVE", "coinReward": 10, "answered": false, "questions": [{ "id": 3, "text": "...", "options": ["Hər gün","Xeyr"] }] }`
`status`: `ACTIVE | CLOSED` (bitmiş sorğu görünür, amma cavab qəbul etmir). `DRAFT` → `NOT_FOUND`.
**POST** `/me/surveys/{id}/answers` — `{ "answers": { "3": "Hər gün", "4": "8 dəqiqə" } }` → `{ "coinsAwarded": 10, "coinBalance": 200 }`
Hər sual cavablanmalıdır, dəyər `options`-dan biri olmalıdır, naməlum sual id-si olmaz → `VALIDATION`. Bir istifadəçi bir dəfə: təkrar → `409 ALREADY_DONE` (bitmiş sorğuda da). Cavablanmamış bitmiş sorğu → `VALIDATION`. Auditoriya yoxlanmır (bildiriş almayan da cavab verə bilər). Coin: `reason=SURVEY`, `ref=survey:{id}`, label `Sorğu: {title}`, + `coin.updated` SSE; dispetçerlərə `survey.updated`.

**GET** `/me/notifications?page=0&size=20` → `Page<{ "id": 88, "type": "SURVEY", "title": "...", "body": "...", "target": "survey:1", "read": false, "createdAt": "..." }>` (ən yenisi əvvəl)
**POST** `/me/notifications/{id}/read` · **POST** `/me/notifications/read-all`

`target` formatları: `survey:{id}` · `report:{code}` · `review:{tripId}` · `wallet`

**GET** `/me/wallet`

```json
{ "balance": 245, "aznEquivalent": "0.49", "rate": 500, "earnedToday": 5,
  "history": [{ "label": "Banka çevirmə: 1,00 ₼", "amount": -500, "createdAt": "...", "status": "PENDING" },
              { "label": "Sorğu: interval rahatlığı", "amount": 10, "createdAt": "...", "status": null }],
  "rules": [{ "label": "Səfərdən sonra rəy", "amount": "+5" }, { "label": "Sorğuya cavab", "amount": "+5–20" }] }
```
- `history`: ledger (`coin_tx`), ən yenisi əvvəl, son 50 sətir. `SEED` ("Başlanğıc balansı") və `DEMO` sətirləri **göstərilmir**, ona görə tarixçənin cəmi balansa bərabər olmaya bilər.
- `status`: yalnız bank çevirməsi sətirlərində `PENDING | COMPLETED` (mobil: "icra olunur" / "tamamlandı"), qalanlarında `null`.
- `earnedToday`: bu gün (demo saatının günü, Bakı) qazanılan müsbət sətirlərin cəmi, `SEED`/`DEMO` daxil deyil.
- `rules`: yalnız qazanma qaydaları (`+` ilə), `/admin/coins/rules`-dan törəyir (eyni mənbə: `CoinService.rules()`).

**POST** `/me/wallet/conversions` — `{ "coins": 1000, "cardMask": "•• 0193" }` → `201 { "id": 7, "coins": 1000, "azn": "2.00", "status": "PENDING", "coinBalance": 245 }`
- `coins`: min 500 (= 1 ₼) və 100-ə bölünən (400, 499, 550 → səhv), əks halda `400 VALIDATION` (`coins: minimum 500 və 100-ə bölünən olmalıdır`). Balans azdırsa `409 COINS_INSUFFICIENT` (`Yolçu Coin kifayət etmir: 245 var, 500 lazımdır`), heç nə yazılmır.
- `cardMask` istəyə bağlıdır (default `•• 0193`, ≤ 30 simvol). Format `•• NNNN`; "Partnyor bank" sözünü UI əlavə edir.
- `azn = coins / yolcu.coin-rate` (500), 2 rəqəm, string (500 → `1.00`, 1000 → `2.00`). Ledger: `reason=CONVERSION`, `ref=conv:{id}`, label `Banka çevirmə: 2,00 ₼`, + `coin.updated` (sahibə) və `conversion.updated` (DISPATCHER, ADMIN).
- Bank (SIM, `BankSimJob`): `yolcu.demo.bank-delay-seconds` (60) san sonra `COMPLETED` → sahibə bildiriş (`type: COIN`, `title: Bank çevirməsi tamamlandı`, `body: 1000 Yolçu Coin → 2,00 ₼ kartınıza köçürüldü (•• 0193)`, `target: wallet`) + `COIN` alert (`Bank çevirməsi tamamlandı: 2,00 ₼` / `Nigar Ə., 1000 Yolçu Coin, Partnyor bank •• 0193`, `p-coins`) + `conversion.updated`. Gecikmə real (divar) saatı ilə sayılır, server restart-ından sağ çıxır.

## 8. Canlı data (SIM)

**GET** `/live/buses?route=88` → `[{ "bort": "10-BB-482", "route": "88", "type": "ELECTRIC", "lat": .., "lon": .., "bearing": 160, "occupancy": 84, "capacity": 90 }]`
**GET** `/live/traffic` → `[{ "id": "yusif-seferov", "name": "Yusif Səfərov küç.", "level": "JAM", "speedKmh": 9, "line": [[lat,lon],...] }]` (`level`: `FREE | SLOW | JAM`)
**GET** `/live/hubs` → `[{ "id": "may28", "name": "28 May", "lat": .., "lon": .., "count": 340, "capacity": 400, "trend10": 18, "forecast15": 420 }]`

Qeydlər (hamısı giriş tələb edir, istənilən rol):
- `/live/buses`: `route` verilməsə bütün simulyasiya avtobusları. `occupancy` faizdir (0–100), `capacity` yer sayı, `bearing` 0–359°. Mövqe real trayektoriya (`routes` qatı, `properties.route`) üzərindədir, 2 saniyədə yenilənir. Bortlar `buses` cədvəlindən (`status = LINE`); marşrutda `route_config.busCount`-dan az bort varsa, o qədər avtobus göstərilir.
- `/live/traffic`: `line` real küçə boyuncadır (seqmentin dayanacaqlarından keçən real avtobus trayektoriyasının hissəsi), heç vaxt dayanacaqlar arası düz xətt deyil. Uyğun trayektoriya tapılmayan seqment siyahıda **yoxdur** (siyahı 7-dən az ola bilər). `level` saata görə: pik saatlarda (07–10, 17–20) `baseLevel`, qalan vaxt bir pillə yüngül.
- `/live/hubs`: `lat/lon` metro stansiyasının real nöqtəsidir (çıxışların mərkəzi, `metro-ridership`). `trend10` = son 10 dəqiqədəki dəyişmə, `forecast15 = count + trend10 × 1,5`.
- `/live/micromobility`: `radius` default 800, 1–5000 m (əks halda `VALIDATION`); `lat`/`lon` yoxdursa `VALIDATION`. `bikeParkings` real AYNA qatıdır (məsafəyə görə sıralı); mənbədə tutum yoxdur, `capacity` hələlik hamısında `10`-dur. `scooters` SIM.

SSE yükləri (`02_ARCHITECTURE` §4): `bus.positions` → `/live/buses` elementi ilə eyni massiv (bütün marşrutlar); `hub.counts` → `[{ "hubId": "may28", "count": 340, "trend": 18, "forecast15": 420 }]` (DISPATCHER, ADMIN; hər 5 san); `traffic.updated` → `/live/traffic` ilə eyni massiv, yalnız hər hansı `level` dəyişəndə; `alert.created` → `Alert` (DISPATCHER, ADMIN).

## 9. Admin (DISPATCHER, ADMIN)

### Dashboard
**GET** `/admin/kpis` → `{ "avgSpeedKmh": 18, "avgSpeedYesterday": 22, "waitingAtHubs": 12480, "avgOccupancy": 74, "overfullBuses": 63, "satisfaction": 4.2, "satisfactionWeekDelta": 0.2 }`
**GET** `/admin/feed?limit=50` → `Alert[]` · `{ "id": 1, "category": "OCC", "title": "...", "body": "...", "target": "p-occ", "createdAt": "..." }`
`Alert.category`: `HUB | OCC | AI | BILETIM | REPORT | EVENT | WEATHER | COIN` (`COIN`: bank çevirməsi tamamlandı, `target: p-coins`). `target` — web səhifə id-si (`p-hubs`, `p-occ`, `p-split`, `p-express`, `p-reports` ...). Lent ən yenisi əvvəl, `limit` 1–200 (default 50). Yeni şikayət gələndə `REPORT` alert-i yaranır (`title`: `Yeni şikayət #A-2044: 88, <kateqoriya>`, `body`: mətn, `target`: `p-reports`) və `alert.created` SSE yayılır.
KPI mənbələri: `avgSpeedKmh` — simulyasiya avtobuslarının marşrut sürətlərinin orta qiyməti; `avgSpeedYesterday` — hələlik sabit `22`; `waitingAtHubs` — `/live/hubs` `count` cəmi; `avgOccupancy` — avtobusların orta doluluğu (%); `overfullBuses` — doluluğu > 90% olan simulyasiya avtobusları; `satisfaction` — bütün rəylərin orta balı (3 reytinqin ortası, 1 onluq); `satisfactionWeekDelta` — son 7 gün − əvvəlki 7 gün.

### Hub kameraları
**GET** `/admin/hubs` → `/live/hubs` + `{ "level": "HIGH", "avgWaitMin": 11, "camera": "K-201", "recommendation": "65 intervalı 12 → 9 dəq", "kind": "HUB" }`
**POST** `/admin/hubs/{id}/apply` → `{ "ok": true }` (alert yazır)

Qeydlər (BE-8.4):
- **Siyahı:** `/live/hubs` ilə eyni 8 element, eyni sıra (`koroglu, may28, avtovagzal, genclik, nizami, elmler, nerimanov, sahil`), eyni `count/trend10/forecast15` (SIM). `lat/lon` real metro stansiyası nöqtəsidir.
- **`kind`:** `HUB | STOP` (dizayn `#p-hubs` seqmenti "Hub-lar" / "Dayanacaqlar"). `HUB` = `may28`, `koroglu`, `avtovagzal`; qalanları `STOP`. Ayrıca dayanacaq kamerası yoxdur — "Dayanacaqlar" bu 5 elementdir. "Yalnız sıx" = `level ≠ LOW`.
- **`level`:** `r = count / capacity`; `r ≥ 0,8` → `HIGH`, `r ≥ 0,55` → `MEDIUM`, əks halda `LOW` (dizayn: `HIGH` = "Çox sıx"/bad, `MEDIUM` = "Sıx"/warn, `LOW` = "Normal"/ok).
- **`avgWaitMin`:** `min(20, round(2 + 10 × r))`, tam dəqiqə.
- **`camera`:** `mocks/hubs.json` `camera` (`K-201` ...).
- **`recommendation`** (`string | null`), qayda ardıcıllığı:
  1. `level = LOW` → `null` (UI: "Dəyişiklik lazım deyil", "Tətbiq et" düyməsi yoxdur).
  2. `avtovagzal`: növbəti 30 dəq ərzində ≥ 40 bilet satılmış Biletim gəlişi varsa → `"08:20 Gəncə reysi üçün M1 ekspressi işə sal"` (ilk belə gəliş, demo gəlişi daxil).
  3. Hub-dan keçən marşrutlar (`stops_seed`-də eyni id-li dayanacağın `routes`-u) arasında `IntervalEngine` tövsiyəsi `recommendedMin < intervalMin` olan, `avgOccupancy`-si ən yüksək marşrut → `"88 intervalı 12 → 8 dəq"` (rəqəmlər `/admin/occupancy/routes` ilə eyni).
  4. Yoxdursa: `HIGH` → `"Platformaya nəzarətçi göndər, gözləyənlərə tətbiqdə metro təklif et"`, `MEDIUM` → `null`.
- **`POST /apply`:** naməlum id → `404 NOT_FOUND`. Tövsiyə `null`-dırsa heç nə yazılmır, yenə `{ "ok": true }`. Əks halda lentə `AI` alert: `title` `28 May: tövsiyə tətbiq olundu`, `body` = `recommendation` mətni, `target` `p-hubs` (+ `alert.created`). Tövsiyə interval tipidirsə (qayda 3) interval həm də **real tətbiq olunur** — `POST /admin/occupancy/routes/{r}/interval` ilə eyni: `route_config` yazılır, `route.updated` SSE və öz `AI` alert-i (`88: interval 12 → 8 dəq tətbiq olundu`, `p-occ`). Yəni interval tövsiyəsində lentə 2 alert düşür. Tətbiqdən sonra tövsiyə yenidən hesablanır (növbəti GET-də başqa mətn və ya `null` ola bilər). Rol: DISPATCHER, ADMIN; PASSENGER → `403`.

### Doluluq və interval
**GET** `/admin/occupancy/routes`

```json
[{ "route": "88", "type": "ELECTRIC", "operator": "BakuBus MMC", "busCount": 6, "avgOccupancy": 92,
   "intervalMin": 12, "recommendedMin": 8, "satNow": 3.8, "satAfter": 4.5, "autoMode": false }]
```
**GET** `/admin/occupancy/routes/{route}/buses` → `[{ "bort": "10-BB-482", "type": "ELECTRIC", "near": "28 May", "occupancy": 84, "capacity": 90 }]`
**POST** `/admin/occupancy/routes/{route}/interval` — `{ "intervalMin": 8 }` → yenilənmiş `RouteOccupancy` + `route.updated` SSE
**GET** `/admin/occupancy/simulate?route=88&intervalMin=10` → `{ "occupancy": 77, "waitMin": 5, "busesNeeded": 8, "sat": 4.2, "note": "2 əlavə avtobus lazımdır..." }`
**PUT** `/admin/occupancy/auto` — `{ "enabled": true }` → `{ "enabled": true }`
**GET** `/admin/occupancy/bunching` → `[{ "route": "88", "front": { "bort": "10-BB-482", "occupancy": 84 }, "back": { "bort": "10-BB-511", "occupancy": 41 }, "gapMin": 2, "near": "28 May" }]`
**POST** `/admin/occupancy/bunching/{route}/message` → `{ "ok": true, "text": "10-BB-511 sürücüsünə: Sahil dayanacağında 3 dəqiqə gözləyin" }`

Qeydlər (BE-8.1–8.3):
- **Hesablama:** `recommendedMin`, `satNow`, `satAfter`, `/simulate` — `IntervalEngine` (`02_ARCHITECTURE` §7), `packages/shared/src/headway.ts` ilə eyni: `simulate(avgOccupancy, intervalMin, busCount, iv)`. Giriş `avgOccupancy` artıq tam ədədə yuvarlanmış dəyərdir (cavabdakı ilə eyni). `satNow`/`satAfter` 1 onluğa yuvarlanır, fərq yuvarlanmış dəyərlərdən hesablanır. `recommendedMin` **həmişə** formula nəticəsidir (tətbiq olunan dəyərlə əvəz olunmur); interval dəyişən kimi canlı doluluq hələ köhnədir, ona görə ilk ~30 san tövsiyə müvəqqəti fərqli görünə bilər.
- **`/routes`:** yalnız simulyasiyada avtobusu olan marşrutlar (88, 4, 65, 33, M1; `H1`-in `LINE` bortu yoxdur → siyahıda yoxdur). Sıra: `avgOccupancy` azalan. `busCount` = `route_config.bus_count` (plan; xəritədə daha az bort ola bilər). `avgOccupancy` = həmin marşrutun simulyasiya avtobuslarının orta doluluğu (%). `type`/`operator` — marşrutun əsas tipi və operatorun adı. `autoMode` = `route_config.auto_mode`.
- **`occupancy` vahidi:** `/routes/{route}/buses` və `/bunching`-də `occupancy` **sərnişin sayıdır** (`round(% × capacity / 100)`, məs. `84` / `90` yer); `/live/buses`, `bus.positions` və `RouteOccupancy.avgOccupancy`-də **faizdir**. `/simulate` `occupancy` — faiz.
- **`/routes/{route}/buses`:** `near` — avtobusa ən yaxın dayanacağın adı. Naməlum marşrut → `NOT_FOUND`.
- **`POST .../interval`:** `intervalMin` 6–16 (engine-in `MIN/MAX`-ı; 33 üçün tövsiyə 16-dır), əks halda `VALIDATION`; naməlum marşrut → `NOT_FOUND`. `route_config.interval_min` yazılır, simulyator növbəti tick-də (≤ 2 san) yeni intervala keçir, doluluq ~30 san ərzində `occ × new/old`-a enir. Cavab və `route.updated` SSE (hamıya) eyni `RouteOccupancy` sətiridir. Lentə `AI` alert: `title` `88: interval 12 → 8 dəq tətbiq olundu`, `body` `Gözlənilən doluluq 92% → 61%, məmnunluq 3,8 → 4,5. Lazım olan avtobus: 9`, `target` `p-occ` (+ `alert.created`). Eyni dəyər göndərilsə heç nə dəyişmir (event yoxdur).
- **`/simulate`:** `route` naməlum → `NOT_FOUND`; `intervalMin` 6–16 deyil → `VALIDATION`. `waitMin` = `iv / 2` (kəsr ola bilər, `4.5`), `sat` = gözlənilən məmnunluq. `note`: `busesNeeded − busCount > 0` → `"N əlavə avtobus lazımdır. Təklif: <ən boş digər marşrut>-dən min(2,N)[, depodan N−2]."`; `< 0` → `"N avtobus azad olur, tələbatı yüksək marşruta verilə bilər."`; `0` → `"Avtobus sayı dəyişmir."`
- **`PUT /auto`:** qlobaldır — bütün marşrutların `auto_mode`-unu yazır. Açıq olanda hər 30 san `recommendedMin ≠ intervalMin` olan marşrutlarda tövsiyə tətbiq olunur (son 60 san ərzində dəyişdirilmiş marşrut atlanır — doluluq hələ yerinə oturmayıb). Hər tətbiq → `route.updated` + `AI` alert (`title` `4: interval 10 → 7 dəq (avtomatik rejim)`). `enabled` yoxdursa → `VALIDATION`. Rejim dəyişəndə `route.updated` göndərilmir — `/routes`-u yenidən çəkin.
- **`/bunching`:** eyni marşrutda ardıcıl iki avtobus arası (xətt boyu məsafə / marşrut sürəti) < 3 dəq; marşrut başına ən yaxın cüt. `front` = cütün **daha dolu** avtobusu, `back` = daha boşu (fiziki sıra deyil). `gapMin` tam dəqiqə (aşağı, min 1). `near` — `front`-a ən yaxın dayanacaq. Topa yığılma başlayanda lentə `OCC` alert (`88: iki avtobus topa yığılıb`, `10-BB-482 və 10-BB-511 arası 2 dəq`, `p-occ`) — marşrut başına bir epizodda bir dəfə, ən çoxu 5 dəqiqədə bir; server başlayanda artıq olan topa yığılma üçün alert yoxdur (seed lentdə var). Demo: 88-də 10-BB-511 başlanğıcda 10-BB-482-dən 2 dəq arxada olur.
- **`POST /bunching/{route}/message`:** cütün fiziki olaraq **arxadakı** avtobusu 3 dəq dayanır (aralıq açılır, ~1 dəq sonra siyahıdan çıxır). `text` — sürücüyə gedən mətn. Həmin marşrutda topa yığılma yoxdursa → `NOT_FOUND`. Lentə yazılmır.

### Avtovağzal və Biletim.az (SIM)
**GET** `/admin/biletim/arrivals` → `[{ "time": "08:20", "from": "Gəncə", "plate": "90-AB-114", "ticketsSold": 41, "status": "Yoldadır, 18 km", "nextCityBusMin": 18, "level": "HIGH" }]`

Qeydlər (BE-8.5):
- **Mənbə:** `BiletimAdapter` interfeysi; indi `MockBiletimAdapter` (`mocks/biletim_arrivals.json`). Server başlayanda demo saatı (dəqiqəyə yuvarlanmış) "anker" olur: `time = anker + minutesFromNow`. Vaxtı keçmiş gəliş tam 180 dəqiqəlik dövrlər qədər irəli sürüşür, ona görə siyahının sonunda yenidən görünür (siyahı həmişə növbəti 3 saatdır, 7 element). `status` mətni mock-dan sabitdir.
- **Sıra:** `time` artan (real an üzrə, gecə yarısını keçsə də düzgün). Demo gəlişi (bax §10 `ganja-arrival`) varsa **həmişə birinci**dir.
- **`time`:** `"HH:mm"` Bakı vaxtı (demo saatı).
- **`level`:** `ticketsSold ≥ 35` → `HIGH`, `≥ 20` → `MEDIUM`, əks halda `LOW`.
- **`nextCityBusMin`** ("Şəhərə keçid"): bütün gələn sərnişinlərin şəhər avtobusuna minməsi üçün təxmini dəqiqə = `max(1, round(ticketsSold / 15 × h))`; `h` = Avtovağzaldan keçən simulyasiya marşrutlarının (`88`, `4`, `M1`) birləşik intervalı `1 / Σ(1 / intervalMin)` (`route_config`-dən canlı; interval qısalanda bu rəqəm də azalır). 15 = bir şəhər avtobusunda boş yer (sabit).
- Rol: DISPATCHER, ADMIN; PASSENGER → `403`.

### Şikayətlər
**GET** `/admin/reports?status=NEW&category=&operator=bb&q=10-BB&page=0&size=10` → `Page<ReportListItem>`
`status` filtri: `NEW | REVIEW | FORWARDED | CLOSED` (`CLOSED` = `ANSWERED` + `RESOLVED`)
**GET** `/admin/reports/export.csv?status=&category=&operator=&q=` → CSV fayl, `/admin/reports` ilə eyni filtrlər, səhifələmə yoxdur (bütün uyğun sətirlər, ən yenisi əvvəl)
Başlıqlar: `Content-Type: text/csv;charset=UTF-8` · `Content-Disposition: attachment; filename="sikayetler-YYYY-MM-DD.csv"` · `X-Total-Count: <sətir sayı>`. Gövdə: UTF-8 BOM, ayırıcı `;`, CRLF, başlıq `ID;Vaxt;Marşrut;Bort;Operator;Mövzu;Mətn;Status;Yer;Gecikir` (`Vaxt` = `yyyy-MM-dd HH:mm` Bakı vaxtı, `Status` azərbaycanca, `Gecikir` = `Bəli`/`Xeyr`, `Mətn` tam mətn). `=`, `+`, `-`, `@` ilə başlayan xanaların əvvəlinə `'` əlavə olunur
**GET** `/admin/reports/stats` → `{ "today": 214, "open": 23, "forwarded": 6, "late": 2, "newCount": 15 }`
**GET** `/admin/reports/{code}` → `ReportDetail` (+ `"passenger": "Gizli"`)
**PATCH** `/admin/reports/{code}/status` — `{ "status": "REVIEW", "note": "" }` → `ReportDetail`
**POST** `/admin/reports/{code}/forward` → `ReportDetail` (status `FORWARDED`, sərnişinə bildiriş)
**POST** `/admin/reports/{code}/resolve` — `{ "answerText": "Sürücü ilə izahat işi aparıldı" }` → `ReportDetail` (coin verilmir; bildiriş: `#A-2042: təşəkkür edirik! Rəyiniz xidməti yaxşılaşdırmağa kömək edir`)
**POST** `/admin/reports/{code}/share` — `{ "targets": ["OPERATOR","YPX"], "hidePersonal": true }` → `{ "ok": true }` · `targets`: `OPERATOR`, `YPX` (Dövlət Yol Polisi), `CITY` (Bakı Şəhər İcra Hakimiyyəti)

Status keçidləri: `NEW → REVIEW → FORWARDED → ANSWERED/RESOLVED`. `NEW`-dən birbaşa `FORWARDED` də olar. Səhv keçid → `VALIDATION`.
`late` = `FORWARDED` və `forwardedAt` 24 saatdan köhnədir (seed-də bir neçəsi belədir).

### Sorğular
**GET** `/admin/surveys?status=ACTIVE&page=0&size=20` → `Page<SurveyListItem>`, ən yenisi əvvəl (`createdAt desc`). `status`: `DRAFT | ACTIVE | CLOSED` və ya boş (hamısı); başqa dəyər → `VALIDATION`.
`SurveyListItem` = `{ "id": 1, "title": "...", "status": "ACTIVE", "audience": "ALL", "audienceLabel": "Bütün istifadəçilər", "responses": 3140, "coinReward": 10, "createdAt": "...", "publishedAt": "...", "closedAt": null }`
`responses` = tarixi seed cəmi (`mocks/surveys.json`) + real cavablar. `audience` / `audienceLabel`: `ALL` Bütün istifadəçilər · `AVTOVAGZAL_2KM` Avtovağzal ətrafı, 2 km · `ROUTE_88_4` 88 və 4 sərnişinləri.
**GET** `/admin/surveys/{id}/results` → `{ "id": 1, "responses": 3140, "questions": [{ "id": 1, "text": "...", "total": 3139, "options": [{ "label": "Hər gün", "count": 690, "percent": 22 }] }] }`
`percent` = sualın öz `total`-ına görə, ən böyük qalıq üsulu ilə yuvarlaqlaşdırılır: bir sualın faizləri cəmi həmişə dəqiq 100 (heç kim cavab verməyibsə hamısı 0). Sualın `total`-ı seed yuvarlaqlaşdırmasına görə `responses`-dan bir neçə vahid fərqlənə bilər.
**POST** `/admin/surveys` — `{ "title": "...", "audience": "ALL", "coinReward": 10, "questions": [{ "text": "...", "options": ["A","B"] }], "publish": true }` → `201 SurveyListItem`
Validasiya (`VALIDATION`): `title` 1–160 simvol; `audience` yuxarıdakı 3 dəyərdən biri; `coinReward` ∈ {5, 10, 20}; `questions` 1–10; hər sualın `text` 1–500, `options` 2–10 təkrarsız, hər biri 1–80 simvol. `publish: false` → `DRAFT`.
**POST** `/admin/surveys/{id}/publish` (`DRAFT → ACTIVE`) · **POST** `/admin/surveys/{id}/close` (`ACTIVE → CLOSED`) → `SurveyListItem`. Səhv keçid → `VALIDATION`.
Publish → auditoriyadakı hər sərnişinə `notification` (`type: SURVEY`, `title` = sorğu başlığı, `body` = "1 dəqiqəlik sorğu. Cavab verin, {coinReward} Yolçu Coin qazanın.", `target: survey:{id}`) + hər birinə `notification.created` SSE. Auditoriya: `ALL` = bütün sərnişinlər; `ROUTE_88_4` = 88 və ya 4 ilə səfəri olanlar; `AVTOVAGZAL_2KM` = Avtovağzalın 2 km-lik qutusunda düşən səfəri olanlar. Sintetik (demo) sərnişinlər heç bir auditoriyaya daxil deyil.
Hər yeni cavab (real və ya simulyasiya), publish və close → `survey.updated` SSE (DISPATCHER, ADMIN), data = `SurveyListItem`.

### Yolçu Coin proqramı
**GET** `/admin/coins/summary` → `{ "earnedToday": 48210, "earnedTodayUsers": 9120, "inCirculation": 3100000, "convertedThisMonthAzn": "12400.00", "rate": 500 }`
Mənbə: pilot üzrə baza rəqəmlər (dizayndakı `48 210 / 9 120 / 3,1 mln / 12 400 ₼`, `CoinService`-də sabit) + DB: `earnedToday`/`earnedTodayUsers` — bu gün müsbət `coin_tx` cəmi / fərqli istifadəçi (`SEED`, `DEMO` daxil deyil); `inCirculation` — sərnişinlərin `coin_balance` cəmi; `convertedThisMonthAzn` — bu ay `COMPLETED` olan çevirmələrin `azn` cəmi (string, 2 rəqəm). Web `3100000` → "3,1 mln" formatlayır.

**GET** `/admin/coins/conversions?page=0&size=10` → `Page<ConversionRow>`, ən yenisi əvvəl (id üzrə), `size` 1–100 (default 10):
`{ "id": 7, "userName": "Elvin M.", "phoneMask": "+994 55 ••• 08", "coins": 1200, "azn": "2.40", "cardMask": "•• 4410", "status": "PENDING", "createdAt": "...", "completedAt": null }`
`userName` = ad + soyadın ilk hərfi; `phoneMask` = ilk 7 simvol + ` ••• ` + son 2 rəqəm; `status`: `PENDING | COMPLETED`. Web status mətni: `PENDING` → "Gözləyir", `COMPLETED` → "Tamamlandı"; kart sütunu `Partnyor bank {cardMask}`.
Seed: cədvəl boşdursa (mövcud DB-də də) 5 sətir — 3 `COMPLETED` + 2 `PENDING` (dizayndakı CONV məbləğləri: 500, 1200, 300, 800, 2000), seed sərnişinləri adından. Seed `PENDING` sətirləri bank simulyasiyasına **düşmür** (demo boyu "Gözləyir" qalır).

**GET** `/admin/coins/rules` → `CoinRule[]` (səhifələnmir, `CoinService` sabitlərindən):
`[{ "code": "REVIEW", "action": "Səfərdən sonra rəy", "amount": "+5", "limit": "Hər səfər üçün 1 dəfə" }, { "code": "SURVEY", "action": "Sorğuya cavab", "amount": "+5–20", "limit": "Sorğu başına 1 dəfə" }, { "code": "FARE", "action": "Gediş haqqını Yolçu Coin ilə ödəmək", "amount": "−300", "limit": "0,60 ₼ gediş" }]`
Kurs 500 Yolçu Coin = 1 ₼. Şikayət qaydaları (`REPORT`, `REPORT_VERIFIED`) yoxdur: şikayət coin qazandırmır. `FARE` = 0,60 ₼ × kurs.
`amount` göstəriş üçün string-dir (işarə ilə, `−` U+2212).

### Operatorlar və avtopark
**GET** `/admin/operators` → `[{ "id": 1, "code": "bb", "name": "BakuBus MMC", "fleetTotal": 640, "onLine": 584, "avgRating": 4.6, "complaintsPer10k": 3.1, "punctuality": 91, "routes": ["1","2","4","88"] }]`
**GET** `/admin/buses?operator=xf&status=LINE&type=ELECTRIC&q=99-XF&page=0&size=12` → `Page<{ bort, operator, route, type, model, status, occupancy, driverRating }>`
**GET** `/admin/buses/{bort}` → `BusDetail` (yeni, BE-9.3)

Qeydlər (BE-9.3):
- `operators`: massiv (səhifələmə yoxdur), sıra `id` üzrə (bb, xf, ct, 6d). `fleetTotal` = seed-dəki şəhər miqyaslı park (`operators.fleet_total`, 640/300/140/60). `onLine` = `round(fleetTotal × xətdəki payı)`, pay = DB-dəki həmin operator avtobuslarında `status = LINE` olanların nisbəti (DB-də cəmi 66 avtobus var; **qısa yol**, tam park import olunanda real say olacaq). `avgRating` = operatorun bütün rəylərində `(driver + road + bus) / 3` ortası, 1 rəqəm (rəy yoxdursa `null`). `complaintsPer10k` = demo saatına görə **son 30 gündə** operatora aid şikayətlər ÷ həmin dövrdə operatorun avtobuslarında (`trips.bort → buses`) başlayan səfərlər × 10 000, 1 rəqəm (səfər yoxdursa `0`). Seed ilə xam rəqəm ~60–120 olur (40 şikayət / 5 000 səfər), UI olduğu kimi göstərsin. `punctuality` = seed dəyəri (`mocks/operators.json`), real cədvələ uyğunluq mənbəyi yoxdur (**qısa yol**). `routes` = seed siyahısı.
- `buses` item: `{ "bort": "10-BB-120", "operator": "BakuBus MMC", "operatorCode": "bb", "route": "65", "type": "ELECTRIC", "model": "BMC Procity 12m", "capacity": 90, "status": "LINE", "occupancy": 56, "driverRating": 4.6 }`. `operator` = ad (digər siyahılarla eyni), `operatorCode` əlavə. `occupancy` = simulyatordan faiz, yalnız `LINE` və simulyasiya olunan marşrutlarda (`route_config`: 88, 4, 65, 33, M1...); qalanlarında `null`. `driverRating` = həmin bortun rəylərində `driver_rating` ortası, 1 rəqəm, yoxdursa `null`.
- Filtrlər: `operator` = operator kodu; `status` = `LINE | DEPOT | REPAIR`; `type` = `ELECTRIC | DIESEL`; `q` = bort və ya marşrutda hərf fərqi olmadan alt sətir (≤ 40 simvol). Səhv dəyər → `VALIDATION`. `page ≥ 0`, `size` 1–100 (default 12). Sıra: `bort` artan.
- `BusDetail` = `buses` item sahələri + `"reviewCount": 10`, `"reports": [ReportListItem]` (bu bort üzrə son 5, ən yenisi əvvəl), `"reviews": [{ "driverRating": 5, "roadRating": 4, "busRating": 4, "crowdLevel": "FULL", "tags": ["Vaxtında gəldi"], "comment": null, "createdAt": "..." }]` (son 5). `{bort}` hərf/tire fərqinə dözümlüdür (`10bb120` da tapır). Naməlum bort → `404 NOT_FOUND`.

### Data analitika
**GET** `/admin/analytics/kpis` → `{ "trackedTrips30d": 1240000, "consentUsers": 31200, "avgTripMin": 27, "longestTransferWaitMin": 14, "suggestionAcceptance": 41 }`
**GET** `/admin/analytics/od?from=07:00&to=10:00` → `{ "zones": ["Avtovağzal","Koroğlu","28 May","Nizami","Gənclik","Nərimanov"], "matrix": [[0,8200,14600,6100,5300,4400]] }`
**GET** `/admin/analytics/alighting?limit=5` → `[{ "stop": "Nərimanov metro", "perDay": 18420, "peak": "08:35", "note": "..." }]`
Hub matrisi, proqnoz, ödənişli yol və data məhsulları FAKE-dir: `mocks/analytics_static.json`.

Qeydlər (BE-9.1): mənbə `trips` cədvəlidir (real + 5 000 sintetik seed səfəri), dövr — demo saatına görə son 30 gün, saatlar Bakı vaxtı (UTC+4).
- **Rəqəmlər xam saydır**, yuxarıdakı nümunələr (1 240 000, 8200, 18 420) şəhər miqyasında illüstrasiyadır. Seed ilə: `trackedTrips30d` ≈ 4 800, `consentUsers` ≈ 21, OD xanaları ~40–280, `perDay` ~20–40. UI rəqəmi olduğu kimi göstərsin.
- `kpis`: `trackedTrips30d` — son 30 gündə başlayan səfər sayı; `consentUsers` — həmin səfərləri olan `tracking_consent = true` istifadəçi sayı; `avgTripMin` — `ended_at − started_at` ortası (dəq, tam); `longestTransferWaitMin` — `route_config`-də ən uzun interval (keçiddə ən pis gözləmə; interval tətbiq olunanda dəyişir); `suggestionAcceptance` — hələlik sabit `41` (təklif qəbulu saxlanmır).
- `od`: `from`/`to` `HH:mm` (00:00–23:59), default `07:00`–`10:00`, `from < to`, əks halda `VALIDATION`. `[from, to)` aralığında başlayan səfərlər. `zones` həmişə bu 6-dır (sıra sabit); `matrix[i][j]` = `zones[i]`-də minib (`from_stop`) `zones[j]`-yə gedən (`to_place`) səfər sayı. Zonalardan kənar səfərlər sayılmır.
- `alighting`: `limit` 1–50 (default 5), əks halda `VALIDATION`. `alight_stop` üzrə ən çoxdan aza. `perDay` = 30 günlük say / 30 (tam); `peak` = ən çox düşülən 5 dəqiqəlik slot (`ended_at`, `"08:35"`); `note` = `"<əsas mənşə> → <dayanacaq> axını, <ən çox 2 marşrut 'və' ilə>"`.

### Hub təklifləri
**GET** `/admin/hub-proposals?status=NEW` → `[{ "id": 3, "name": "Fətəli Xan Xoyski pr. – Gənclik metrosu", "lat": 40.3990, "lon": 49.8560, "jamMinutes": 35, "routesCount": 3, "metroM": 300, "waiting": 58, "paxPerHour": 2400, "confidence": 82, "items": ["Avtobus cibi, 2 avtobusluq"], "impact": { "transferMin": -6, "toMetroPerDay": 900, "waitingPct": -40, "costAzn": "180–240 min" }, "status": "NEW" }]`
**POST** `/admin/hub-proposals/{id}/accept` · **POST** `/admin/hub-proposals/{id}/reject` → yenilənmiş `HubProposal`

Qeydlər (BE-9.2):
- `HubProposal` əlavə sahələr: `reason` (drawer-dəki izah mətni, `metroM` ilə) və `createdAt`. Siyahı **massivdir** (səhifələmə yoxdur), ən yenisi əvvəl. `status` filtri: `NEW | ACCEPTED | REJECTED` və ya boş (hamısı); başqa dəyər → `VALIDATION`.
- Keçidlər: yalnız `NEW → ACCEPTED` və `NEW → REJECTED`; başqa keçid → `VALIDATION`, naməlum `id` → `NOT_FOUND`. Keçid SSE/alert yaratmır.
- Yaradılanda: `proposal.created` (DISPATCHER, ADMIN) data = `HubProposal`; lentə `AI` alert: `title` `Yeni hub təklifi: <name>`, `body` `Tıxac 35 dəq, 19 marşrut, metroya 236 m, gözləyən 204 nəfər. Etibarlılıq 95%`, `target` `p-map`.
- Qayda (`02_ARCHITECTURE` §6.2, hər 30 san): `JAM` ≥ 20 dəq olan seqmentdən ≤ 300 m-dəki hər seed dayanacağı üçün yer = seqmentin real küçə xəttində dayanacağa ən yaxın nöqtə. `routesCount` — həmin nöqtədən ≤ 50 m keçən real marşrut trayektoriyaları (`routes.geojson`) ≥ 3; `metroM` — ən yaxın real metro çıxışı (`metro_exits`) ≤ 400; `waiting` — ən yaxın hub kamerasının sayı (hub sim) ≥ 40; ən yaxın hub kamerası **> 300 m** (§6.2-də 500 m yazılıb, amma bütün hub-lar metro stansiyasıdır və metroya ≤ 400 m şərti ilə 500 m heç vaxt ödənmir); 300 m-də əvvəlki təklif (istənilən status) yoxdur — eyni yer bir dəfə təklif olunur. `confidence` §6.2 formulası, ən çoxu `95`. `paxPerHour` = stansiyanın `metro_ridership.avgDaily2026` × 6 % (pik saat payı), 100-ə yuvarlaq. `name` = `<seqment adı> – <metro stansiyası> metrosu`. `items`: `Avtobus cibi, 2 avtobusluq, <küçə> sağ tərəf`; 50 m-də velo parkinq yoxdursa `Velo parkinq, 20 yer və skuter zonası`; `Metroya qədər işıqlı piyada keçidi`; `Hub kamerası və canlı gəliş ekranı`. `impact`: `transferMin` = −round(metroM / 50), `toMetroPerDay` = paxPerHour × 3/8 (100-ə), `waitingPct` −40, `costAzn` `180–240 min` (sadə qayda, model deyil).
- Demo nəticəsi (artificial-jam): `Fətəli Xan Xoyski pr. – Gənclik metrosu`, `lat 40.40115, lon 49.85522`, `metroM 236` (Gənclik, Çıxış 5), `routesCount 19`, `paxPerHour 2400`, `confidence 95`.

### Axtarış
**GET** `/admin/search?q=10-BB-482` → `{ "type": "BUS", "ref": "10-BB-482" }` və ya `{ "type": "REPORT", "ref": "A-2042" }` və ya `{ "type": "NONE" }`

Qeydlər (BE-9.4): `A-2041`, `#A-2041`, `a2041` → mövcud şikayətdirsə `REPORT` (`ref` = kanonik kod). Əks halda bort tam uyğunluğu, hərf, tire və boşluq fərqi nəzərə alınmır (`10bb482` → `10-BB-482`) → `BUS` (`ref` = kanonik bort). Qalan hər şey, boş `q` da → `{ "type": "NONE" }` (`ref` yoxdur). Marşrut axtarışı yoxdur.

## 10. Demo idarəsi (DISPATCHER, ADMIN; `yolcu.demo.enabled`)

**POST** `/demo/trigger/{scenario}` → `{ "ok": true, "scenario": "artificial-jam", "message": "..." }`

Spring profili yoxdur: `yolcu.demo.enabled` (default `true`). `false` olanda `/demo/trigger/*` və `/demo/reset` → `404 NOT_FOUND`. PASSENGER → `403 FORBIDDEN`. Naməlum ssenari → `404 NOT_FOUND`. `message` — Azərbaycanca qısa nəticə (toast üçün).

| scenario | Nə edir |
|---|---|
| `bunching` | İki 88-i bir-birinə yaxınlaşdırır, OCC xəbərdarlığı |
| `ganja-arrival` | Biletim: Gəncə reysi 5 dəq sonra, 45 bilet, BILETIM xəbərdarlığı |
| `crowd-28may` | 28 May hub sayını 46 → 70-ə qaldırır |
| `stadium` | EVENT xəbərdarlığı + passenger-lərə bildiriş |
| `rain` | WEATHER xəbərdarlığı |
| `new-report` | Demo sərnişin adından şikayət yaradır |
| `overload-88` | 88 doluluğunu 95%-ə qaldırır |
| `coin-conversion` | Demo sərnişin Rauf Həsənli (`+994 51 ••• 92`) üçün 1000 coinlik (2,00 ₼) bank çevirməsi, `PENDING` → 60 san sonra `COMPLETED` |
| `artificial-jam` | Fətəli Xan Xoyski pr. seqmentini 1 san sonra SLOW, 2,5 san sonra JAM edir, JAM müddətini 35 dəq yazır → hub təklifi qaydası işləyir |

Ssenari detalları (BE-9.5):
- `artificial-jam`: cavab dərhal; `babek` seqmenti (id tarixi səbəbdən belədir; real küçə — Fətəli Xan Xoyski prospekti, OSM ilə yoxlanılıb, bax `04_DATA` §7) 1 san sonra `SLOW` (`traffic.updated` + `EVENT` alert `Fətəli Xan Xoyski pr.: hərəkət ləngiyir`), 2,5 san sonra `JAM`, JAM müddəti 35 dəq (`traffic.updated` + `OCC` alert `Fətəli Xan Xoyski pr.: tıxac, 35 dəqiqədir davam edir`), 3 san sonra hub təklifi qaydası → `proposal.created` + `AI` alert. Seqment reset-ə qədər `JAM` qalır. Hər trigger yeni təklif yaradır (təkrar oynatmaq üçün dublikat yoxlaması atlanır). Reset gözləyən addımları ləğv edir.
- `coin-conversion`: Rauf Həsənli (yoxdursa yaradılır, `users.synthetic = false`, sorğu simulyatorunun hovuzuna düşməsin deyə) əvvəlcə +1000 alır (`reason=DEMO`, `ref=demo-conv:{n}`, ledger uyğun qalsın), sonra adi `POST /me/wallet/conversions` axını: −1000 `CONVERSION`, `PENDING`, `conversion.updated`; 60 san sonra `BankSimJob` → `COMPLETED` + bildiriş + `COIN` alert + `conversion.updated`. Hər trigger yeni sorğu yaradır.
- `new-report`: demo sərnişin (Nigar) adından `88`, `10-BB-482`, `Kobud davranış`, 28 May yaxınlığında şikayət → `report.created` + `REPORT` alert (coin verilmir). Adi limit qüvvədədir: 10 dəqiqədə 4-cü → `429 RATE_LIMIT` (reset limiti sıfırlayır).
- `overload-88`: 88-in simulyasiya doluluğu ~95% (random walk onun ətrafında qalır, reset-ə və ya interval dəyişənə qədər) + `OCC` alert `88: avtobuslar 95% doludur` / `Tövsiyə: interval 12 → 8 dəq`.
- `bunching`: 88-də 10-BB-511 10-BB-482-dən 2 dəq arxaya qoyulur (hər dəfə, əvvəlki "gözləyin" mesajından asılı olmayaraq) → `/admin/occupancy/bunching`-də 88, `gapMin 2`, + hər trigger-də bir `OCC` alert `88: iki avtobus topa yığılıb`.
- `crowd-28may`: 28 May hub sayı ×70/46 (≈ +52%, reset-ə qədər) + `HUB` alert `28 May: N nəfər gözləyir` / `15 dəqiqəyə M-ə çata bilər` (`p-hubs`).
- `stadium`: `EVENT` alert + bütün real (sintetik olmayan) sərnişinlərə bildiriş (`type: INFO`, `target: event:stadium` — mobil naməlum target-i sadəcə açmır).
- `rain`: `WEATHER` alert. `ganja-arrival`: `/admin/biletim/arrivals`-a demo gəlişi əlavə olunur — `{ "time": indi + 5 dəq (dəqiqəyə yuvarlanmış), "from": "Gəncə", "plate": "90-AB-205", "ticketsSold": 45, "status": "Yoldadır, 6 km", "level": "HIGH" }`, siyahıda **birinci** (vaxtı keçənə və ya reset-ə qədər; təkrar trigger əvvəlkini əvəz edir) + `BILETIM` alert `Gəncə reysi HH:mm-də gəlir, 45 bilet satılıb` / `M1 ekspress tövsiyə olunur`, `p-express`. SSE `biletim.*` hadisəsi yoxdur — web `alert.created` (`BILETIM`) gələndə siyahını yenidən çəksin. Avtovağzal hub-unun tövsiyəsi bu gəlişə keçir (`"HH:mm Gəncə reysi üçün M1 ekspressi işə sal"`).

**POST** `/demo/surveys/{id}/simulate-answers?count=50` (DISPATCHER və ya ADMIN; `count` 1–500, default 50; yalnız `ACTIVE` sorğu, əks halda `VALIDATION`) → `{ "created": 50, "survey": SurveyListItem }`
Bu sorğunu hələ cavablamamış sintetik sərnişinlər (`users.synthetic = true`) seçilir, çatmayan qədəri yenisi yaradılır (sonrakı sorğularda təkrar istifadə olunur). Variantlar sualın seed paylanmasına görə çəkili təsadüfi seçilir. Sintetik sərnişinlərə coin verilmir, bildiriş getmir. Batch-dən sonra bir dəfə `survey.updated`.

**POST** `/demo/reset` → simulyatoru və demo datanı başlanğıc vəziyyətə qaytarır (DB-də demo sərnişinin bu günkü şikayət, səfər, coin dəyişiklikləri silinir).
→ `{ "ok": true, "scenario": "reset", "message": "...", "coinBalance": 245, "deleted": { "report_events": 2, "reports": 2, "reviews": 0, "trips": 0, "coin_tx": 2, "coin_conversions": 0, "notifications": 1, "survey_answers": 0, "alerts": 3, "hub_proposals": 1 } }`
Bir tranzaksiyada: demo sərnişinin **bu günkü** (demo saatının günü) `reports` + `report_events`, onların `REPORT` alert-ləri, `reviews` (bu gün yazılan və ya bu günkü səfərə), `trips`, `coin_tx`, `coin_conversions`, `notifications`, `survey_answers` silinir, `coin_balance = 245`; Rauf Həsənlinin (coin-conversion trigger-i) bu günkü `coin_conversions`, `coin_tx`, `notifications` sətirləri də silinir (sayları `deleted`-də eyni açarlara əlavə olunur), balansı = ledger cəmi; gözləyən çevirmə ləğv olunur (bank job sətri tapmır). `COIN` alert-ləri lentdə qalır; bütün `hub_proposals` və onların `AI` alert-ləri silinir; `route_config` interval/avtobus sayı `mocks/route_config.json`-a, `auto_mode = false`. Yaddaşda: tıxac override-ları və JAM müddətləri, hub boost-u, Biletim demo gəlişi, avtobus düzülüşü (88 demo topa yığılması daxil) və doluluq bazası başlanğıc vəziyyətə. Sərnişinə `coin.updated {balance: 245}`. Sxem silinmir, seed yenidən işləmir, digər istifadəçilərin datası və keçmiş günlərin seed sətirləri qalır; trigger-lərin `EVENT/WEATHER/HUB/OCC/BILETIM` alert-ləri lentdə qalır.

## 11. SSE

**GET** `/stream?token=...` — `text/event-stream`. Hadisə formatı:

```
event: report.created
data: {"code":"A-2042","route":"88",...}
```

Qoşulan kimi `event: hello` → `{"userId":1,"role":"DISPATCHER","ts":"..."}`, sonra hər 20 san `event: ping` → `{"ts":"..."}`.

Hadisə siyahısı: `02_ARCHITECTURE.md` → SSE.

`survey.updated` (DISPATCHER, ADMIN) → `SurveyListItem` (bax §9 Sorğular): yeni cavab, simulyasiya batch-i, publish, close.

`conversion.updated` (DISPATCHER, ADMIN) → `ConversionRow` (`/admin/coins/conversions` sətri ilə eyni JSON, bax §9 Yolçu Coin proqramı): yeni çevirmə (`PENDING`) və bank cavabı (`COMPLETED`). Web id üzrə upsert edir (yeni id → siyahının əvvəlinə), `total`/KPI üçün `summary`-ni yenidən çəkə bilər.

`coin.updated` (istifadəçi) → `{ "balance": 45 }`: hər `award`/`spend`-dən sonra (commit-dən sonra).
