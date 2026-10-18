# 08 — Dizayn sistemi

**İstiqamət:** Waze və AYNA MaaS kimi nəqliyyat tətbiqlərinin sakit, aydın dili. Ağ və açıq boz səthlər, tünd göy-qara mətn, bir əsas mavi rəng. Rəng yalnız məna daşıyanda istifadə olunur (tıxac, avtobus növü, insan sıxlığı, coin). Bəzək üçün qradient, kölgə yığını, rəngli kartlar yoxdur.

**Yaddaqalan bir element:** marşrut lövhəsi (yaşıl elektrik, qırmızı dizel, qalın rəqəm). Qalan hər şey sakitdir.

## 1. Rəng token-ləri

| Token | Açıq | Tünd | Nə üçün |
|---|---|---|---|
| `--bg` | `#F4F6F9` | `#0B121C` | Səhifə fonu |
| `--surface` | `#FFFFFF` | `#111A26` | Kart, panel, vərəq |
| `--surface-2` | `#F2F5F8` | `#16212F` | Daxili blok, input, hover |
| `--surface-3` | `#E9EEF4` | `#1C2939` | Zolaq fonu, deaktiv |
| `--line` | `#E3E8EF` | `#223044` | Xətlər, sərhədlər |
| `--ink` | `#0E1A2B` | `#E6EDF5` | Əsas mətn |
| `--ink-2` | `#536274` | `#9AAABD` | İkinci dərəcəli mətn |
| `--ink-3` | `#8796A8` | `#6C7C90` | Etiket, vaxt, ipucu |
| `--primary` | `#0B6FCB` | `#3B9BFF` | Əsas düymə, seçim, link, proqnoz |
| `--primary-soft` | `#E8F2FC` | `#14273D` | Seçilmiş sətir, aktiv nav |
| `--live` | `#1FB8E8` | eyni | İstifadəçi mövqeyi nəbzi, skuter |
| `--ok` | `#13935A` | eyni | Uğur, "uyğundur" |
| `--warn` | `#C98209` | eyni | Diqqət |
| `--bad` | `#D4404A` | eyni | Xəta, gecikmə |
| `--crowd` | `#6E55E8` | eyni | **Yalnız** insan sayı / sıxlıq |
| `--coin` | `#E3A20F` | eyni | **Yalnız** coin |
| `--bus-e` | `#13935A` | eyni | **Yalnız** elektrik avtobus lövhəsi və markeri |
| `--bus-d` | `#D4404A` | eyni | **Yalnız** dizel avtobus |
| `--metro` | `#C8102E` | eyni | Metro "M" |
| `--fluid` / `--slow` / `--jam` | `#2DB46F` / `#F2A93B` / `#E5484D` | eyni | **Yalnız** yol tıxacı və doluluq zolağı |

Qaydalar:
- Bir ekranda əsas rəng (`--primary`) ilə ən çox bir "əsas" düymə.
- Tıxac rəngləri UI elementlərində (düymə, nişan) istifadə olunmur.
- Status: rəngli nöqtə + mətn (`● Gecikir`), dolu rəngli "pill" yox.
- Tünd rejim sistemdən gəlir, istifadəçi dəyişə bilir.

## 2. Tipoqrafiya

Bir şrift: **Onest** (Google Fonts, Azərbaycan hərfləri ilə). Rəqəmlər `tabular-nums`.

| Rol | Web | Mobil | Çəki |
|---|---|---|---|
| Səhifə başlığı | 22 | 24 | 700 |
| Panel başlığı | 15 | 17 | 600 |
| KPI rəqəmi | 26 | 22–40 | 700–800 |
| Əsas mətn | 14 | 15 | 400–500 |
| Kiçik / etiket | 12–13 | 12–13 | 500 |
| Marşrut lövhəsi | 12–18 | 11–21 | 800 |

Böyük hərflə etiket (ALL CAPS) yoxdur. Başlıqlar cümlə formasında.

## 3. Ölçülər

- Boşluq şkalası: 4, 6, 8, 10, 12, 14, 16, 20, 24.
- Radius: input və düymə 9–14 · kart və panel 14–18 · çip 20 · alt vərəq 24 · lövhə 6–12.
- Kölgə: web-də bir yüngül kölgə (`0 1px 2px` + `0 4px 14px`, 5%), tünd rejimdə yox. Mobildə üzən elementlərdə (`0 4px 16px`, 10%).
- Web sidebar 248 px, topbar 60 px, məzmun maks. 1440 px.
- Mobil toxunma hədəfi ≥ 44 pt, tab bar 84 pt.

## 4. Komponentlər

| Komponent | Variantlar | Qeyd |
|---|---|---|
| Button | primary, default, quiet, small, disabled | Mətn feillə başlayır: "Operatora göndər", "Çevir və köçür" |
| Plate (marşrut lövhəsi) | e (yaşıl), d (qırmızı), x (mavi, ekspress), m (metro), lg, xl | Rəqəm qalın, ağ |
| Status | ok, warn, bad, info, crowd, neytral | Nöqtə + mətn |
| Toggle chip (qat) | on/off | Web: çərçivə + checkbox; mobil: on = tünd fon |
| Segmented | 2–5 seçim | Tablar və filtrlər üçün |
| Switch | on/off | Ayarlar, avtomatik rejim |
| Panel | başlıq, meta, actions, body, flush | Səhifədə iki səviyyədən dərin iç-içə kart yoxdur |
| KPI | etiket, rəqəm, alt mətn (ok/bad) | Sətirdə 4 |
| Table | klik sətir, seçilmiş sətir, boş vəziyyət | Rəqəmlər sətirdə qırılmır |
| Drawer | başlıq, body, footer actions | Şikayət və hub təklifi |
| Modal | başlıq, body, 2 düymə | Paylaş |
| Toast | bir sətir | Hər hərəkətin nəticəsini deyir |
| PushBanner (mobil) | başlıq, mətn, hədəf | SSE bildirişi |
| BottomSheet (mobil) | sabit 40–50% | Xəritə ekranlarında |
| Occupancy (mobil) | Boş, Orta, Dolu | 3 insan ikonu |
| CoinBurst | +N | Coin qazananda |

## 5. Xəritə qaydaları

- Xəritə real OSM xəritəsidir, mockup-dakı SVG yalnız nümunədir. Panel, çiplər, legend və lent dizaynı mockup-dan götürülür; küçə adları Azərbaycanca (`name:az`, yoxdursa `name`), atribusiya aşağı sağda: "© OpenStreetMap contributors, OpenFreeMap".
- Web: MapLibre + OpenFreeMap `positron` (sakit, açıq). Tünd rejimdə tünd vektor stil (məs. MapTiler `dataviz-dark`, açarla) və ya açıq xəritə saxlanılır.
- Mobil: `react-native-maps` (iOS Apple, Android Google). Xəritə üzərində default yalnız: istifadəçi, dayanacaqlar, yaxın avtobuslar.
- Hər əlavə qat öz legend sətrini gətirir. Eyni anda iki qatdan çox açıqdırsa legend qısalır.
- Web canlı xəritədə **yalnız 2 avtobus** göstərilir (88 və 4).

## 6. Mətn qaydaları (UX copy)

- Sadə, aktiv, cümlə forması: "Şikayət göndər", "Sorğunu göndər", "Banka çevir".
- Eyni hərəkət eyni adla: düymə "Operatora göndər" → toast "Operatora göndərildi".
- Xəta: nə oldu + necə düzəlir. "Yolçu Coin kifayət etmir: 40 var, 60 lazımdır. Başqa ödəniş üsulu seçin."
- Boş vəziyyət: niyə boşdur + nə etmək olar. "Bu filtrə uyğun şikayət yoxdur. Filtrləri dəyişin."
- Rəqəmlər: minliklər boşluqla (12 480), onluq vergüllə (4,2), "dəq", "₼", "coin".
- Simulyasiya datası olan ekranda "Demo data" nişanı.
- Meta mətnləri vergüllə ayrılır, ortadakı nöqtə (·) ilə yox.

## 7. Hərəkət

- Yalnız istifadəçi hərəkətinə cavab: drawer açılması, toast, CoinBurst, yeni hadisənin qısa vurğusu, push banner.
- Canlı avtobus və naviqasiya animasiyası məzmunun özüdür.
- `prefers-reduced-motion`: hamısı söndürülür, mövqe birbaşa dəyişir.

## 8. Brend

**Fayllar** (`web/public/brand/`, SVG əsasdır, PNG yalnız SVG işləməyən yerdə):

| Fayl | Nə üçün | Harada |
|---|---|---|
| `logo-light.svg` | Tam loqo (nişan + "Yolçu" yazısı), açıq fon | Login (açıq rejim), OG şəkli olmayan yerlər |
| `logo-dark.svg` | Tam loqo, tünd fon (yazı ağ) | Login (tünd rejim), OG şəkli |
| `logo-icon.svg` | Nişan, ağ kvadrat fonla | Sidebar, avatar menyusu, bildiriş önizləməsi, `app/icon.svg` |
| `logo-icon-transparent.svg` | Nişan, fonsuz | Yalnız açıq və sakit fonda |
| `logo-icon-512.png` | Nişan, 512 px | Manifest ikonu |
| `favicon.ico` | 16/32 px | `app/favicon.ico` |

Kodda yalnız `web/components/Logo.tsx` istifadə olunur: `LogoMark` (nişan) və `LogoFull` (tam loqo; açıq/tünd fayl `.logo-on-light` / `.logo-on-dark` ilə tema token-lərinin seçiciləri üzrə dəyişir). Hər `img`-də `alt="Yolçu"`.

**Ölçü:**
- Nişan minimum 16 px (ekranda 24 px-dən kiçik olduqda yalnız nişan, yazılı loqo yox). Sidebar-da 32 px.
- Tam loqo minimum 96 px hündürlük: yazı ondan kiçikdə oxunmur (loqo şaquli, 508 × 626). Dar zolaqlarda (sidebar, topbar) tam loqo yox, nişan + mətn.

**Boş sahə:** loqonun hər tərəfində ən azı nişan hündürlüyünün 1/4-i qədər boş sahə; yanında başqa loqo, ikon və ya mətn bu sahəyə girməsin.

**Tünd fonda:** tam loqo üçün `logo-dark.svg`; nişan (`logo-icon.svg`) öz ağ kvadratı ilə hər iki rejimdə oxunur. Fonsuz nişanı tünd və ya rəngli fona qoyma.

**Etmə:** uzatma və ya sıxma (nisbət həmişə saxlanılır), rəngini dəyişmə və ya token rənginə boyama, kölgə, kontur, parıltı, qradiyent əlavə etmə, fırlatma, fotoşəkil üstünə qoyma, nişanı başqa forma ilə (dairə və s.) kəsmə.

**Rəng:** loqonun öz rəngləri qırmızı `#A10D1F`, yaşıl `#15562D`, tünd göy `#132142`. Bunlar UI token-ləri deyil (UI-nin əsas rəngi `--primary` `#0B6FCB`); token-lərə əlavə edilməyib, UI-də loqodan kənar istifadə olunmur.

**Ad:** UI-da məhsul adı **Yolçu**, coin-in adı **Yolçu Coin** (başlıq və izahlarda tam ad; sıx cədvəl xanalarında rəqəmin yanında qısa "coin" qala bilər, məs. "+5 coin"). Kod identifikatorları, fayl/qovluq adları, URL və e-poçtlar ASCII: `yolcu`. Coin ikonu: sarı dairədə "Y".

## 9. Mənbələr

Ölçü, rəng və davranış üçün həqiqət mənbəyi: `design/web_dashboard.html` və `design/mobile_app.html`. Onların `:root` token-ləri bu cədvəllə eynidir.
