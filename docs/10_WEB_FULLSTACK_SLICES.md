# 10 — Web: backend və frontend birlikdə (vertical slice)

Web işi **funksiya-funksiya** gedir. Hər dilim (slice) bir səhifəni başdan-sona bitirir:

```
1. Müqavilə   → 03_API_CONTRACT.md-dəki formaları packages/shared/src/api.ts-də yoxla
2. Backend    → Java endpoint + DB + SSE (05_BACKEND_ROADMAP bəndləri)
3. Yoxlama    → curl və ya Swagger ilə cavab müqaviləyə uyğundur
4. Frontend   → Next.js səhifəsi (06_WEB_ROADMAP bəndləri), design/web_dashboard.html-ə görə
5. Birlikdə   → səhifə live rejimdə (API_MODE=live) real backend ilə işləyir
6. Bitir      → hər iki roadmap-də [x], commit: "slice S1: reports"
```

**Qayda:** dilim yalnız 5-ci addım keçəndə bitmiş sayılır. "Backend hazırdır, front sonra" və ya "UI hazırdır, API yoxdur" yarımçıq işdir.

## Dilimlər

| # | Dilim | Backend | Frontend | Etiket | Asılılıq |
|---|---|---|---|---|---|
| **S0** | Təməl | BE-0, BE-2 (SSE) | WEB-0 | REAL | — |
| **S1** | Şikayətlər | BE-4 (mobil `POST` daxil, test üçün) | WEB-2 | REAL | S0 |
| **S2** | Canlı xəritə və lent | BE-1, BE-3, BE-8.6 | WEB-1.1–1.6 | SIM | S0 |
| **S3** | Avtobus doluluğu və interval | BE-8.1–8.3 | WEB-4 | REAL + SIM | S2 |
| **S4** | Sorğular | BE-7 (mobil cavab endpoint-i daxil) | WEB-3 | REAL | S0 |
| **S5** | Hub kameraları | BE-8.4 | WEB-5 | SIM | S2 |
| **S6** | Operatorlar, avtopark, qlobal axtarış | BE-9.3, BE-9.4 | WEB-6, WEB-2.7 | REAL | S1 |
| **S7** | Avtovağzal və Biletim.az | BE-8.5 | WEB-7 | SIM | S2 |
| **S8** | Coin proqramı | BE-5.1, BE-5.3, BE-5.5 | WEB-10.1 | REAL | S1 |
| **S9** | Analitika, hub təklifi, süni tıxac, demo paneli | BE-9.1, BE-9.2, BE-9.5 | WEB-9, WEB-1.7, WEB-10.3 | REAL + FAKE | S2, S3 |
| **S10** | Yalnız frontend səhifələr | — | WEB-8 (bölgü), WEB-10.2 (inteqrasiyalar) | FAKE | S0 |
| **S11** | Sabitləşdirmə | BE-10 | WEB-11 | — | hamısı |

Mobil tətbiq eyni endpoint-ləri istifadə edir. Web dilimi mobil endpoint-ə ehtiyac duyursa (məs. S1-də şikayət yaratmaq), həmin endpoint də dilimin içində yazılır ki, web tərəfi real data ilə test oluna bilsin.

## Kim nə edir

**Bir nəfər web + backend edirsə:** dilimləri sıra ilə edin: S0 → S1 → S2 → S3 → S4 → S9 → S6 → S5 → S7 → S8 → S10 → S11.

**İki nəfər (Dev 1 və Dev 2) paralel işləyirsə:**

| Saat | Dev 1 | Dev 2 |
|---|---|---|
| H0–H3 | S0 backend hissəsi (BE-0, BE-2) | S0 frontend hissəsi (WEB-0), `api.ts` |
| H3 | **CP1:** login web-dən backend-ə işləyir | |
| H3–H8 | S1 Şikayətlər | S2 Canlı xəritə |
| H8–H12 | S4 Sorğular | S3 Doluluq |
| H12–H15 | S6 Operatorlar | S5 Hub kameraları, S7 Avtovağzal |
| H15–H18 | S8 Coin, S10 | S9 Analitika və demo |
| H18–H20 | S11 | S11 |

Mobil developer eyni backend-ə qoşulur. Mobil üçün lazım olan endpoint-lər (BE-6 səfər və planlayıcı, BE-5.2 cüzdan) Dev 1-in növbəsinə H8-dən sonra əlavə olunur və ya mobil developer onları özü yazır.

## Paralel iş: iki Claude Code sessiyası

İki nəfər eyni repo-da işləyəndə bir-birinin fayllarını pozmamaq üçün **git worktree** istifadə edin:

```bash
# repo kökündə
git worktree add ../Yolcu-dev1 -b dev1
git worktree add ../Yolcu-dev2 -b dev2

# Dev 1:
cd ../Yolcu-dev1 && claude
# Dev 2:
cd ../Yolcu-dev2 && claude
```

Hər dilim bitəndə: `git pull --rebase origin main && git push origin HEAD:main`.

Fayl toqquşmasının qarşısını almaq üçün:
- `packages/shared/src/api.ts` və `03_API_CONTRACT.md` yalnız S0-da yazılır. Sonra dəyişiklik lazım olsa, kiçik ayrıca commit, komandaya xəbər.
- Backend-də hər dilim öz paketindədir (`report/`, `survey/`...), web-də öz `app/(dash)/<səhifə>/` qovluğundadır. Ortaq fayllar (`SecurityConfig`, `layout.tsx`, sidebar) yalnız S0-da.

## Bir sessiyada paralel: köməkçi agentlər

`.claude/agents/` qovluğundakı iki agent bir Claude Code sessiyasında işi bölür: əsas sessiya dilimi planlayır, `backend-dev` Java hissəsini, `web-dev` Next.js hissəsini yazır. Müqavilə (`03_API_CONTRACT.md`) hər ikisi üçün eynidir, ona görə eyni anda işləyə bilərlər: frontend əvvəlcə mock rejimdə qurulur, backend hazır olanda live rejimə keçir.

## Hazır tapşırıq şablonları

**Bir dilim, bir sessiya:**

```
docs/10_WEB_FULLSTACK_SLICES.md-dən S1 (Şikayətlər) dilimini et.
Sıra: api.ts tiplərini yoxla → BE-4 (backend) → curl ilə yoxla → WEB-2 (frontend,
design/web_dashboard.html #p-reports-a görə) → API_MODE=live ilə birlikdə yoxla.
Hər addımdan sonra qısa hesabat ver. Dilim yalnız 5-ci addım keçəndə bitir.
```

**Bir dilim, iki agent paralel:**

```
S3 (Avtobus doluluğu) dilimini et. backend-dev agentinə BE-8.1–8.3-ü,
web-dev agentinə WEB-4-ü ver, paralel işləsinlər. web-dev əvvəlcə mock rejimdə qursun.
İkisi bitəndə live rejimdə birlikdə yoxla və uyğunsuzluqları düzəlt.
```

**Dilim bitəndə yoxlama:**

```
S1 dilimini yoxla: backend işləyir, web live rejimdədir. Telefon yoxdursa
curl ilə POST /me/reports göndər və dashboard-da 2 saniyə ərzində görünməsini yoxla.
Sonra "Operatora göndər" düyməsi ilə statusun dəyişdiyini yoxla. Nəticəni yaz.
```

## Hər dilimin "bitdi" yoxlaması

- [ ] Endpoint-lər Swagger-də görünür, cavablar `03_API_CONTRACT.md` ilə eynidir
- [ ] Səhifə `API_MODE=live` ilə real data göstərir
- [ ] Boş, yüklənir və xəta vəziyyətləri var
- [ ] SSE olan yerdə (şikayət, lent, doluluq) yeniləmə səhifəni yeniləmədən gəlir
- [ ] Görünüş `design/web_dashboard.html` ilə uyğundur (rənglər, mətnlər, düymələr)
- [ ] `05_BACKEND_ROADMAP` və `06_WEB_ROADMAP`-da bəndlər `[x]`
- [ ] Commit: `slice S<n>: <ad>`
