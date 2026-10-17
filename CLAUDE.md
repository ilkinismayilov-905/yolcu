> **Ad:** məhsul Yolcu, Java paketi `az.yolcu`, DB `yolcu`, property prefiksi `yolcu.*`,
> demo hesablar `dispatcher@yolcu.az` / `admin@yolcu.az` (şifrə `demo123`).

# CLAUDE.md — Yolcu (Claude Code üçün əsas təlimat)

Bu faylı hər sessiyanın əvvəlində oxu. Sonra yalnız tapşırığa aid sənədi aç (aşağıdakı xəritə).

## 1. Layihə bir abzasda

Yolcu — AYNA üçün vahid nəqliyyat platformasıdır. **Web dashboard** (AYNA dispetçeri): canlı xəritə, hub kameraları, avtobus doluluğu və AI interval tövsiyəsi, avtobus bölgüsü, Avtovağzal + Biletim.az, data analitika (OD matrisi, proqnoz, ödənişli yollar, datanın tətbiqi), süni tıxac → yeni hub təklifi, şikayətlər, sorğular, coin proqramı, operatorlar və avtopark. **Mobil tətbiq** (sərnişin): sadə xəritə (velo və skuter qatı ilə), "Sizin üçün" proaktiv təklif, marşrut variantları, dayanacağa piyada naviqasiya, ödəniş, səfər və düşmənin avtomatik aşkarlanması, rəy, şikayət, sorğu, coin cüzdanı, səfərlərim. Hər ikisi **bir Spring Boot backend**-ə qoşulur və canlı hadisələri **SSE** ilə alır.

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
| `docs/10_WEB_FULLSTACK_SLICES.md` | **Web işi: backend və frontend birlikdə, dilim-dilim (S0–S11)** |
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
Yolcu/
├── CLAUDE.md
├── docs/  design/  mocks/
├── backend/                 # Spring Boot (Maven, mvnw)
│   └── src/main/java/az/yolcu/...
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

| Əmr (repo kökündə) | Nə edir | Ünvan |
|---|---|---|
| `./run.sh` | **Demo / hamısı bir prosesdə:** Postgres + web statik export (`-Pweb`) + Spring Boot | **:8080** — dashboard, `/api/v1`, `/swagger-ui.html` |
| `pnpm dev` | **İnkişaf, avtomatik yenilənir:** Postgres + `api` (spring-boot:run + devtools) + `watch` (nodemon: `backend/src` dəyişəndə `mvn -q -o compile` → devtools backend-i yenidən başladır) + `web` (Next.js dev, hot reload) | web :3000, API :8080 |
| `cd backend && mvn spring-boot:run` | Yalnız backend | :8080 |
| `cd backend && mvn -B -ntp test` | Backend testləri (pnpm işə düşmür) | — |
| `pnpm build` / `pnpm test` | Web build / `packages/shared` testləri | — |
| `cd mobile && npx expo start` | Mobil, telefonda Expo Go ilə | — |

**Qayda (Claude Code və agentlər):** İnkişaf mühiti istifadəçinin terminalında 'pnpm dev' ilə daim işləyir (web 3000, API 8080). Serverləri başlatma, dayandırma, 3000/8080/5432/5433 portlarındakı prosesləri kill etmə. Kod dəyişəndə heç nəyi yenidən başlatma: web hot reload, backend watcher + devtools. Yoxlama üçün curl localhost:8080 və brauzerdə localhost:3000 istifadə et. pom.xml və ya package.json dəyişibsə, istifadəçiyə 'pnpm dev-i yenidən başlat' de.

Lokal Postgres 5432-ni tutursa: `DB_PORT=5433 ./run.sh`, `DB_PORT=5433 pnpm dev` (və ya `DB_PORT=5433 docker compose up -d db` + `DB_PORT=5433 mvn spring-boot:run`).

pnpm qlobal quraşdırılmayıbsa `npx -y pnpm@9.15.9 ...` işlət (9.15.0 Node 26-da "Done"-dan sonra çıxmır). pnpm əmrlərini `CI=true` ilə işlət ki, sual verməsin.

Mobil telefon backend-ə laptopun LAN IP-si ilə qoşulur: `EXPO_PUBLIC_API_URL=http://192.168.x.x:8080`.

## 8. Web: backend və frontend birlikdə

Web üçün iş **dilim-dilim** gedir (`docs/10_WEB_FULLSTACK_SLICES.md`). Hər dilim həm Java endpoint-ini, həm də Next.js səhifəsini əhatə edir və yalnız ikisi `API_MODE=live` rejimində birlikdə işləyəndə bitmiş sayılır. Yalnız backend və ya yalnız frontend yazıb dilimi bitmiş hesab etmə.

Köməkçi agentlər (`.claude/agents/`): `backend-dev` (yalnız `backend/`), `web-dev` (yalnız `web/` və `packages/shared/`). Bir dilimdə ikisini paralel işlət: web-dev əvvəlcə mock rejimdə qurur, backend-dev endpoint-i yazır, sonra live rejimdə birlikdə yoxla.

## 9. İş qaydası (Claude Code)

1. Tapşırığı bənd nömrəsi ilə al (məs. `BE-3.2`, `WEB-4.1`, `MOB-2.3`).
2. Roadmap-də həmin bəndin **Qəbul meyarını** oxu, `01_SCOPE`-da etiketini yoxla.
3. Kiçik plan yaz (3–6 addım), sonra kodu yaz.
4. Bitəndə: qəbul meyarını özün yoxla (curl / brauzer), roadmap-də `[x]` et.
5. Böyük refaktor etmə. Vaxt 1 gündür.
