package uk.gov.dwp.uc.pairtest;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import java.util.EnumMap;
import java.util.Map;

public class TicketServiceImpl implements TicketService {
    /**
     * Should only have private methods other than the one below.
     */

    static final int MAX_TICKETS_PER_PURCHASE = 25;

    static final String INVALID_ACCOUNT = "Invalid Account ID.";
    static final String NO_TICKETS = "No tickets requested.";
    static final String NULL_TICKET_REQUEST = "Ticket request must not be null.";
    static final String MISSING_TICKET_TYPE = "Ticket type is required.";
    static final String NEGATIVE_TICKET_COUNT = "Negative ticket counts are invalid.";
    static final String ZERO_TICKETS = "Total ticket count is zero.";
    static final String TOO_MANY_TICKETS =
            "Cannot purchase more than " + MAX_TICKETS_PER_PURCHASE + " tickets.";
    static final String ADULT_REQUIRED = "Child or Infant tickets require an Adult ticket.";
    static final String TOO_MANY_INFANTS =
            "Infants cannot outnumber Adults (lap seating restriction).";

    private static final Map<Type, Integer> TICKET_PRICES = Map.of(
            Type.ADULT, 25,
            Type.CHILD, 15,
            Type.INFANT, 0);

    private final TicketPaymentService ticketPaymentService;
    private final SeatReservationService seatReservationService;

    public TicketServiceImpl(TicketPaymentService ticketPaymentService,
                             SeatReservationService seatReservationService) {
        if (ticketPaymentService == null || seatReservationService == null) {
            throw new IllegalArgumentException("Payment and seat reservation services are required");
        }
        this.ticketPaymentService = ticketPaymentService;
        this.seatReservationService = seatReservationService;
    }

    @Override
    public void purchaseTickets(final Long accountId, final TicketTypeRequest... ticketTypeRequests)
            throws InvalidPurchaseException {

        validateAccountId(accountId);
        final Map<Type, Long> ticketCounts = countTicketsByType(ticketTypeRequests);
        validateBusinessRules(ticketCounts);

        // safe to narrow: the total has already been checked against the 25-ticket limit
        final int totalCost = (int) calculateTotalCost(ticketCounts);
        final int seatCount = (int) calculateSeatCount(ticketCounts);

        ticketPaymentService.makePayment(accountId, totalCost);
        seatReservationService.reserveSeat(accountId, seatCount);
    }

    private void validateAccountId(final Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new InvalidPurchaseException(INVALID_ACCOUNT);
        }
    }

    private Map<Type, Long> countTicketsByType(final TicketTypeRequest... ticketTypeRequests) {
        if (ticketTypeRequests == null || ticketTypeRequests.length == 0) {
            throw new InvalidPurchaseException(NO_TICKETS);
        }

        final Map<Type, Long> counts = new EnumMap<>(Type.class);
        for (Type type : Type.values()) {
            counts.put(type, 0L);
        }

        for (TicketTypeRequest request : ticketTypeRequests) {
            if (request == null) {
                throw new InvalidPurchaseException(NULL_TICKET_REQUEST);
            }
            if (request.getTicketType() == null) {
                throw new InvalidPurchaseException(MISSING_TICKET_TYPE);
            }
            if (request.getNoOfTickets() < 0) {
                throw new InvalidPurchaseException(NEGATIVE_TICKET_COUNT);
            }
            // counts are summed as long so huge values can't overflow past the limit
            counts.merge(request.getTicketType(), (long) request.getNoOfTickets(), Long::sum);
        }
        return counts;
    }

    private void validateBusinessRules(final Map<Type, Long> counts) {
        final long adults = counts.get(Type.ADULT);
        final long children = counts.get(Type.CHILD);
        final long infants = counts.get(Type.INFANT);
        final long totalTickets = adults + children + infants;

        if (totalTickets == 0) {
            throw new InvalidPurchaseException(ZERO_TICKETS);
        }
        if (totalTickets > MAX_TICKETS_PER_PURCHASE) {
            throw new InvalidPurchaseException(TOO_MANY_TICKETS);
        }
        if (adults == 0) {
            throw new InvalidPurchaseException(ADULT_REQUIRED);
        }
        if (infants > adults) {
            throw new InvalidPurchaseException(TOO_MANY_INFANTS);
        }
    }

    private long calculateTotalCost(final Map<Type, Long> counts) {
        return counts.entrySet().stream()
                .mapToLong(entry -> TICKET_PRICES.get(entry.getKey()) * entry.getValue())
                .sum();
    }

    private long calculateSeatCount(final Map<Type, Long> counts) {
        return counts.get(Type.ADULT) + counts.get(Type.CHILD);
    }
}
