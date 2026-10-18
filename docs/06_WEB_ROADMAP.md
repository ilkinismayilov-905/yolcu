# 06 — Web roadmap (Next.js dashboard)

**Vizual mənbə:** `design/web_dashboard.html`. Hər səhifəni brauzerdə aç, eyni strukturu, mətni və davranışı təkrarla. Rənglər və komponentlər: `08_DESIGN_SYSTEM.md`.

Bir web developer (**FE-W**). Fake səhifələrdə məhsul/pitch üzvü (**PM**) kömək edə bilər.

## Marşrutlar (App Router)

```
web/app/
├── login/page.tsx
├── (dash)/layout.tsx           # sidebar + topbar + SSE provider
├── (dash)/map/page.tsx         # Canlı xəritə
├── (dash)/hubs/page.tsx        # Hub kameraları
├── (dash)/occupancy/page.tsx   # Avtobus doluluğu
├── (dash)/split/page.tsx       # Avtobus bölgüsü          (FAKE)
├── (dash)/terminal/page.tsx    # Avtovağzal və Biletim.az
├── (dash)/analytics/page.tsx   # Data analitika (4 tab)
├── (dash)/reports/page.tsx     # Şikayətlər
├── (dash)/surveys/page.tsx     # Sorğular
├── (dash)/coins/page.tsx       # Coin proqramı
├── (dash)/operators/page.tsx   # Operatorlar və avtopark
├── (dash)/integrations/page.tsx# İnteqrasiyalar           (FAKE)
└── (dash)/demo/page.tsx        # Demo idarə paneli (sidebar-da yoxdur)
```

Sidebar qrupları (mockup-dakı kimi): **Canlı** (Canlı xəritə, Hub kameraları, Avtobus doluluğu) · **Planlaşdırma** (Avtobus bölgüsü, Avtovağzal və Biletim.az) · **Analitika** (Data analitika) · **Sərnişinlər** (Şikayətlər + yeni sayı nişanı, Sorğular, Coin proqramı) · **Tərəfdaşlar** (Operatorlar, İnteqrasiyalar).

---

## WEB-0 Təməl — H0–H3 · MUST

- [x] **WEB-0.1** `create-next-app` (TS, App Router, Tailwind). Token-lər `tailwind.config` və `globals.css` CSS dəyişənləri kimi (`08_DESIGN_SYSTEM` §1), açıq/tünd rejim (`data-theme` + sistem).
- [x] **WEB-0.2** Şrift: Onest (`next/font/google`), `font-variant-numeric: tabular-nums`.
- [x] **WEB-0.3** Primitiv komponentlər: `Button` (p/default/quiet/sm), `Panel` (+ başlıq, meta, actions), `Kpi`, `Seg` (seqmentli), `Toggle` (qat çipi), `Switch`, `Plate` (marşrut lövhəsi), `Status` (nöqtə + mətn), `Bar`, `Table` (+ boş vəziyyət), `Pager`, `Drawer`, `Modal`, `Toast`, `DemoBadge`.
- [x] **WEB-0.4** Layout: sidebar (qruplar), topbar (qlobal axtarış, "Demo data", "Canlı" nöqtəsi + saat, mövzu düyməsi, avatar).
- [x] **WEB-0.5** `/login` (email, şifrə, xəta mesajı), token `localStorage`-da, 401-də login-ə yönləndir.
- [x] **WEB-0.6** `packages/shared` qoşulur: `api.ts` tipləri, `client.ts` (`NEXT_PUBLIC_API_MODE=mock|live`), `headway.ts`, `toll.ts`.
- [x] **WEB-0.7** `useStream` hook (EventSource, yenidən qoşulma, "Bağlantı bərpa olunur" zolağı) + `StreamProvider` (hadisəni TanStack Query cache-inə yazır).

**Qəbul:** dispetçer daxil olur, bütün 11 səhifəyə sidebar ilə keçir (boş olsa da), mövzu dəyişir, mock rejimdə də işləyir.

## WEB-1 Canlı xəritə — H3–H8 · MUST

> Xəritə real OSM xəritəsidir, mockup-dakı SVG yalnız nümunədir. Bütün qatlar real koordinatlarla backend-dən gəlir (`/live/*`, `/geo/layers/routes-lite`, `metro-exits`, `bike-lanes`, `bike-parkings`); client-də həndəsə uydurulmur.

- [x] **WEB-1.1** `react-map-gl` + MapLibre, açıq stil: OpenFreeMap `positron` (açarsız). Mərkəz Bakı, zoom 12. Küçə adları `name:az`, atribusiya aşağı sağda.
- [x] **WEB-1.2** Qatlar və çiplər: Tıxac (`/live/traffic`, xətt rəngi səviyyəyə görə), Hub sıxlığı (dairə + "N nəfər" etiketi), Avtobuslar, Marşrutlar (`/geo/layers/routes-lite`), Kameralar (`/admin/hubs`), AI təklifləri (WEB-1.7-də dolur), Metro (`metro-exits`), Velo (`bike-lanes` + `bike-parkings`, default bağlı).
- [x] **WEB-1.3** **Avtobuslar qatında yalnız 2 avtobus**: 88-dən biri və 4-dən biri (`bus.positions` SSE, client-də filtr). Avtobusa klik → `/occupancy?route=88`.
- [x] **WEB-1.4** KPI kartları (`/admin/kpis`, 30 san yenilənmə).
- [x] **WEB-1.5** Hadisələr lenti (`/admin/feed` + `alert.created`; yeni şikayət backend-də `REPORT` alert-i yaradır), kateqoriya rəngi, kliklə müvafiq səhifəyə keçid, yeni hadisə qısa vurğu.
- [x] **WEB-1.6** Legend (sabit, aşağı solda).
- [ ] **WEB-1.7** **"Süni tıxac yarat (demo)"** düyməsi → `POST /demo/trigger/artificial-jam` → Babək pr. rəngi dəyişir → `proposal.created` gələndə xəritədə kəsik xətli "Yeni hub təklifi" markeri + sağda **Hub təklifi drawer-i** (səbəb, 4 göstərici, nə qurulmalı, təsir, "Plana əlavə et" → `accept`, "Analitikaya bax").

**Qəbul:** xəritədə 2 avtobus real vaxtda hərəkət edir; qatlar açılıb-bağlanır; demo düyməsindən 5 saniyə sonra hub təklifi drawer-i açılır.

## WEB-2 Şikayətlər — H6–H10 · MUST

- [x] **WEB-2.1** KPI: bu gün, cavab gözləyir, operatora göndərilib, 24 saatdan çox gecikən (`/admin/reports/stats`).
- [x] **WEB-2.2** Alət çubuğu: axtarış (ID, bort, marşrut, mətn), status seqmenti (Hamısı, Yeni, Baxılır, Operatorda, Bağlanıb), mövzu seçimi, operator seçimi, nəticə sayı. Filtrlər URL query-də.
- [x] **WEB-2.3** Cədvəl (ID, vaxt, marşrut lövhəsi, bort, operator, mövzu + mətn önizləməsi, status) + səhifələmə (10). Boş vəziyyət mətni.
- [x] **WEB-2.4** Sətir → drawer: sitat, foto yeri, açar-dəyər siyahısı, tarixçə (timeline), düymələr: Paylaş, Baxılır et, Operatora göndər, Həll edildi (statusa görə).
- [x] **WEB-2.5** Paylaş modalı (Operator, YPX, İcra Hakimiyyəti, şəxsi məlumatı gizlət).
- [x] **WEB-2.6** `report.created` SSE → siyahının başına əlavə, sidebar nişanı +1, lentə hadisə. (Lent hələ yoxdur: sətir vurğusu + toast; lent WEB-1-də `alert.created` ilə.)
- [x] **WEB-2.7** Qlobal axtarış `A-2042` → bu səhifə, filtrlənmiş.

**Qəbul:** telefondan göndərilən şikayət 2 saniyəyə cədvəldə və lentdə görünür; "Operatora göndər" basılanda status dəyişir və telefondakı status ekranı yenilənir.

## WEB-3 Sorğular — H9–H12 · MUST

- [ ] **WEB-3.1** Tablar: Bütün sorğular / Yeni sorğu.
- [ ] **WEB-3.2** Siyahı: filtr (Hamısı, Aktiv, Bitib, Qaralama), cədvəl (sorğu, status, auditoriya, cavab, coin). Sətir → sağ paneldə nəticələr (sual üzrə faiz zolaqları), aktivdirsə "Sorğunu bitir", qaralamadırsa "İndi göndər".
- [ ] **WEB-3.3** Yeni sorğu forması: başlıq, sual, variantlar (vergüllə), auditoriya, mükafat (5/10/20 coin), "Sorğunu göndər" və "Qaralama kimi saxla". Sağda bildiriş önizləməsi canlı yenilənir.
- [ ] **WEB-3.4** Göndərəndən sonra siyahıya keç, yeni sorğu seçili, toast.

**Qəbul:** yeni sorğu göndəriləndə telefonda bildiriş görünür; telefon cavab verəndən sonra nəticələrdə cavab sayı artır (yenidən yükləmə ilə).

## WEB-4 Avtobus doluluğu — H8–H13 · MUST

- [x] **WEB-4.1** Başlıqda "Avtomatik tənzimləmə" açarı (`PUT /admin/occupancy/auto`).
- [x] **WEB-4.2** KPI-lar.
- [x] **WEB-4.3** Marşrut cədvəli (lövhə, avtobus, doluluq zolağı, interval, tövsiyə, məmnunluq dəyişimi). Seçim → sağda avtobus siyahısı (salon miniatürü SVG, `84 / 90`, status).
- [x] **WEB-4.4** İnterval paneli: slayder 6–15, 4 göstərici (`packages/shared/headway.ts` ilə dərhal), qeyd mətni, "İntervalı tətbiq et" (`POST .../interval`), "Əlavə avtobusu haradan götürək?" → bölgü səhifəsi.
- [x] **WEB-4.5** Topa yığılma kartı (sxem SVG, mətn, "Sürücüyə mesaj göndər").
- [x] **WEB-4.6** Doluluq və məmnunluq qrafiki — **FAKE**, statik.

**Qəbul:** slayder hərəkətində rəqəmlər < 100 ms dəyişir; tətbiq edəndə cədvəldə interval yenilənir və 30 saniyədə doluluq düşür.

## WEB-5 Hub kameraları — H12–H14 · MUST

- [ ] **WEB-5.1** Seqment: Hamısı, Hub-lar, Dayanacaqlar, Yalnız sıx.
- [ ] **WEB-5.2** Kamera kartları: SVG səhnə (insan fiqurları sayına görə, çərçivə rəngi səviyyəyə görə; **FAKE vizual**), altında ad, böyük rəqəm (`hub.counts` SSE ilə canlı), orta gözləmə, son 10 dəq dəyişim.
- [ ] **WEB-5.3** Hub cədvəli (indi, doluluq zolağı, 15 dəq sonra, tövsiyə, "Tətbiq et").
- [ ] **WEB-5.4** "Kamera rəqəmi qərara necə çevrilir" — 4 addım (statik).

## WEB-6 Operatorlar və avtopark — H13–H15 · MUST

- [ ] **WEB-6.1** 4 operator kartı: BakuBus MMC, Xaliq Faiqoğlu MMC, Çinar-Trans MMC, 6 Dost MMC (xətdə / cəmi, orta rəy, şikayət / 10 min, dəqiqlik, marşrutlar). Klik → avtopark filtrlənir, təkrar klik → filtr götürülür.
- [ ] **WEB-6.2** Avtopark cədvəli: axtarış (bort, marşrut), status seqmenti, növ seçimi, sütunlar (bort, operator, marşrut, növ və model, status, doluluq, sürücü rəyi), səhifələmə 12.

**Qəbul:** "Xaliq Faiqoğlu" seçiləndə yalnız `99-XF-` bortları görünür; bort axtarışı topbar-dan da işləyir.

## WEB-7 Avtovağzal və Biletim.az — H14–H15 · MUST

- [ ] **WEB-7.1** Gələn reyslər cədvəli (`/admin/biletim/arrivals`): gəliş, haradan + nömrə, satılmış bilet, vəziyyət, şəhərə keçid statusu.
- [ ] **WEB-7.2** Ekspress tövsiyələri M1 və H1 (**FAKE** hesab: biletlərin cəmi / tutum), "Planı təsdiqlə".
- [ ] **WEB-7.3** Çıxış axını qrafiki (statik).

## WEB-8 Avtobus bölgüsü — H15–H16 · SHOULD (FAKE)

- [ ] **WEB-8.1** 33 və 88 kartları, mini avtobus nişanları, slayder 0–3, 3 göstərici, şərtlər siyahısı, təsdiq/rədd (toast), saatlıq tələbat-tutum qrafiki. Hamısı `headway.ts` ilə.

## WEB-9 Data analitika — H15–H18 · MUST

- [ ] **WEB-9.1** Tablar: Axın və matris · Proqnoz · Ödənişli yollar · Datanın tətbiqi.
- [ ] **WEB-9.2** Axın və matris: KPI-lar (REAL), OD matrisi heatmap (REAL `/admin/analytics/od`), hub saat × gün matrisi (FAKE), düşmə nöqtələri cədvəli (REAL).
- [ ] **WEB-9.3** Proqnoz (FAKE): seqment (Şəbəkə, 88, 4, M1), fakt + proqnoz + etibarlılıq zolağı SVG/Recharts, xəta mətni; sabahkı hub-lar cədvəli; amillər zolaqları.
- [ ] **WEB-9.4** Ödənişli yollar (FAKE, `toll.ts`): prinsip qeydi, namizəd cədvəli (bal ilə sıralı, sətir seçimi), ssenari paneli (qiymət slayderi 0,5–3 ₼, saat seqmenti, 4 nəticə, izah), pilot tövsiyəsi, "Sərnişinlərdən soruş" → sorğular.
- [ ] **WEB-9.5** Datanın tətbiqi (FAKE): qərar × mənbə matrisi, data məhsulları cədvəli, məxfilik qaydaları qeydi.

**Qəbul:** 4 tabın hamısı keçid edir; ödənişli yolda qiymət dəyişəndə nəticələr dərhal dəyişir; Heydər Əliyev pr. seçiləndə "tövsiyə edilmir" mətni çıxır.

## WEB-10 Coin, inteqrasiyalar, demo paneli — H16–H18

- [ ] **WEB-10.1** Coin proqramı: KPI-lar, qaydalar cədvəli, çevirmə sorğuları cədvəli (REAL). · MUST
- [ ] **WEB-10.2** İnteqrasiyalar: 12 kart (`mocks/integrations.json`), Biletim.az birinci, "Bağlantını yoxla" toast. · MUST (FAKE)
- [ ] **WEB-10.3** `/demo`: hər ssenari üçün iri düymə (`03_API` §10) + "Sıfırla". Yalnız ADMIN. · MUST

## WEB-11 Cilalama — H18–H20 · MUST

- [ ] **WEB-11.1** Hər cədvəldə boş, yüklənir (skeleton) və xəta vəziyyətləri; xəta mətni "nə oldu + nə etmək olar".
- [ ] **WEB-11.2** Tünd rejimi bütün səhifələrdə yoxla.
- [ ] **WEB-11.3** 1440 × 900 və 1920 × 1080 (proyektor) ekranlarda yoxla.
- [ ] **WEB-11.4** `Esc` drawer və modalı bağlayır; fokus görünür.
- [ ] **WEB-11.5** SIM data olan hər səhifədə "Demo data" nişanı.

## Claude Code üçün nümunə tapşırıq

```
06_WEB_ROADMAP.md WEB-2.1–WEB-2.5. design/web_dashboard.html-də #p-reports bölməsini
və openReport funksiyasını aç: eyni struktur, mətnlər və davranış. API: 03_API_CONTRACT §9
Şikayətlər. Komponentlər WEB-0.3-dən. Əvvəlcə mock rejimdə işlət, sonra live.
```
