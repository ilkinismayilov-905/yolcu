# 00 — Məhsul

## Problem

AYNA rəhbərliyinin qoyduğu iki problem:

1. **Nəqliyyat növləri təcrid olunub** — avtovağzaldan avtobusa və mikromobilliyə keçid zəifdir.
2. **Vahid idarəetmə mərkəzi yoxdur** — Mobility Hub-ları və çatışmayan infrastrukturu real vaxtda göstərən sistem yoxdur.

Ortaq kök: **keçid nöqtəsi və tələbat görünmür.** Sərnişin növbəti addımı görmür, AYNA isə harada, niyə və nə qədər problem olduğunu görmür.

## Həll

Bir məlumat qatı, iki interfeys, bir-birini qidalandıran dövrə:

- Sərnişin tətbiqi keçidi görünən edir (növbəti avtobus, doluluq, dayanacağa yol) və AYNA-ya siqnal göndərir (rəy, şikayət, sorğu).
- Dashboard bu siqnalları, kamera rəqəmlərini və Biletim.az datasını birləşdirib qərara çevirir (interval, avtobus bölgüsü, ekspress xətt).
- Qərarın nəticəsi yenidən sərnişinə qayıdır (bildiriş, daha qısa gözləmə).

## Rollar

| Rol | Interfeys | Demo hesabı |
|---|---|---|
| `PASSENGER` | Mobil | "Demo ilə daxil ol" düyməsi (Nigar Əliyeva) |
| `DISPATCHER` | Web | `dispatcher@yolcu.az` / `demo123` |
| `ADMIN` | Web | `admin@yolcu.az` / `demo123` |

## Modullar

| # | Modul | Web | Mobil |
|---|---|---|---|
| 1 | Canlı xəritə (tıxac, hub sıxlığı, avtobuslar, AI təklifi) | ✓ | ✓ sadələşdirilmiş |
| 2 | Hub kameraları (dayanacaqda insan sayı) | ✓ | sıxlıq qatı |
| 3 | Avtobus doluluğu + AI interval + topa yığılma | ✓ | növbəti avtobusun doluluğu |
| 4 | Avtobus bölgüsü (33 → 88) | ✓ | — |
| 5 | Avtovağzal + Biletim.az (gəlişlər, ekspress xətt) | ✓ | Biletim bileti, Avtovağzala yol |
| 6 | Marşrut planlayıcı + piyada naviqasiya | — | ✓ |
| 7 | Ödəniş (BakıKart, kart, coin) + səfər | — | ✓ |
| 8 | Rəy (Bolt tipli, səfərdən sonra) | operator reytinqinə düşür | ✓ |
| 9 | Şikayət (vasitəçisiz, AYNA-ya birbaşa) | ✓ bütün şikayətlər | ✓ göndər + status |
| 10 | Sorğular (hamıya bildiriş) | ✓ bütün sorğular + yeni | ✓ cavabla |
| 11 | Coin (qazan, gedişə xərclə, banka çevir) | ✓ proqram icmalı | ✓ cüzdan |
| 12 | Operatorlar və avtopark (BakuBus, Xaliq Faiqoğlu, Çinar-Trans, 6 Dost) | ✓ | — |
| 13 | İnteqrasiyalar (Biletim.az, AYNA MaaS, BakıKart, bank, YPX, kameralar) | ✓ status | — |
| 14 | Data analitika: OD matrisi, hub istilik matrisi, proqnoz, ödənişli yol namizədləri, datanın tətbiqi və satışı | ✓ | — |
| 15 | Süni tıxac → yeni hub təklifi (demo ssenarisi) | ✓ | — |
| 16 | Velo və skuter qatı: velo zolaqları, velo parkları, skuterlər (AYNA MaaS kimi) | — | ✓ |
| 17 | Səfər izləmə: harada, nə vaxt minib-düşdü + vərdişlərdən proaktiv yol təklifi | analitikaya düşür | ✓ |

Ekologiya bölməsi layihədən **çıxarılıb**.

## Səfər izləmə və proaktiv təklif

- İstifadəçi razılıq verirsə (`Səfərlərim` ekranında açar), tətbiq hər səfərin minmə dayanacağını, düşmə dayanacağını və vaxtlarını yadda saxlayır.
- Düşmə avtomatik aşkarlanır: telefon hədəf dayanacağın 80 m radiusunda, sürət 30 saniyə ərzində piyada səviyyəsindədir. Demo-da bu, səfər başlayandan 10 saniyə sonra baş verir. İstifadəçi "Düşdüm" ilə əl ilə də bitirə bilər.
- Eyni marşrut eyni saat aralığında (±45 dəq) və eyni həftə günü tipində (iş günü / həftəsonu) ən azı 3 dəfə təkrarlanıbsa, bu "vərdiş" sayılır və əsas ekranda "Sizin üçün" kartı göstərilir: "İş günləri bu saatda Nərimanova gedirsiniz, 88 6 dəqiqəyə gəlir".
- Anonim aqreqatlar (haradan-haraya, düşmə nöqtələri) web-dəki Data analitika səhifəsinə düşür.

## Coin modeli

- Coin real pul deyil. Sərnişin rəy (+5), sorğu (+5–20), şikayət (+5) və təsdiqlənmiş şikayət (+15) üçün qazanır.
- İstifadə: gediş haqqı (60 coin = 0,60 ₼) və ya partnyor bank vasitəsilə manata çevirmə (100 coin = 1 ₼, minimum 100).
- Demo-da bank inteqrasiyası simulyasiyadır: sorğu yaranır, 60 saniyə sonra "tamamlandı" olur.

## Əsas demo axını (pitch-in onurğası)

1. Sərnişin xəritədə Koroğlu tıxacını görür, "İş: Nərimanov" seçir.
2. Tətbiq 4 əvəzinə 88 təklif edir, Elmlər dayanacağına piyada yolu göstərir, naviqasiya başlayır.
3. Ödəyir (coin ilə), minir. Avtobusun doluluğu görünür.
4. "Düşdüm" → bildiriş → rəy → +5 coin.
5. Şikayət göndərir → **dashboard-da 1–2 saniyəyə görünür** → dispetçer "Operatora göndər" basır → sərnişinin telefonunda status dəyişir.
6. Dashboard-da 88-in 92% dolu olduğu görünür → AI 12 → 8 dəq təklif edir → dispetçer tətbiq edir.
7. Dispetçer sorğu göndərir → bütün telefonlara bildiriş düşür.
8. Dispetçer "Süni tıxac yarat" basır → Fətəli Xan Xoyski prospekti (Gənclik metrosunun yanı) qırmızıya çevrilir → sistem orada yeni hub təklif edir (səbəbləri, nə qurulmalı, təsiri).
9. Data analitika: haradan-haraya matrisi, proqnoz, ödənişli yol pilotu (Yusif Səfərov küç., 07:30–10:00, 1 ₼) və AYNA-nın datadan necə gəlir əldə edə biləcəyi.
