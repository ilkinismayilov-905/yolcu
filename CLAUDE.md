# CLAUDE.md — HALQA (Claude Code üçün əsas təlimat)

Bu faylı hər sessiyanın əvvəlində oxu. Sonra yalnız tapşırığa aid sənədi aç (aşağıdakı xəritə).

## 1. Layihə bir abzasda

HALQA — AYNA üçün vahid nəqliyyat platformasıdır. **Web dashboard** (AYNA dispetçeri): canlı xəritə, hub kameraları, avtobus doluluğu və AI interval tövsiyəsi, avtobus bölgüsü, Avtovağzal + Biletim.az, data analitika (OD matrisi, proqnoz, ödənişli yollar, datanın tətbiqi), süni tıxac → yeni hub təklifi, şikayətlər, sorğular, coin proqramı, operatorlar və avtopark. **Mobil tətbiq** (sərnişin): sadə xəritə (velo və skuter qatı ilə), "Sizin üçün" proaktiv təklif, marşrut variantları, dayanacağa piyada naviqasiya, ödəniş, səfər və düşmənin avtomatik aşkarlanması, rəy, şikayət, sorğu, coin cüzdanı, səfərlərim. Hər ikisi **bir Spring Boot backend**-ə qoşulur və canlı hadisələri **SSE** ilə alır.

**Vaxt: 1 gün.** Məqsəd: tez, sabit, demo-da heç vaxt sınmayan məhsul. Mükəmməl arxitektura yox, işləyən axın.

## 2. Sənəd xəritəsi

| Fayl | Nə vaxt oxu |
|---|---|
| `docs/00_PRODUCT.md` | Hər zaman ilk dəfə: məhsul, rollar, əsas axınlar |
| `docs/01_SCOPE_REAL_SIM_FAKE.md` | **Hər tapşırıqdan əvvəl**: bu hissə real backend-dirmi, simulyasiyadır, yoxsa frontend fake? |
| `docs/02_ARCHITECTURE.md` | Backend strukturu, DB sxemi, SSE, təhlükəsizlik |
| `docs/03_API_CONTRACT.md` | **Bütün endpoint-lər və JSON formaları. Frontend və backend bu fayla görə yazılır** |
| `docs/04_DATA.md` | Data importu, dayanacaq seed-i, simulyator |
| `docs/05_BACKEND_ROADMAP.md` | Java işləri |
| `docs/06_WEB_ROADMAP.md` | Next.js işləri |
| `docs/07_MOBILE_ROADMAP.md` | React Native işləri |
| `docs/08_DESIGN_SYSTEM.md` | Rənglər, şrift, komponentlər, mətn qaydaları |
| `docs/09_24H_PLAN_AND_DEMO.md` | Saat-saat plan, komanda bölgüsü, demo ssenarisi, ehtiyat planlar |
| `design/web_dashboard.html` | **Web üçün vizual həqiqət mənbəyi.** Brauzerdə aç, ekranı təkrarla |
| `design/mobile_app.html` | **Mobil üçün vizual həqiqət mənbəyi** |
| `mocks/*.json` | Fake hissələr və mock rejim üçün hazır data |

## 3. Stack (dəyişmə)

| Qat | Texnologiya |
|---|---|
| Backend | Java 21, Spring Boot 3.x (Web, Data JPA, Validation, Security), Flyway, PostgreSQL 16 (Docker), springdoc-openapi, jjwt |
| Real vaxt | Server-Sent Events (`SseEmitter`), tək endpoint `/api/v1/stream` |
| Web | Next.js (App Router) + TypeScript, Tailwind CSS, TanStack Query, react-map-gl + MapLibre GL, Recharts |
| Mobil | Expo (React Native) + TypeScript, Expo Router, react-native-maps, expo-location, TanStack Query, react-native-sse, Zustand |
| Ortaq | `packages/shared` — TS tipləri (`api.ts`), formula funksiyaları (`headway.ts`), sabitlər |

## 4. Repo strukturu

```
halqa/
├── CLAUDE.md
├── docs/  design/  mocks/
├── backend/                 # Spring Boot (Gradle, Kotlin DSL)
│   └── src/main/java/az/halqa/...
├── web/                     # Next.js
├── mobile/                  # Expo
├── packages/shared/         # TS tipləri və formulalar (web + mobile)
├── data/
│   ├── raw/                 # verilən .geojson/.kmz/.csv
│   └── processed/           # GeoJSON-a çevrilmiş fayllar (git-ə daxil)
└── docker-compose.yml       # postgres
```

## 5. Üç etiket: REAL, SIM, FAKE

Hər funksiya `docs/01_SCOPE_REAL_SIM_FAKE.md`-də bu etiketlərdən birini daşıyır. Ona uyğun işlə:

- **REAL** — Java endpoint + Postgres + validasiya. Frontend həqiqi API çağırır.
- **SIM** — Java-da `@Scheduled` simulyator datanı yaradır, amma API və SSE real işləyir. Gələcəkdə yalnız simulyator real mənbə ilə əvəz olunur.
- **FAKE** — backend yoxdur. Frontend `mocks/*.json` və ya `packages/shared` formulası ilə işləyir, amma UI tam interaktivdir (düymələr, filtrlər, state).

**Qayda:** FAKE kimi işarələnmiş şeyə backend yazma. REAL kimi işarələnmişi fake etmə. Şübhə olanda soruş.

## 6. Kod qaydaları

- Kod, identifikatorlar, commit-lər: **ingiliscə**. UI mətnləri: **Azərbaycanca** (`az` lokal faylında).
- Backend: hər domen öz paketində (`report`, `survey`, `coin`...). Controller → Service → Repository. DTO-lar Java `record`. Entity-ni heç vaxt birbaşa qaytarma.
- Xəta formatı hamı üçün eyni: `{ "error": { "code": "COINS_INSUFFICIENT", "message": "..." } }` (bax `03_API_CONTRACT.md`).
- Bütün siyahı endpoint-ləri: `?page=0&size=20` + cavabda `{ items, page, size, total }`.
- Pul: coin `int`, manat `BigDecimal` (2 rəqəm). Coin yalnız serverdə dəyişir.
- Koordinat: `lat`, `lon` (WGS84). Məsafə: haversine (`GeoUtil.distanceMeters`).
- Frontend: tiplər yalnız `packages/shared/src/api.ts`-dən. `any` yoxdur.
- Hər frontend-də `API_MODE=mock|live` (env). `mock` rejimində `mocks/*.json` işləyir — backend hazır olmayanda iş dayanmır.
- Rənglər yalnız design token-lərdən (`08_DESIGN_SYSTEM.md`). Hardcode hex yoxdur.
- Simulyasiya datası göstərilən hər ekranın küncündə kiçik "Demo data" nişanı.

## 7. Əmrlər

```bash
docker compose up -d db                       # Postgres :5432
cd backend && ./gradlew bootRun               # API :8080, Swagger: /swagger-ui.html
cd web && pnpm dev                            # :3000
cd mobile && npx expo start                   # telefonda Expo Go ilə
```

Mobil telefon backend-ə laptopun LAN IP-si ilə qoşulur: `EXPO_PUBLIC_API_URL=http://192.168.x.x:8080`.

## 8. İş qaydası (Claude Code)

1. Tapşırığı bənd nömrəsi ilə al (məs. `BE-3.2`, `WEB-4.1`, `MOB-2.3`).
2. Roadmap-də həmin bəndin **Qəbul meyarını** oxu, `01_SCOPE`-da etiketini yoxla.
3. Kiçik plan yaz (3–6 addım), sonra kodu yaz.
4. Bitəndə: qəbul meyarını özün yoxla (curl / brauzer), roadmap-də `[x]` et.
5. Böyük refaktor etmə. Vaxt 1 gündür.
