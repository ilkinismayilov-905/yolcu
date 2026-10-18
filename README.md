# Yolçu — AYNA üçün vahid nəqliyyat platforması

Web dashboard (Next.js) + mobil tətbiq (React Native, Expo) + backend (Java Spring Boot). 1 günlük hakaton üçün Claude Code ilə qurulur.

## Paketdə nə var

```
Yolcu/
├── CLAUDE.md                        Claude Code üçün əsas qaydalar (repo kökünə qoyun)
├── .claude/agents/                  backend-dev və web-dev köməkçi agentləri
├── README.md
├── docs/
│   ├── 00_PRODUCT.md                Problem, həll, rollar, modullar, coin, səfər izləmə
│   ├── 01_SCOPE_REAL_SIM_FAKE.md    Hansı hissə real Java, hansı simulyasiya, hansı fake
│   ├── 02_ARCHITECTURE.md           Paketlər, DB sxemi, SSE, simulyator, formulalar
│   ├── 03_API_CONTRACT.md           Bütün endpoint-lər və JSON formaları
│   ├── 04_DATA.md                   Data çevirmə, seed, simulyator parametrləri
│   ├── 05_BACKEND_ROADMAP.md        BE-0 … BE-10
│   ├── 06_WEB_ROADMAP.md            WEB-0 … WEB-11
│   ├── 07_MOBILE_ROADMAP.md         MOB-0 … MOB-10
│   ├── 08_DESIGN_SYSTEM.md          Rənglər, şrift, komponentlər, mətn qaydaları
│   ├── 09_24H_PLAN_AND_DEMO.md      Saat-saat plan, komanda, demo ssenarisi, risklər
│   └── 10_WEB_FULLSTACK_SLICES.md   Web: backend + frontend birlikdə, dilim-dilim
├── design/
│   ├── web_dashboard.html           Web üçün kliklə işləyən vizual mənbə
│   └── mobile_app.html              Mobil üçün kliklə işləyən vizual mənbə
└── mocks/                           Seed və FAKE hissələr üçün JSON-lar
```

## Demo-nu necə işə salmaq

Tələb: Docker, Java 21, Maven, Node.js (pnpm lazım deyil — `npx pnpm@9.15.9` avtomatik işləyir).

```bash
./run.sh                      # Postgres + web build + API, hamısı bir prosesdə
DB_PORT=5433 ./run.sh         # lokal Postgres 5432-ni tutursa
```

- **İlk işə salma:** cache-lər doludursa ~20 saniyə (ölçülüb: 18 s — Docker, `pnpm install`, web export, Spring başlanğıcı). Təmiz maşında (boş pnpm/Maven cache, yavaş internet) asılılıqların yüklənməsinə görə **~5 dəqiqə** (ölçülüb: 309 s). Demo-dan əvvəl bir dəfə işə salın.
- Ayrıca jar: `mvn -B -ntp -f backend/pom.xml -Pweb clean package` → `java -jar backend/target/backend-0.0.1.jar` (repo kökündən; web + API eyni portda, default 8080, `--server.port=…` ilə dəyişir).

Açılacaq URL-lər:

| URL | Nə |
|---|---|
| http://localhost:8080 | Dashboard (`dispatcher@yolcu.az` / `demoyolcu123`) |
| http://localhost:8080/demo/ | Demo paneli: ssenari düymələri və **Hamısını sıfırla** (sidebar-da yoxdur) |
| http://localhost:8080/swagger-ui.html | API sənədi |

**Sıfırlama:** hər göstərişdən əvvəl və sonra `/demo` → **Hamısını sıfırla** (və ya `curl -X POST -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/demo/reset`). Simulyatoru, intervalları, hub təkliflərini, demo sərnişinin bugünkü şikayət/səfər/Yolçu Coin dəyişikliklərini, demo çevirmələrini, bu demo sessiyasında (bu gün 08:12-dən sonra) yaradılan sorğuları və lent hadisələrini başlanğıca qaytarır; sxemi və seed-i silmir. Diqqət: real istifadədə bu gün yaradılmış sorğular da silinir — reset yalnız demo üçündür.

Demo ssenarisi (4 dəqiqə): `docs/09_24H_PLAN_AND_DEMO.md` §4. İnkişaf rejimi (`pnpm dev`, hot reload): `CLAUDE.md` §7.

## Necə başlamaq

1. Bu paketi repo-nun kökünə açın. Verilən dataları `data/raw/`-a qoyun.
2. Hər kəs `docs/09_24H_PLAN_AND_DEMO.md`-də öz rolunu və H0–H3 işlərini oxuyur.
3. Claude Code-u öz qovluğunuzda açın və ilk tapşırığı verin:

```
CLAUDE.md və docs/01_SCOPE_REAL_SIM_FAKE.md-ni oxu. Sonra 05_BACKEND_ROADMAP.md BE-0
bəndlərini et. Əvvəlcə plan yaz.
```

## Əsas qərarlar

- Bir monolit backend, bir Postgres, bir SSE kanalı.
- Hər funksiya REAL, SIM və ya FAKE etiketi daşıyır. Demo-nun canlı anları REAL-dır.
- Frontend-lər mock rejimdə də işləyir: backend gecikəndə iş dayanmır.
- Ekologiya bölməsi çıxarılıb. Cashback yerinə coin: real pul deyil, partnyor bank vasitəsilə manata çevrilir.
- Operatorlar: BakuBus MMC, Xaliq Faiqoğlu MMC, Çinar-Trans MMC, 6 Dost MMC (adlar real, rəqəmlər demo).
