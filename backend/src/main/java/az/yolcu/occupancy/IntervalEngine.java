package az.yolcu.occupancy;

/**
 * BE-8.1: interval engine (02_ARCHITECTURE §7). Pure math, a line-by-line port of packages/shared/src/headway.ts:
 * same operation order and rounding (sat values rounded to 1 decimal before the delta), so Java and TS agree.
 */
public final class IntervalEngine {
    public static final int TARGET_OCCUPANCY = 65;
    public static final int MIN_INTERVAL = 6;
    public static final int MAX_INTERVAL = 16;

    private IntervalEngine() {}

    /** headway.ts HeadwayResult. occupancy is a rounded %, sat values have 1 decimal. */
    public record Result(int intervalMin, int occupancy, double waitMin, int busesNeeded, double satNow, double satAfter,
                         double satDelta) {}

    public static double occNew(double occ, int currentIv, int iv) { return Math.min(130, (occ * iv) / currentIv); }

    public static double waitMin(int iv) { return iv / 2.0; }

    public static int busesNeeded(int busCount, int currentIv, int iv) { return (int) Math.ceil((double) (busCount * currentIv) / iv); }

    public static double sat(double occ, double wait) {
        return clamp(2.5, 4.8, 4.6 - Math.max(0, occ - 65) * 0.022 - Math.max(0, wait - 3) * 0.08);
    }

    public static int recommendedInterval(double occ, int currentIv) {
        int raw = (int) clamp(MIN_INTERVAL, MAX_INTERVAL, Math.floor((currentIv * TARGET_OCCUPANCY) / occ));
        return Math.abs(raw - currentIv) <= 1 ? currentIv : raw;
    }

    public static Result simulate(double occ, int currentIv, int busCount) {
        return simulate(occ, currentIv, busCount, recommendedInterval(occ, currentIv));
    }

    public static Result simulate(double occ, int currentIv, int busCount, int iv) {
        double o = occNew(occ, currentIv, iv);
        double satNow = round1(sat(occ, waitMin(currentIv)));
        double satAfter = round1(sat(o, waitMin(iv)));
        return new Result(iv, (int) Math.round(o), waitMin(iv), busesNeeded(busCount, currentIv, iv), satNow, satAfter,
                round1(satAfter - satNow));
    }

    static double round1(double x) { return Math.round(x * 10) / 10.0; }

    private static double clamp(double lo, double hi, double x) { return Math.min(hi, Math.max(lo, x)); }
}
