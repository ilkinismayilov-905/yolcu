// Sidebar from design/web_dashboard.html (NAV + ICON). `desc` is the page subtitle from each <section class="page">.
// ICON: raw inner SVG markup copied verbatim from the mockup (static, trusted).
export interface NavItem {
  href: string;
  label: string;
  icon: keyof typeof ICON;
  desc: string;
  badge?: 'reports';
}

export const ICON = {
  map: '<path d="M9 4 3 6.5v13.5l6-2.5 6 2.5 6-2.5V4l-6 2.5z"/><path d="M9 4v13.5M15 6.5V20"/>',
  cam: '<path d="M3 8h3l2-3h8l2 3h3v11H3z"/><circle cx="12" cy="13" r="3.5"/>',
  bus: '<rect x="4" y="3" width="16" height="15" rx="3"/><path d="M4 11h16M8 21v-3M16 21v-3"/><circle cx="8" cy="14.5" r="1"/><circle cx="16" cy="14.5" r="1"/>',
  split: '<path d="M7 4v16M17 4v16M7 9h10M7 15h10"/>',
  term: '<path d="M3 20h18M5 20V9l7-5 7 5v11"/><path d="M10 20v-5h4v5"/>',
  chat: '<path d="M4 5h16v11H9l-5 4z"/>',
  poll: '<path d="M5 20V10M12 20V4M19 20v-7"/>',
  coin: '<circle cx="12" cy="12" r="8"/><path d="M12 8v8M9.5 10h4a1.5 1.5 0 0 1 0 3h-3a1.5 1.5 0 0 0 0 3h4"/>',
  ops: '<path d="M4 20h16M6 20V8l6-4 6 4v12"/><path d="M10 12h4M10 16h4"/>',
  plug: '<path d="M9 3v5M15 3v5M7 8h10v4a5 5 0 0 1-10 0zM12 17v4"/>',
  chart: '<path d="M4 20h16"/><path d="M6 16l4-5 3 3 5-7"/>',
} as const;

export const NAV: { group: string; items: NavItem[] }[] = [
  {
    group: 'Canlı',
    items: [
      { href: '/map', label: 'Canlı xəritə', icon: 'map', desc: 'Şəhərin indiki vəziyyəti: yollarda tıxac, hub-larda insan sıxlığı və xətdəki avtobuslar.' },
      { href: '/hubs', label: 'Hub kameraları', icon: 'cam', desc: 'Dayanacaq və hub-larda gözləyən insan sayı. Kamera insanları sayır, amma tanımır: mərkəzə yalnız rəqəm gəlir.' },
      { href: '/occupancy', label: 'Avtobus doluluğu', icon: 'bus', desc: 'Avtobus daxili kameralar hər 30 saniyədən bir sərnişin sayını göndərir. AI bu rəqəmə görə intervalı tənzimləməyi təklif edir.' },
    ],
  },
  {
    group: 'Planlaşdırma',
    items: [
      { href: '/split', label: 'Avtobus bölgüsü', icon: 'split', desc: 'Boş gedən marşrutdan avtobusu müvəqqəti olaraq dolu gedən marşruta keçirmək təklifi.' },
      { href: '/terminal', label: 'Avtovağzal və Biletim.az', icon: 'term', desc: 'Biletim.az-da satılan biletlər Avtovağzala neçə sərnişinin gələcəyini dəqiq göstərir. Buna görə şəhərə çıxış üçün ekspress xətlər planlanır.' },
    ],
  },
  {
    group: 'Analitika',
    items: [
      { href: '/analytics', label: 'Data analitika', icon: 'chart', desc: 'Yolçunun topladığı data AYNA üçün haraya tətbiq olunur: axın matrisləri, tələbat proqnozu, ödənişli yol namizədləri və data məhsulları.' },
    ],
  },
  {
    group: 'Sərnişinlər',
    items: [
      { href: '/reports', label: 'Şikayətlər', icon: 'chat', badge: 'reports', desc: 'Mobil tətbiqdən birbaşa gələn bütün şikayətlər. Sətrə klikləyin: detallara baxın, operatora göndərin və ya statusu dəyişin.' },
      { href: '/surveys', label: 'Sorğular', icon: 'poll', desc: 'Bütün istifadəçilərə və ya seçilmiş qrupa bildiriş kimi sorğu göndərin. Cavab verənlər Yolçu Coin qazanır.' },
      { href: '/coins', label: 'Yolçu Coin', icon: 'coin', desc: 'Sərnişinlər rəy, sorğu və şikayət üçün Yolçu Coin qazanır. Yolçu Coin real pul deyil: partnyor bank vasitəsilə manata çevrilir və ya gediş haqqına xərclənir.' },
    ],
  },
  {
    group: 'Tərəfdaşlar',
    items: [
      { href: '/operators', label: 'Operatorlar', icon: 'ops', desc: 'Hər operatorun bütün avtobusları, statusu, doluluğu və sərnişin rəyləri. Operatoru seçin və ya bort nömrəsi ilə axtarın.' },
      { href: '/integrations', label: 'İnteqrasiyalar', icon: 'plug', desc: 'Yolçunun məlumat aldığı və ya göndərdiyi sistemlər. Demo-da simulyasiya olunanlar ayrıca qeyd edilib.' },
    ],
  },
];

export const navItem = (href: string): NavItem => {
  const it = NAV.flatMap((g) => g.items).find((i) => i.href === href);
  if (!it) throw new Error(`Unknown nav item ${href}`);
  return it;
};
