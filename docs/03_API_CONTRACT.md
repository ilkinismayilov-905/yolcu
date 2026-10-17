# 03 — API müqaviləsi

**Bu fayl qanundur.** Backend bu formaları qaytarır, frontend bu formaları gözləyir. Dəyişiklik lazımdırsa əvvəl bu faylı yenilə, sonra komandaya de.

Baza URL: `/api/v1`. Bütün cavablar JSON, sahə adları `camelCase`, vaxt ISO-8601 (`2026-10-17T08:12:00+04:00`).

İlk saatda Claude Code bu fayldan `packages/shared/src/api.ts` (TS tipləri) və backend-də `dto` record-larını yaradır.

## Ümumi formalar

```jsonc
// Səhifələnmiş siyahı
{ "items": [], "page": 0, "size": 20, "total": 46 }

// Xəta (HTTP 4xx/5xx)
{ "error": { "code": "COINS_INSUFFICIENT", "message": "Coin kifayət etmir: 40 var, 60 lazımdır" } }
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
{ "token": "eyJ...", "user": { "id": 1, "fullName": "Leyla Məmmədova", "role": "DISPATCHER", "coinBalance": 0 } }
```

**GET** `/me` → `User`

## 2. Geo və yerlər (açıq)

**GET** `/geo/layers/{name}` — `name`: `terminals | routes | bike-parkings | bike-lanes | metro-exits` → GeoJSON `FeatureCollection`

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
`paidWith`: `BAKIKART | CARD | COIN`. `COIN` → 60 coin çıxılır, azdırsa `COINS_INSUFFICIENT`.

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
→ `ReportDetail` + `coinsAwarded`. Dashboard-a `report.created` SSE.

**GET** `/me/reports?page=0&size=20` → `{ items: ReportListItem[], page, size, total }` · **GET** `/me/reports/{code}` → `ReportDetail`

```jsonc
// ReportListItem
{ "code": "A-2042", "createdAt": "...", "route": "88", "bort": "10-BB-482", "operator": "BakuBus MMC",
  "category": "Dayanacaqda saxlamadı", "textPreview": "Elmlər dayanacağında...", "status": "NEW", "late": false, "place": "Elmlər" }
// ReportDetail = ReportListItem + 
{ "text": "...", "hasPhoto": true, "coinsGiven": 5, "answerText": null,
  "events": [{ "type": "CREATED", "note": null, "actor": "Sərnişin", "createdAt": "..." }] }
```

Kateqoriyalar (sabit siyahı, `packages/shared`): `Dayanacaqda saxlamadı`, `Kobud davranış`, `Təhlükəli sürüş`, `Telefonla danışırdı`, `Kondisioner işləmir`, `Çirkli salon`, `Çox uzun gözləmə`, `Əlçatanlıq problemi`, `Ödəniş problemi`, `Doluluq`.

## 7. Mobil: sorğu, bildiriş, cüzdan

**GET** `/me/surveys/{id}` → `{ "id": 1, "title": "...", "coinReward": 10, "answered": false, "questions": [{ "id": 3, "text": "...", "options": ["Hər gün","Xeyr"] }] }`
**POST** `/me/surveys/{id}/answers` — `{ "answers": { "3": "Hər gün", "4": "8 dəqiqə" } }` → `{ "coinsAwarded": 10, "coinBalance": 200 }`

**GET** `/me/notifications` → `[{ "id": 88, "type": "SURVEY", "title": "...", "body": "...", "target": "survey:1", "read": false, "createdAt": "..." }]`
**POST** `/me/notifications/{id}/read` · **POST** `/me/notifications/read-all`

`target` formatları: `survey:{id}` · `report:{code}` · `review:{tripId}` · `wallet`

**GET** `/me/wallet`

```json
{ "balance": 245, "aznEquivalent": "2.45", "rate": 100,
  "history": [{ "label": "Sorğu: interval rahatlığı", "amount": 10, "createdAt": "..." }],
  "rules": [{ "label": "Səfərdən sonra rəy", "amount": "+5" }] }
```

**POST** `/me/wallet/conversions` — `{ "coins": 200, "cardMask": "•• 0193" }` → `{ "id": 7, "coins": 200, "azn": "2.00", "status": "PENDING" }`
Min 100, 100-ə bölünməlidir. 60 san sonra `COMPLETED` + bildiriş.

## 8. Canlı data (SIM)

**GET** `/live/buses?route=88` → `[{ "bort": "10-BB-482", "route": "88", "type": "ELECTRIC", "lat": .., "lon": .., "bearing": 160, "occupancy": 84, "capacity": 90 }]`
**GET** `/live/traffic` → `[{ "id": "yusif-seferov", "name": "Yusif Səfərov küç.", "level": "JAM", "speedKmh": 9, "line": [[lat,lon],...] }]` (`level`: `FREE | SLOW | JAM`)
**GET** `/live/hubs` → `[{ "id": "may28", "name": "28 May", "lat": .., "lon": .., "count": 340, "capacity": 400, "trend10": 18, "forecast15": 420 }]`

## 9. Admin (DISPATCHER, ADMIN)

### Dashboard
**GET** `/admin/kpis` → `{ "avgSpeedKmh": 18, "avgSpeedYesterday": 22, "waitingAtHubs": 12480, "avgOccupancy": 74, "overfullBuses": 63, "satisfaction": 4.2, "satisfactionWeekDelta": 0.2 }`
**GET** `/admin/feed?limit=50` → `Alert[]` · `{ "id": 1, "category": "OCC", "title": "...", "body": "...", "target": "p-occ", "createdAt": "..." }`

### Hub kameraları
**GET** `/admin/hubs` → `/live/hubs` + `{ "level": "HIGH", "avgWaitMin": 11, "camera": "K-201", "recommendation": "65 intervalı 12 → 9 dəq" }`
**POST** `/admin/hubs/{id}/apply` → `{ "ok": true }` (alert yazır)

### Doluluq və interval
**GET** `/admin/occupancy/routes`

```json
[{ "route": "88", "type": "ELECTRIC", "operator": "BakuBus MMC", "busCount": 6, "avgOccupancy": 92,
   "intervalMin": 12, "recommendedMin": 8, "satNow": 3.8, "satAfter": 4.5, "autoMode": false }]
```
**GET** `/admin/occupancy/routes/{route}/buses` → `[{ "bort": "10-BB-482", "type": "ELECTRIC", "near": "28 May", "occupancy": 84, "capacity": 90 }]`
**POST** `/admin/occupancy/routes/{route}/interval` — `{ "intervalMin": 8 }` → yenilənmiş `RouteOccupancy` + `route.updated` SSE
**GET** `/admin/occupancy/simulate?route=88&intervalMin=10` → `{ "occupancy": 77, "waitMin": 5, "busesNeeded": 8, "sat": 4.2, "note": "2 əlavə avtobus lazımdır..." }`
**PUT** `/admin/occupancy/auto` — `{ "enabled": true }`
**GET** `/admin/occupancy/bunching` → `[{ "route": "88", "front": { "bort": "10-BB-482", "occupancy": 84 }, "back": { "bort": "10-BB-511", "occupancy": 41 }, "gapMin": 2, "near": "28 May" }]`
**POST** `/admin/occupancy/bunching/{route}/message` → `{ "ok": true }`

### Avtovağzal və Biletim.az (SIM)
**GET** `/admin/biletim/arrivals` → `[{ "time": "08:20", "from": "Gəncə", "plate": "90-AB-114", "ticketsSold": 41, "status": "Yoldadır, 18 km", "nextCityBusMin": 18, "level": "HIGH" }]`

### Şikayətlər
**GET** `/admin/reports?status=NEW&category=&operator=bb&q=10-BB&page=0&size=10` → `Page<ReportListItem>`
`status` filtri: `NEW | REVIEW | FORWARDED | CLOSED` (`CLOSED` = `ANSWERED` + `RESOLVED`)
**GET** `/admin/reports/stats` → `{ "today": 214, "open": 23, "forwarded": 6, "late": 2, "newCount": 15 }`
**GET** `/admin/reports/{code}` → `ReportDetail` (+ `"passenger": "Gizli"`)
**PATCH** `/admin/reports/{code}/status` — `{ "status": "REVIEW", "note": "" }` → `ReportDetail`
**POST** `/admin/reports/{code}/forward` → `ReportDetail` (status `FORWARDED`, sərnişinə bildiriş)
**POST** `/admin/reports/{code}/resolve` — `{ "answerText": "Sürücü ilə izahat işi aparıldı" }` → `ReportDetail` (+15 coin sərnişinə)
**POST** `/admin/reports/{code}/share` — `{ "targets": ["OPERATOR","YPX"], "hidePersonal": true }` → `{ "ok": true }` · `targets`: `OPERATOR`, `YPX` (Dövlət Yol Polisi), `CITY` (Bakı Şəhər İcra Hakimiyyəti)

Status keçidləri: `NEW → REVIEW → FORWARDED → ANSWERED/RESOLVED`. `NEW`-dən birbaşa `FORWARDED` də olar. Səhv keçid → `VALIDATION`.
`late` = `FORWARDED` və `forwardedAt` 24 saatdan köhnədir (seed-də bir neçəsi belədir).

### Sorğular
**GET** `/admin/surveys?status=ACTIVE` → `[{ "id": 1, "title": "...", "status": "ACTIVE", "audience": "ALL", "audienceLabel": "Bütün istifadəçilər", "responses": 3120, "coinReward": 10, "createdAt": "..." }]`
**GET** `/admin/surveys/{id}/results` → `{ "id": 1, "responses": 3120, "questions": [{ "text": "...", "options": [{ "label": "Hər gün", "count": 686, "percent": 22 }] }] }`
**POST** `/admin/surveys` — `{ "title": "...", "audience": "ALL", "coinReward": 10, "questions": [{ "text": "...", "options": ["A","B"] }], "publish": true }`
**POST** `/admin/surveys/{id}/publish` · **POST** `/admin/surveys/{id}/close`
Publish → auditoriyadakı hər sərnişinə `notification` + `notification.created` SSE.

### Coin proqramı
**GET** `/admin/coins/summary` → `{ "earnedToday": 48210, "earnedTodayUsers": 9120, "inCirculation": 3100000, "convertedThisMonthAzn": "12400.00", "rate": 100 }`
**GET** `/admin/coins/conversions?page=0&size=10` → `Page<{ id, userName, phoneMask, coins, azn, cardMask, status, createdAt }>`

### Operatorlar və avtopark
**GET** `/admin/operators` → `[{ "id": 1, "code": "bb", "name": "BakuBus MMC", "fleetTotal": 640, "onLine": 584, "avgRating": 4.6, "complaintsPer10k": 3.1, "punctuality": 91, "routes": ["1","2","4","88"] }]`
**GET** `/admin/buses?operator=xf&status=LINE&type=ELECTRIC&q=99-XF&page=0&size=12` → `Page<{ bort, operator, route, type, model, status, occupancy, driverRating }>`

### Data analitika
**GET** `/admin/analytics/kpis` → `{ "trackedTrips30d": 1240000, "consentUsers": 31200, "avgTripMin": 27, "longestTransferWaitMin": 14, "suggestionAcceptance": 41 }`
**GET** `/admin/analytics/od?from=07:00&to=10:00` → `{ "zones": ["Avtovağzal","Koroğlu","28 May","Nizami","Gənclik","Nərimanov"], "matrix": [[0,8200,14600,6100,5300,4400]] }`
**GET** `/admin/analytics/alighting?limit=5` → `[{ "stop": "Nərimanov metro", "perDay": 18420, "peak": "08:35", "note": "..." }]`
Hub matrisi, proqnoz, ödənişli yol və data məhsulları FAKE-dir: `mocks/analytics_static.json`.

### Hub təklifləri
**GET** `/admin/hub-proposals?status=NEW` → `[{ "id": 3, "name": "Babək pr. – Gənclik metrosu", "lat": 40.3990, "lon": 49.8560, "jamMinutes": 35, "routesCount": 3, "metroM": 300, "waiting": 58, "paxPerHour": 2400, "confidence": 82, "items": ["Avtobus cibi, 2 avtobusluq"], "impact": { "transferMin": -6, "toMetroPerDay": 900, "waitingPct": -40, "costAzn": "180–240 min" }, "status": "NEW" }]`
**POST** `/admin/hub-proposals/{id}/accept` · **POST** `/admin/hub-proposals/{id}/reject`

### Axtarış
**GET** `/admin/search?q=10-BB-482` → `{ "type": "BUS", "ref": "10-BB-482" }` və ya `{ "type": "REPORT", "ref": "A-2042" }` və ya `{ "type": "NONE" }`

## 10. Demo idarəsi (ADMIN, yalnız `demo` profilində)

**POST** `/demo/trigger/{scenario}` → `{ "ok": true }`

| scenario | Nə edir |
|---|---|
| `bunching` | İki 88-i bir-birinə yaxınlaşdırır, OCC xəbərdarlığı |
| `ganja-arrival` | Biletim: Gəncə reysi 5 dəq sonra, 45 bilet, BILETIM xəbərdarlığı |
| `crowd-28may` | 28 May hub sayını 46 → 70-ə qaldırır |
| `stadium` | EVENT xəbərdarlığı + passenger-lərə bildiriş |
| `rain` | WEATHER xəbərdarlığı |
| `new-report` | Demo sərnişin adından şikayət yaradır |
| `overload-88` | 88 doluluğunu 95%-ə qaldırır |
| `artificial-jam` | Babək pr. seqmentini 1 san sonra SLOW, 2,5 san sonra JAM edir, JAM müddətini 35 dəq yazır → hub təklifi qaydası işləyir |

**POST** `/demo/reset` → simulyatoru və demo datanı başlanğıc vəziyyətə qaytarır (DB-də demo sərnişinin bu günkü şikayət, səfər, coin dəyişiklikləri silinir).

## 11. SSE

**GET** `/stream?token=...` — `text/event-stream`. Hadisə formatı:

```
event: report.created
data: {"code":"A-2042","route":"88",...}
```

Qoşulan kimi `event: hello` → `{"userId":1,"role":"DISPATCHER","ts":"..."}`, sonra hər 20 san `event: ping` → `{"ts":"..."}`.

Hadisə siyahısı: `02_ARCHITECTURE.md` → SSE.
