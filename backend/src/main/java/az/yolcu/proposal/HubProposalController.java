package az.yolcu.proposal;

import az.yolcu.proposal.dto.HubProposal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/hub-proposals")
public class HubProposalController {
    private final HubProposalService proposals;

    public HubProposalController(HubProposalService proposals) {
        this.proposals = proposals;
    }

    @GetMapping
    public List<HubProposal> list(@RequestParam(required = false) String status) { return proposals.list(status); }

    @PostMapping("/{id}/accept")
    public HubProposal accept(@PathVariable long id) { return proposals.accept(id); }

    @PostMapping("/{id}/reject")
    public HubProposal reject(@PathVariable long id) { return proposals.reject(id); }
}
