# HALQA — AYNA üçün vahid nəqliyyat platforması

Web dashboard (Next.js) + mobil tətbiq (React Native, Expo) + backend (Java Spring Boot). 1 günlük hakaton üçün Claude Code ilə qurulur.

## Paketdə nə var

```
halqa-v2/
├── CLAUDE.md                        Claude Code üçün əsas qaydalar (repo kökünə qoyun)
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
│   └── 09_24H_PLAN_AND_DEMO.md      Saat-saat plan, komanda, demo ssenarisi, risklər
├── design/
│   ├── web_dashboard.html           Web üçün kliklə işləyən vizual mənbə
│   └── mobile_app.html              Mobil üçün kliklə işləyən vizual mənbə
└── mocks/                           Seed və FAKE hissələr üçün JSON-lar
```

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
