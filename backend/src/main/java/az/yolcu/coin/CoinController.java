package az.yolcu.coin;

import az.yolcu.coin.dto.CoinRule;
import az.yolcu.coin.dto.CoinSummary;
import az.yolcu.coin.dto.ConversionCreated;
import az.yolcu.coin.dto.ConversionRequest;
import az.yolcu.coin.dto.ConversionRow;
import az.yolcu.coin.dto.Wallet;
import az.yolcu.common.PageResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Wallet (/me/wallet) and coin program admin (/admin/coins); roles are enforced in SecurityConfig. */
@RestController
@RequestMapping("/api/v1")
public class CoinController {
    private final CoinService coins;
    private final ConversionService conversions;

    public CoinController(CoinService coins, ConversionService conversions) {
        this.coins = coins;
        this.conversions = conversions;
    }

    @GetMapping("/me/wallet")
    public Wallet wallet(@AuthenticationPrincipal Long userId) {
        return coins.wallet(userId);
    }

    @PostMapping("/me/wallet/conversions")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversionCreated convert(@AuthenticationPrincipal Long userId, @RequestBody(required = false) ConversionRequest req) {
        return conversions.create(userId, req);
    }

    @GetMapping("/admin/coins/summary")
    public CoinSummary summary() {
        return coins.summary();
    }

    @GetMapping("/admin/coins/rules")
    public List<CoinRule> rules() {
        return coins.rules();
    }

    @GetMapping("/admin/coins/conversions")
    public PageResponse<ConversionRow> conversions(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return conversions.page(page, size);
    }
}
