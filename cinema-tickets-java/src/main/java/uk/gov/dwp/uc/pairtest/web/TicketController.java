package uk.gov.dwp.uc.pairtest.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.dwp.uc.pairtest.TicketService;

@RestController
@RequestMapping("/cinema")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingConfirmation book(@RequestBody PurchaseRequest request) {
        ticketService.purchaseTickets(request.accountId(), request.toTicketTypeRequests());
        return new BookingConfirmation(request.accountId());
    }

}
