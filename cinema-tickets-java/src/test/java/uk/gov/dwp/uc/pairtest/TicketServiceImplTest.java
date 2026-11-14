package uk.gov.dwp.uc.pairtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.ADULT;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.CHILD;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.INFANT;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    private static final Long ACCOUNT_ID = 77L;

    @Mock
    private TicketPaymentService paymentService;

    @Mock
    private SeatReservationService seatReservationService;

    @InjectMocks
    private TicketServiceImpl ticketService;

    //Valid cases - purchase
    @Test
    void singleAdult_paysAdultPrice_andReservesOneSeat() {
        assertPurchase(25, 1, ticket(ADULT, 1));
    }

    @Test
    void mixedGroup_infantsPayNothing_andGetNoSeat() {
        assertPurchase(95, 5, ticket(ADULT, 2), ticket(CHILD, 3), ticket(INFANT, 1));
    }

    @Test
    void exactlyMaximumTickets_isAccepted() {
        assertPurchase(625, 25, ticket(ADULT, 25));
    }

    @Test
    void maximumTicketsIncludingInfants_isAccepted() {
        assertPurchase(315, 13, ticket(ADULT, 12), ticket(CHILD, 1), ticket(INFANT, 12));
    }

    @Test
    void infantsEqualToAdults_isAccepted() {
        assertPurchase(50, 2, ticket(ADULT, 2), ticket(INFANT, 2));
    }

    @Test
    void sameTypeRequestedTwice_isAddedTogether() {
        assertPurchase(65, 3, ticket(ADULT, 1), ticket(ADULT, 1), ticket(CHILD, 1));
    }

    @Test
    void typeWithZeroCount_isIgnoredWhenOtherTicketsAreValid() {
        assertPurchase(25, 1, ticket(ADULT, 1), ticket(CHILD, 0));
    }

    @Test
    void paymentIsRequestedBeforeSeatReservation() {
        ticketService.purchaseTickets(ACCOUNT_ID, ticket(ADULT, 1));

        InOrder order = inOrder(paymentService, seatReservationService);
        order.verify(paymentService).makePayment(ACCOUNT_ID, 25);
        order.verify(seatReservationService).reserveSeat(ACCOUNT_ID, 1);
    }

    //Constructor
    @Test
    void constructorRequiresBothServices() {
        assertThrows(IllegalArgumentException.class,
                () -> new TicketServiceImpl(null, seatReservationService));
        assertThrows(IllegalArgumentException.class,
                () -> new TicketServiceImpl(paymentService, null));
    }

    //Invalid account
    @Test
    void nullAccountId_isRejected() {
        assertRejected(null, TicketServiceImpl.INVALID_ACCOUNT, ticket(ADULT, 1));
    }

    @Test
    void zeroAccountId_isRejected() {
        assertRejected(0L, TicketServiceImpl.INVALID_ACCOUNT, ticket(ADULT, 1));
    }

    @Test
    void negativeAccountId_isRejected() {
        assertRejected(-10L, TicketServiceImpl.INVALID_ACCOUNT, ticket(ADULT, 1));
    }

    //Invalid request for tickets
    @Test
    void noTicketRequests_isRejected() {
        assertRejected(ACCOUNT_ID, TicketServiceImpl.NO_TICKETS);
    }

    @Test
    void nullTicketRequestArray_isRejected() {
        assertRejected(ACCOUNT_ID, TicketServiceImpl.NO_TICKETS, (TicketTypeRequest[]) null);
    }

    @Test
    void nullTicketRequestEntry_isRejected() {
        assertRejected(ACCOUNT_ID, TicketServiceImpl.NULL_TICKET_REQUEST, ticket(ADULT, 1), null);
    }

    @Test
    void missingTicketType_isRejected() {
        assertRejected(ACCOUNT_ID, TicketServiceImpl.MISSING_TICKET_TYPE, ticket(null, 1));
    }

    @Test
    void negativeTicketCount_isRejected() {
        assertRejected(ACCOUNT_ID, TicketServiceImpl.NEGATIVE_TICKET_COUNT, ticket(ADULT, -5));
    }

    @Test
    void zeroTicketsInTotal_isRejected() {
        assertRejected(ACCOUNT_ID, TicketServiceImpl.ZERO_TICKETS, ticket(ADULT, 0), ticket(CHILD, 0));
    }

    @Test
    void moreThanMaximumTickets_isRejected() {
        assertRejected(ACCOUNT_ID, TicketServiceImpl.TOO_MANY_TICKETS, ticket(ADULT, 26));
    }

    @Test
    void infantsCountTowardsMaximum() {
        assertRejected(ACCOUNT_ID, TicketServiceImpl.TOO_MANY_TICKETS,
                ticket(ADULT, 13), ticket(INFANT, 13));
    }

    @Test
    void hugeCountsDoNotOverflowPastTheLimit() {
        assertRejected(ACCOUNT_ID, TicketServiceImpl.TOO_MANY_TICKETS,
                ticket(ADULT, Integer.MAX_VALUE), ticket(ADULT, Integer.MAX_VALUE), ticket(ADULT, 2));
    }

    @Test
    void childWithoutAdult_isRejected() {
        assertRejected(ACCOUNT_ID, TicketServiceImpl.ADULT_REQUIRED, ticket(CHILD, 2));
    }

    @Test
    void infantWithoutAdult_isRejected() {
        assertRejected(ACCOUNT_ID, TicketServiceImpl.ADULT_REQUIRED, ticket(INFANT, 1));
    }

    @Test
    void childAndInfantWithoutAdult_isRejected() {
        assertRejected(ACCOUNT_ID, TicketServiceImpl.ADULT_REQUIRED,
                ticket(CHILD, 1), ticket(INFANT, 1));
    }

    @Test
    void moreInfantsThanAdults_isRejected() {
        assertRejected(ACCOUNT_ID, TicketServiceImpl.TOO_MANY_INFANTS,
                ticket(ADULT, 1), ticket(INFANT, 2));
    }

    private static TicketTypeRequest ticket(Type type, int count) {
        return new TicketTypeRequest(type, count);
    }

    private void assertPurchase(int expectedCost, int expectedSeats, TicketTypeRequest... requests) {
        ticketService.purchaseTickets(ACCOUNT_ID, requests);

        InOrder order = inOrder(paymentService, seatReservationService);
        order.verify(paymentService).makePayment(ACCOUNT_ID, expectedCost);
        order.verify(seatReservationService).reserveSeat(ACCOUNT_ID, expectedSeats);
    }

    private void assertRejected(Long accountId, String expectedMessage, TicketTypeRequest... requests) {
        InvalidPurchaseException ex = assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(accountId, requests));

        assertEquals(expectedMessage, ex.getMessage());
        verifyNoInteractions(paymentService, seatReservationService);
    }
}
