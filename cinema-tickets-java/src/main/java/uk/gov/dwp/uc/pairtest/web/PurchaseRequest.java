package uk.gov.dwp.uc.pairtest.web;

import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;

import java.util.List;

public record PurchaseRequest(Long accountId, List<TicketRequest> ticketRequests) {

    public record TicketRequest(TicketTypeRequest.Type type, int ticketCount) {
    }

    TicketTypeRequest[] toTicketTypeRequests() {
        if (ticketRequests == null) {
            return new TicketTypeRequest[0];
        }
        // null entries are passed through as null so the service rejects them
        return ticketRequests.stream()
                .map(r -> r == null ? null : new TicketTypeRequest(r.type(), r.ticketCount()))
                .toArray(TicketTypeRequest[]::new);
    }
}
