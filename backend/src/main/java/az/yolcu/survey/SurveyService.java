package az.yolcu.survey;

import az.yolcu.coin.CoinService;
import az.yolcu.common.ApiException;
import az.yolcu.common.AppClock;
import az.yolcu.common.PageResponse;
import az.yolcu.notification.NotificationService;
import az.yolcu.stream.StreamEvent;
import az.yolcu.survey.dto.AnswerRequest;
import az.yolcu.survey.dto.AnswerResult;
import az.yolcu.survey.dto.CreateSurveyRequest;
import az.yolcu.survey.dto.PassengerSurvey;
import az.yolcu.survey.dto.SimulateAnswersResult;
import az.yolcu.survey.dto.SurveyListItem;
import az.yolcu.survey.dto.SurveyResults;
import az.yolcu.user.Role;
import az.yolcu.user.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SurveyService {
    static final Map<String, String> AUDIENCES = Map.of("ALL", "Bütün istifadəçilər",
            "AVTOVAGZAL_2KM", "Avtovağzal ətrafı, 2 km", "ROUTE_88_4", "88 və 4 sərnişinləri");
    static final Set<Integer> REWARDS = Set.copyOf(CoinService.SURVEY_REWARDS);
    static final double AVTOVAGZAL_LAT = 40.4214, AVTOVAGZAL_LON = 49.7963; // mocks/places.json
    private static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private static final TypeReference<List<Long>> LONGS = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> ANSWERS = new TypeReference<>() {};

    private final SurveyRepository surveys;
    private final SurveyQuestionRepository questions;
    private final SurveyAnswerRepository answers;
    private final UserRepository users;
    private final CoinService coins;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;
    private final AppClock clock;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final Random rnd = new Random();

    public SurveyService(SurveyRepository surveys, SurveyQuestionRepository questions, SurveyAnswerRepository answers,
                         UserRepository users, CoinService coins, NotificationService notifications,
                         ApplicationEventPublisher events, AppClock clock, ObjectMapper mapper, JdbcTemplate jdbc) {
        this.surveys = surveys;
        this.questions = questions;
        this.answers = answers;
        this.users = users;
        this.coins = coins;
        this.notifications = notifications;
        this.events = events;
        this.clock = clock;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    // ---- admin ----

    @Transactional(readOnly = true)
    public PageResponse<SurveyListItem> list(String status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw ApiException.validation("page >= 0, size 1..100");
        var pageable = PageRequest.of(page, size, NEWEST);
        Page<Survey> p;
        if (status == null || status.isBlank()) p = surveys.findAll(pageable);
        else if (List.of(Survey.DRAFT, Survey.ACTIVE, Survey.CLOSED).contains(status)) p = surveys.findByStatus(status, pageable);
        else throw ApiException.validation("status: DRAFT, ACTIVE və ya CLOSED olmalıdır");

        var counts = new HashMap<Long, Long>();
        var ids = p.getContent().stream().map(Survey::getId).toList();
        if (!ids.isEmpty()) answers.countBySurveyIds(ids).forEach(r -> counts.put((Long) r[0], (Long) r[1]));
        return PageResponse.of(p, s -> item(s, counts.getOrDefault(s.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public SurveyResults results(long id) {
        var survey = find(id);
        var stored = answers.answersOf(id).stream().map(a -> read(a, ANSWERS)).toList();
        var qs = questions.findBySurveyIdOrderByOrdAsc(id).stream().map(q -> {
            var labels = read(q.getOptions(), STRINGS);
            var seed = q.getSeedCounts() == null ? List.<Long>of() : read(q.getSeedCounts(), LONGS);
            var key = q.getId().toString();
            var counts = new long[labels.size()];
            for (int i = 0; i < counts.length; i++) {
                var label = labels.get(i);
                counts[i] = (i < seed.size() ? seed.get(i) : 0)
                        + stored.stream().filter(a -> label.equals(a.get(key))).count();
            }
            var pct = percents(counts);
            long total = 0;
            for (long c : counts) total += c;
            var opts = IntStream.range(0, counts.length)
                    .mapToObj(i -> new SurveyResults.Option(labels.get(i), counts[i], pct[i])).toList();
            return new SurveyResults.Question(q.getId(), q.getText(), total, opts);
        }).toList();
        return new SurveyResults(id, survey.getSeedResponses() + stored.size(), qs);
    }

    public SurveyListItem create(CreateSurveyRequest req) {
        if (!REWARDS.contains(req.coinReward())) throw ApiException.validation("coinReward: 5, 10 və ya 20 olmalıdır");
        for (var q : req.questions()) {
            if (new HashSet<>(q.options().stream().map(String::trim).toList()).size() != q.options().size())
                throw ApiException.validation("options: variantlar təkrarlanmamalıdır");
        }
        var survey = surveys.save(new Survey(req.title().trim(), req.audience(), req.coinReward(), clock.now()));
        int ord = 0;
        for (var q : req.questions()) {
            questions.save(new SurveyQuestion(survey.getId(), ++ord, q.text().trim(),
                    write(q.options().stream().map(String::trim).toList())));
        }
        if (req.publish()) return publish(survey.getId());
        return item(survey, 0);
    }

    public SurveyListItem publish(long id) {
        var survey = find(id);
        if (!survey.getStatus().equals(Survey.DRAFT)) throw ApiException.validation("Yalnız qaralama göndərilə bilər");
        survey.publish(clock.now());
        notifications.createAll(audience(survey.getAudience()), "SURVEY", survey.getTitle(),
                "1 dəqiqəlik sorğu. Cavab verin, " + survey.getCoinReward() + " Yolçu Coin qazanın.", "survey:" + id);
        return updated(survey);
    }

    public SurveyListItem close(long id) {
        var survey = find(id);
        if (!survey.getStatus().equals(Survey.ACTIVE)) throw ApiException.validation("Yalnız aktiv sorğu bitirilə bilər");
        survey.close(clock.now());
        return updated(survey);
    }

    // ---- passenger ----

    @Transactional(readOnly = true)
    public PassengerSurvey forPassenger(long userId, long id) {
        var survey = find(id);
        if (survey.getStatus().equals(Survey.DRAFT)) throw ApiException.notFound("Sorğu tapılmadı");
        var qs = questions.findBySurveyIdOrderByOrdAsc(id).stream()
                .map(q -> new PassengerSurvey.Question(q.getId(), q.getText(), read(q.getOptions(), STRINGS))).toList();
        return new PassengerSurvey(id, survey.getTitle(), survey.getStatus(), survey.getCoinReward(),
                answers.existsBySurveyIdAndUserId(id, userId), qs);
    }

    public AnswerResult answer(long userId, long id, AnswerRequest req) {
        var survey = find(id);
        if (survey.getStatus().equals(Survey.DRAFT)) throw ApiException.notFound("Sorğu tapılmadı");
        // checked before ACTIVE so a repeat after closing still says ALREADY_DONE; unique(survey_id, user_id) is the race backstop
        if (answers.existsBySurveyIdAndUserId(id, userId)) throw ApiException.alreadyDone("Bu sorğunu artıq cavablandırmısınız");
        if (!survey.getStatus().equals(Survey.ACTIVE)) throw ApiException.validation("Sorğu bitib");

        var qs = questions.findBySurveyIdOrderByOrdAsc(id);
        var clean = new LinkedHashMap<String, String>();
        for (var q : qs) {
            var key = q.getId().toString();
            var value = req.answers().get(key);
            if (value == null) throw ApiException.validation("answers." + key + ": cavab verilməyib");
            if (!read(q.getOptions(), STRINGS).contains(value))
                throw ApiException.validation("answers." + key + ": belə variant yoxdur");
            clean.put(key, value);
        }
        if (req.answers().size() != clean.size()) throw ApiException.validation("answers: naməlum sual");

        answers.save(new SurveyAnswer(id, userId, write(clean), clock.now()));
        var label = "Sorğu: " + survey.getTitle();
        int awarded = coins.award(userId, survey.getCoinReward(), "SURVEY", "survey:" + id,
                label.length() > 120 ? label.substring(0, 119) + "…" : label);
        updated(survey);
        return new AnswerResult(awarded, users.coinBalance(userId));
    }

    // ---- demo ----

    /**
     * Answers from synthetic passengers (users.synthetic = true, created on demand, never notified, no coins).
     * Options are drawn with the question's seed counts as weights so percentages stay plausible.
     */
    public SimulateAnswersResult simulate(long id, int count) {
        if (count < 1 || count > 500) throw ApiException.validation("count: 1..500 olmalıdır");
        var survey = find(id);
        if (!survey.getStatus().equals(Survey.ACTIVE)) throw ApiException.validation("Yalnız aktiv sorğu");

        var userIds = surveys.syntheticWithoutAnswer(id, count);
        int missing = count - userIds.size();
        if (missing > 0) {
            jdbc.batchUpdate("insert into users (full_name, role, synthetic) values ('Sintetik sərnişin', 'PASSENGER', true)",
                    IntStream.range(0, missing).mapToObj(i -> new Object[0]).toList());
            userIds = surveys.syntheticWithoutAnswer(id, count);
        }
        var qs = questions.findBySurveyIdOrderByOrdAsc(id);
        var now = clock.now();
        var rows = new ArrayList<Object[]>();
        for (long userId : userIds) {
            var a = new LinkedHashMap<String, String>();
            for (var q : qs) a.put(q.getId().toString(), pick(q));
            rows.add(new Object[]{id, userId, write(a), now});
        }
        jdbc.batchUpdate("insert into survey_answers (survey_id, user_id, answers, created_at) values (?, ?, ?, ?)", rows);
        return new SimulateAnswersResult(rows.size(), updated(survey));
    }

    // ---- helpers ----

    /**
     * Largest-remainder rounding: floor every share, then give the leftover points to the largest fractions,
     * so percents of one question always sum to exactly 100 (all 0 when nobody answered).
     */
    static int[] percents(long[] counts) {
        long total = 0;
        for (long c : counts) total += c;
        var pct = new int[counts.length];
        if (total == 0) return pct;
        int sum = 0;
        for (int i = 0; i < counts.length; i++) sum += pct[i] = (int) (counts[i] * 100 / total);
        final long t = total;
        var order = IntStream.range(0, counts.length).boxed()
                .sorted(Comparator.comparingLong((Integer i) -> -(counts[i] * 100 % t)).thenComparing(i -> i)).toList();
        for (int k = 0; k < 100 - sum; k++) pct[order.get(k)]++;
        return pct;
    }

    private String pick(SurveyQuestion q) {
        var labels = read(q.getOptions(), STRINGS);
        var seed = q.getSeedCounts() == null ? List.<Long>of() : read(q.getSeedCounts(), LONGS);
        long total = seed.stream().mapToLong(Long::longValue).sum();
        if (seed.size() != labels.size() || total == 0) return labels.get(rnd.nextInt(labels.size()));
        long x = (long) (rnd.nextDouble() * total);
        for (int i = 0; i < labels.size(); i++) if ((x -= seed.get(i)) < 0) return labels.get(i);
        return labels.get(labels.size() - 1);
    }

    private List<Long> audience(String audience) {
        return switch (audience) {
            case "ROUTE_88_4" -> surveys.passengersOnRoutes(List.of("88", "4"));
            // shortcut: 2 km box around Avtovağzal on trip alighting points, upgrade to home/work zones once tracked
            case "AVTOVAGZAL_2KM" -> surveys.passengersAlightingIn(AVTOVAGZAL_LAT - 0.018, AVTOVAGZAL_LAT + 0.018,
                    AVTOVAGZAL_LON - 0.0236, AVTOVAGZAL_LON + 0.0236);
            default -> surveys.allPassengers();
        };
    }

    /** Pushes `survey.updated` (same JSON as a list row) to dispatchers and admins; returns that row. */
    private SurveyListItem updated(Survey s) {
        var item = item(s, answers.countBySurveyId(s.getId()));
        events.publishEvent(StreamEvent.toRole(Role.DISPATCHER, "survey.updated", item));
        events.publishEvent(StreamEvent.toRole(Role.ADMIN, "survey.updated", item));
        return item;
    }

    private SurveyListItem item(Survey s, long realAnswers) {
        return new SurveyListItem(s.getId(), s.getTitle(), s.getStatus(), s.getAudience(),
                AUDIENCES.getOrDefault(s.getAudience(), s.getAudience()), s.getSeedResponses() + realAnswers,
                s.getCoinReward(), AppClock.local(s.getCreatedAt()), AppClock.local(s.getPublishedAt()),
                AppClock.local(s.getClosedAt()));
    }

    private Survey find(long id) {
        return surveys.findById(id).orElseThrow(() -> ApiException.notFound("Sorğu tapılmadı"));
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Bad survey JSON: " + json, e);
        }
    }

    private String write(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
