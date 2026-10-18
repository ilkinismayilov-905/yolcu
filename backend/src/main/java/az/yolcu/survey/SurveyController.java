package az.yolcu.survey;

import az.yolcu.common.PageResponse;
import az.yolcu.survey.dto.AnswerRequest;
import az.yolcu.survey.dto.AnswerResult;
import az.yolcu.survey.dto.CreateSurveyRequest;
import az.yolcu.survey.dto.PassengerSurvey;
import az.yolcu.survey.dto.SimulateAnswersResult;
import az.yolcu.survey.dto.SurveyListItem;
import az.yolcu.survey.dto.SurveyResults;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin (/admin/surveys), passenger (/me/surveys) and demo endpoints; roles are enforced in SecurityConfig. */
@RestController
@RequestMapping("/api/v1")
public class SurveyController {
    private final SurveyService service;

    public SurveyController(SurveyService service) {
        this.service = service;
    }

    @GetMapping("/admin/surveys")
    public PageResponse<SurveyListItem> list(@RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.list(status, page, size);
    }

    @GetMapping("/admin/surveys/{id}/results")
    public SurveyResults results(@PathVariable long id) {
        return service.results(id);
    }

    @PostMapping("/admin/surveys")
    @ResponseStatus(HttpStatus.CREATED)
    public SurveyListItem create(@Valid @RequestBody CreateSurveyRequest req) {
        return service.create(req);
    }

    @PostMapping("/admin/surveys/{id}/publish")
    public SurveyListItem publish(@PathVariable long id) {
        return service.publish(id);
    }

    @PostMapping("/admin/surveys/{id}/close")
    public SurveyListItem close(@PathVariable long id) {
        return service.close(id);
    }

    @GetMapping("/me/surveys/{id}")
    public PassengerSurvey get(@AuthenticationPrincipal Long userId, @PathVariable long id) {
        return service.forPassenger(userId, id);
    }

    @PostMapping("/me/surveys/{id}/answers")
    public AnswerResult answer(@AuthenticationPrincipal Long userId, @PathVariable long id,
                               @Valid @RequestBody AnswerRequest req) {
        return service.answer(userId, id, req);
    }

    @PostMapping("/demo/surveys/{id}/simulate-answers")
    public SimulateAnswersResult simulate(@PathVariable long id, @RequestParam(defaultValue = "50") int count) {
        return service.simulate(id, count);
    }
}
