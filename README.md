<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="web/public/brand/logo-dark.svg">
  <img src="web/public/brand/logo-light.svg" alt="Yolçu" width="120">
</picture>

# Yolçu

**AYNA üçün vahid nəqliyyat platforması** — Bakının avtobus şəbəkəsini real vaxtda görən, AI ilə interval və yeni hub tövsiyə edən, sərnişin səsini (şikayət, sorğu, rəy) qərara çevirən dispetçer paneli.

![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![Next.js 16](https://img.shields.io/badge/Next.js-16-000000?logo=nextdotjs&logoColor=white)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![MapLibre](https://img.shields.io/badge/MapLibre-OpenStreetMap-396CB2?logo=maplibre&logoColor=white)
![Tests](https://img.shields.io/badge/backend%20tests-129%20passing-2ea44f)

[**Demo-nu işə sal**](#tez-başlanğıc) · [**Render-ə deploy**](docs/11_DEPLOY.md) · [**Demo ssenarisi**](docs/09_24H_PLAN_AND_DEMO.md) · [**API**](docs/03_API_CONTRACT.md)

<img src="docs/screenshots/map.png" alt="Canlı xəritə: real OSM xəritəsi, avtobuslar, hub-lar, tıxac və hadisələr lenti" width="100%">

</div>

---

## Problem və həll

Bakıda sərnişin keçid nöqtələrində (metro ↔ avtobus, avtovağzal ↔ şəhər) itir: avtobuslar dolu gedir, intervallar köhnə cədvəllə qalır, şikayətlər cavabsız qalır və dispetçer bunların heç birini bir ekranda görmür.

**Yolçu** bütün siqnalları bir paneldə birləşdirir və hər birini konkret hərəkətə çevirir:

| Siqnal | Yolçu nə edir |
|---|---|
| Avtobus doluluğu 90%+ | AI intervalı hesablayır (12 → 8 dəq), bir kliklə tətbiq olunur, doluluq canlı düşür |
| Uzun tıxac + yaxında metro | Yeni **hub təklifi**: səbəb, 4 göstərici, nə qurulmalı, gözlənilən təsir |
| Sərnişin şikayəti | Cədvələ səhifə yenilənmədən düşür → operatora göndərilir → həll edilir; sərnişin hər addımda bildiriş alır |
| Sorğu cavabları | Canlı faizlərə çevrilir; cavab verən **Yolçu Coin** qazanır |
| Avtovağzala gələn reyslər (Biletim.az) | Satılmış biletlərə görə ekspress xətt tövsiyəsi |

## ✨ İmkanlar

**Canlı əməliyyat**
- 🗺️ **Canlı xəritə** — real OpenStreetMap (OpenFreeMap), Azərbaycan dilində küçə adları; avtobuslar real marşrut trayektoriyaları üzrə hərəkət edir, hub-lar real metro çıxışlarının koordinatlarındadır
- 📷 **Hub kameraları** — gözləyən insan sayı (kamera sayır, tanımır), 15 dəqiqəlik proqnoz, qayda əsaslı tövsiyə
- 🚌 **Avtobus doluluğu** — AI interval tövsiyəsi, "Avtomatik tənzimləmə" rejimi, topa yığılma (iki avtobus arası < 3 dəq) aşkarlanması və sürücüyə mesaj
- 🔔 **Hadisələr lenti** — bütün hadisələr Server-Sent Events ilə real vaxtda

**Sərnişin səsi**
- 📝 **Şikayətlər** — filtr, axtarış, CSV ixrac, drawer-də tarixçə, operatora göndərmə və paylaşma
- 📊 **Sorğular** — auditoriyaya bildiriş, canlı nəticələr, bildiriş önizləməsi
- 🪙 **Yolçu Coin** — səfər rəyi və sorğu üçün qazanılır; 500 Yolçu Coin = 1 ₼, partnyor bank vasitəsilə manata çevrilir

**Planlaşdırma və analitika**
- 📈 **Data analitika** — OD matrisi və düşmə nöqtələri (real səfər datasından), tələbat proqnozu, ödənişli yol ssenarisi, datanın tətbiqi xəritəsi
- 🏢 **Operatorlar və avtopark** — 4 operator, 66 avtobus, rəy və şikayət göstəriciləri
- 🚏 **Avtovağzal və Biletim.az** — gələn reyslər və ekspress xətt tövsiyələri

**Demo üçün**
- 🎛️ **`/demo` paneli** — 9 ssenari (süni tıxac, yeni şikayət, 88 dolub, topa yığılma, yağış, stadion…) və bir kliklə **Hamısını sıfırla**

## 🖼️ Ekran görüntüləri

| | |
|---|---|
| <img src="docs/screenshots/hub-proposal.png" alt="Süni tıxacdan sonra yeni hub təklifi"> <br> **Hub təklifi** — tıxac + metro yaxınlığı → nə qurulmalı və təsiri | <img src="docs/screenshots/occupancy.png" alt="Avtobus doluluğu və AI interval tövsiyəsi"> <br> **Doluluq** — AI interval tövsiyəsi, salon miniatürü, topa yığılma |
| <img src="docs/screenshots/reports.png" alt="Şikayətlər və drawer"> <br> **Şikayətlər** — real vaxtda lent, tarixçə, operatora göndərmə | <img src="docs/screenshots/analytics-od.png" alt="OD matrisi və düşmə nöqtələri"> <br> **Analitika** — OD matrisi, düşmə nöqtələri, proqnoz |
| <img src="docs/screenshots/hubs.png" alt="Hub kameraları"> <br> **Hub kameraları** — sıxlıq, proqnoz, tövsiyə | <img src="docs/screenshots/map-dark.png" alt="Tünd rejim"> <br> **Tünd rejim** — bütün səhifələrdə |

### 📱 Telefonda

Dashboard responsivdir: telefon (360–430 px), planşet (768 px) və laptopda işləyir. Qaydalar: [`08_DESIGN_SYSTEM` §9](docs/08_DESIGN_SYSTEM.md#9-responsiv-qaydalar).

| <img src="docs/screenshots/mobile-map.png" alt="Telefonda canlı xəritə" width="200"> | <img src="docs/screenshots/mobile-menu.png" alt="Telefonda ☰ menyu" width="200"> | <img src="docs/screenshots/mobile-occupancy.png" alt="Telefonda doluluq: cədvəl kart görünüşündə" width="200"> | <img src="docs/screenshots/mobile-report.png" alt="Telefonda şikayət drawer-i tam ekran" width="200"> | <img src="docs/screenshots/mobile-map-dark.png" alt="Telefonda tünd rejim" width="200"> |
|:---:|:---:|:---:|:---:|:---:|
| **Xəritə** — 60% hündürlük, legend yığılır | **☰ Menyu** — soldan, Esc / kənara klik bağlayır | **Doluluq** — cədvəl sətirləri kart olur | **Şikayət** — drawer tam ekran, düymələr aşağıda | **Tünd rejim** |

## 🎯 Nə real, nə simulyasiyadır

Münsiflərə dürüst: hər funksiya üç etiketdən birini daşıyır ([tam siyahı](docs/01_SCOPE_REAL_SIM_FAKE.md)).

| Etiket | Mənası | Nümunələr |
|---|---|---|
| **REAL** | Java endpoint + PostgreSQL + validasiya | Şikayətlər, sorğular, Yolçu Coin, interval tətbiqi, hub təklifi qaydası, operatorlar, OD matrisi, xəritə və bütün koordinatlar |
| **SIM** | Server simulyatoru datanı yaradır, API və SSE real işləyir — real mənbə qoşulanda yalnız simulyator dəyişir | Avtobus mövqeləri və doluluğu, hub sayları, tıxac səviyyələri, Biletim.az reysləri |
| **FAKE** | Backend yoxdur, UI tam interaktivdir | Avtobus bölgüsü, inteqrasiyalar, analitikada proqnoz və ödənişli yol ssenarisi |

Simulyasiya datası olan hər ekranda **Demo data** nişanı var.

## 🏗️ Arxitektura

```mermaid
flowchart LR
    subgraph Browser["Brauzer"]
        UI["Next.js dashboard<br/>(statik export)"]
    end
    subgraph App["Spring Boot · bir proses, bir port"]
        API["REST API /api/v1"]
        SSE["SSE /api/v1/stream"]
        SIM["Simulyator<br/>(@Scheduled)"]
        GEO["GeoStore<br/>(real GeoJSON)"]
        STATIC["Statik web<br/>(classpath:/static)"]
    end
    DB[("PostgreSQL 16<br/>Flyway")]
    OSM["OpenFreeMap<br/>(OSM tile-ları)"]

    UI -- "fetch" --> API
    UI -- "EventSource" --> SSE
    UI -- "xəritə" --> OSM
    STATIC -. "HTML/JS" .-> UI
    API --> DB
    SIM -- "bus.positions, hub.counts…" --> SSE
    SIM --> DB
    GEO --> API
```

- **Bir monolit, bir baza, bir SSE kanalı.** Web dashboard statik export olunur və Spring Boot onu API ilə eyni portda verir — CORS yoxdur, deploy bir image-dir.
- **Müqavilə əsaslı.** Bütün endpoint-lər və JSON formaları [`docs/03_API_CONTRACT.md`](docs/03_API_CONTRACT.md)-dədir; TS tipləri [`packages/shared/src/api.ts`](packages/shared/src/api.ts)-də, frontend `mock` rejimdə backend-siz də işləyir.
- **Formulalar bir yerdə.** İnterval mühərriki Java-da (`IntervalEngine`) və TS-də (`headway.ts`) eyni test vektorları ilə yoxlanılır — slayder göstəriciləri şəbəkəsiz, dərhal hesablanır.

Ətraflı: [`docs/02_ARCHITECTURE.md`](docs/02_ARCHITECTURE.md).

## 🧰 Texnologiyalar

| Qat | Texnologiya |
|---|---|
| Backend | Java 21, Spring Boot 3.5 (Web, Data JPA, Validation, Security), Flyway, PostgreSQL 16, JWT (jjwt), springdoc-openapi |
| Real vaxt | Server-Sent Events (`SseEmitter`), bir endpoint |
| Web | Next.js 16 (App Router, statik export), React 19, TypeScript 5.9, Tailwind CSS 4, TanStack Query 5 |
| Xəritə | MapLibre GL 6 + react-map-gl 8, OpenFreeMap (OpenStreetMap datası) |
| Ortaq | `packages/shared` — API tipləri, `headway.ts`, `toll.ts` (pnpm workspace) |
| Deploy | Docker (multi-stage), Render Blueprint |

## Tez başlanğıc

**Tələblər:** Docker, Java 21, Maven, Node.js 20+ (pnpm qlobal lazım deyil — `npx pnpm@9.15.9` avtomatik işləyir).

```bash
git clone https://github.com/ilkinismayilov-905/yolcu.git
cd yolcu
./run.sh                 # Postgres (Docker) + web build + API → http://localhost:8080
DB_PORT=5433 ./run.sh    # lokal Postgres 5432-ni tutursa
```

İlk işə salma cache-lər doludursa ~20 saniyə, təmiz maşında ~5 dəqiqə çəkir (asılılıqlar yüklənir).

| URL | Nə |
|---|---|
| http://localhost:8080 | Dashboard |
| http://localhost:8080/demo/ | Demo paneli: ssenarilər və **Hamısını sıfırla** (sidebar-da yoxdur) |
| http://localhost:8080/swagger-ui.html | API sənədi |

**Demo giriş** (login səhifəsində də göstərilir):

| Rol | Email | Şifrə |
|---|---|---|
| Dispetçer | `dispatcher@yolcu.az` | `demoyolcu123` |
| Admin (demo paneli) | `admin@yolcu.az` | `demoyolcu123` |

**Sıfırlama:** hər göstərişdən əvvəl və sonra `/demo` → **Hamısını sıfırla**. Simulyatoru, intervalları, hub təkliflərini və demo sessiyasında yaradılan şikayət, sorğu, Yolçu Coin dəyişiklikləri və lent hadisələrini başlanğıca qaytarır; seed datası qalır.

**4 dəqiqəlik demo ssenarisi:** [`docs/09_24H_PLAN_AND_DEMO.md` §4](docs/09_24H_PLAN_AND_DEMO.md).

## 💻 İnkişaf

```bash
pnpm install
DB_PORT=5433 pnpm dev    # Postgres + API :8080 (avtomatik restart) + web :3000 (hot reload)
```

| Əmr | Nə edir |
|---|---|
| `pnpm dev` | Backend (nodemon compile watcher + devtools restart) və Next.js dev server birlikdə |
| `cd backend && mvn -B -ntp test` | Backend testləri (129) |
| `pnpm build` / `pnpm test` | Web build / `packages/shared` testləri |
| `mvn -f backend/pom.xml -Pweb clean package` | Web daxil tək jar → `java -jar backend/target/backend-0.0.1.jar` |

Web-i backend-siz işlətmək: `web/.env.local`-da `NEXT_PUBLIC_API_MODE=mock`. Kod qaydaları və iş axını: [`CLAUDE.md`](CLAUDE.md).

## ☁️ Deploy

Render-də pulsuz: bir Docker web service (web + API) və Postgres, [`render.yaml`](render.yaml) Blueprint ilə.

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/ilkinismayilov-905/yolcu)

Addımlar, mühit dəyişənləri, yaddaş limitləri və deploydan sonra yoxlama siyahısı: [`docs/11_DEPLOY.md`](docs/11_DEPLOY.md).

Lokal olaraq eyni image:

```bash
docker build -t yolcu .
docker run --rm --memory=512m -p 8090:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL=postgresql://yolcu:yolcu@host.docker.internal:5432/yolcu \
  -e JWT_SECRET=$(openssl rand -base64 32) -e DEMO_PASSWORD=demoyolcu123 -e DEMO_ENABLED=true \
  yolcu
```

## 📁 Layihə strukturu

```
yolcu/
├── backend/             Spring Boot (az.yolcu): report, survey, coin, occupancy, sim, traffic,
│                        hub, proposal, analytics, fleet, biletim, feed, stream, demo …
├── web/                 Next.js dashboard (app/(dash)/<səhifə>, components, lib)
├── packages/shared/     TS tipləri və formulalar (web + gələcək mobil)
├── data/processed/      Real GeoJSON qatları (marşrutlar, metro, velo, avtovağzallar)
├── data/convert.py      opendata.az KMZ/CSV → GeoJSON (yalnız stdlib)
├── mocks/               Seed və simulyasiya üçün JSON-lar
├── design/              Kliklə işləyən web və mobil mockup-lar
├── docs/                Məhsul, arxitektura, API müqaviləsi, roadmap-lər, deploy
├── Dockerfile, render.yaml, run.sh, docker-compose.yml
└── CLAUDE.md            Kod qaydaları və iş axını
```

## 🗂️ Data mənbələri

| Data | Mənbə | Harada |
|---|---|---|
| Avtobus marşrut trayektoriyaları (243) | [opendata.az](https://opendata.az) — AYNA | Xəritə, simulyator, tıxac xətləri |
| Metro çıxışları (98) və gündəlik sərnişin sayı | opendata.az | Hub koordinatları, analitika |
| Velosiped parkları (834) və zolaqları (60) | opendata.az — AYNA | Xəritə qatı |
| Avtovağzal və avtostansiyalar (69) | opendata.az — AYNA | Xəritə qatı |
| Baza xəritə | © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors, [OpenFreeMap](https://openfreemap.org) | Canlı xəritə |

Çevirmə və yoxlama nəticələri: [`docs/04_DATA.md`](docs/04_DATA.md). Operator adları realdır, onların göstəriciləri demo datasıdır.

## 📚 Sənədlər

| Sənəd | Məzmun |
|---|---|
| [00 Məhsul](docs/00_PRODUCT.md) | Problem, rollar, əsas axınlar, Yolçu Coin modeli |
| [01 Real / SIM / FAKE](docs/01_SCOPE_REAL_SIM_FAKE.md) | Hər funksiyanın statusu |
| [02 Arxitektura](docs/02_ARCHITECTURE.md) | Paketlər, DB sxemi, SSE, simulyator, formulalar |
| [03 API müqaviləsi](docs/03_API_CONTRACT.md) | Bütün endpoint-lər və JSON formaları |
| [04 Data](docs/04_DATA.md) | Yükləmə, çevirmə, seed |
| [05](docs/05_BACKEND_ROADMAP.md) · [06](docs/06_WEB_ROADMAP.md) · [07](docs/07_MOBILE_ROADMAP.md) | Backend, web və mobil roadmap-ləri |
| [08 Dizayn sistemi](docs/08_DESIGN_SYSTEM.md) | Token-lər, komponentlər, mətn qaydaları, brend |
| [09 Plan və demo](docs/09_24H_PLAN_AND_DEMO.md) | 24 saatlıq plan, 4 dəqiqəlik demo ssenarisi |
| [10 Web dilimləri](docs/10_WEB_FULLSTACK_SLICES.md) | Backend + frontend dilim-dilim |
| [11 Deploy](docs/11_DEPLOY.md) | Render, Docker, mühit dəyişənləri |

## 🛣️ Status

- ✅ Web dashboard və backend: bütün dilimlər (S0–S11) hazırdır, demo `v1.0-demo` tag-ındadır
- ✅ Web dashboard responsivdir: telefon, planşet, laptop (açıq və tünd rejim)
- 🔜 Mobil tətbiq (Expo): dizayn ([`design/mobile_app.html`](design/mobile_app.html)) və [roadmap](docs/07_MOBILE_ROADMAP.md) hazırdır; mobil endpoint-lərin bir hissəsi (`/me/home`, `/trip/plan`, `/trips`) hələ yazılmayıb — demo-da sərnişinin rolunu `/demo` paneli oynayır
