package uk.gov.dwp.uc.pairtest.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.config.TicketServiceConfig;

/**
 * Goes through HTTP with the real TicketServiceImpl. Only the two thirdparty
 * services are mocked so we can check what gets sent to them.
 */
@WebMvcTest(TicketController.class)
@Import(TicketServiceConfig.class)
class TicketControllerTest {

    private static final String BOOKINGS_URL = "/cinema/bookings";

    // error messages, kept in step with TicketServiceImpl
    private static final String INVALID_ACCOUNT = "Invalid Account ID.";
    private static final String NO_TICKETS = "No tickets requested.";
    private static final String NULL_TICKET_REQUEST = "Ticket request must not be null.";
    private static final String MISSING_TICKET_TYPE = "Ticket type is required.";
    private static final String NEGATIVE_TICKET_COUNT = "Negative ticket counts are invalid.";
    private static final String ZERO_TICKETS = "Total ticket count is zero.";
    private static final String TOO_MANY_TICKETS = "Cannot purchase more than 25 tickets.";
    private static final String ADULT_REQUIRED = "Child or Infant tickets require an Adult ticket.";
    private static final String TOO_MANY_INFANTS =
            "Infants cannot outnumber Adults (lap seating restriction).";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketPaymentService paymentService;

    @MockitoBean
    private SeatReservationService seatReservationService;

    //Valid Booking - 201
    @Test
    void singleAdult_returns201_andChargesAndReserves() throws Exception {
        postBooking("""
                {"accountId": 250, "ticketRequests": [{"type": "ADULT", "ticketCount": 1}]}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(250));

        verify(paymentService).makePayment(250L, 25);
        verify(seatReservationService).reserveSeat(250L, 1);
    }

    @Test
    void mixedGroup_returns201_infantsFreeAndWithoutSeat() throws Exception {
        postBooking("""
                {"accountId": 250, "ticketRequests": [
                  {"type": "ADULT", "ticketCount": 2},
                  {"type": "CHILD", "ticketCount": 3},
                  {"type": "INFANT", "ticketCount": 1}
                ]}
                """)
                .andExpect(status().isCreated());

        verify(paymentService).makePayment(250L, 95);
        verify(seatReservationService).reserveSeat(250L, 5);
    }

    @Test
    void exactlyMaximumTickets_returns201() throws Exception {
        postBooking("""
                {"accountId": 250, "ticketRequests": [
                  {"type": "ADULT", "ticketCount": 12},
                  {"type": "CHILD", "ticketCount": 1},
                  {"type": "INFANT", "ticketCount": 12}
                ]}
                """)
                .andExpect(status().isCreated());

        verify(paymentService).makePayment(250L, 315);
        verify(seatReservationService).reserveSeat(250L, 13);
    }

    //Business Rule Bad requests - 400
    @Test
    void moreThanMaximumTickets_returns400() throws Exception {
        assertRejected("""
                {"accountId": 250, "ticketRequests": [{"type": "ADULT", "ticketCount": 26}]}
                """, TOO_MANY_TICKETS);
    }

    @Test
    void infantsCountTowardsMaximum_returns400() throws Exception {
        assertRejected("""
                {"accountId": 250, "ticketRequests": [
                  {"type": "ADULT", "ticketCount": 25},
                  {"type": "INFANT", "ticketCount": 1}
                ]}
                """, TOO_MANY_TICKETS);
    }

    @Test
    void childWithoutAdult_returns400() throws Exception {
        assertRejected("""
                {"accountId": 250, "ticketRequests": [{"type": "CHILD", "ticketCount": 3}]}
                """, ADULT_REQUIRED);
    }

    @Test
    void infantWithoutAdult_returns400() throws Exception {
        assertRejected("""
                {"accountId": 250, "ticketRequests": [{"type": "INFANT", "ticketCount": 1}]}
                """, ADULT_REQUIRED);
    }

    @Test
    void moreInfantsThanAdults_returns400() throws Exception {
        assertRejected("""
                {"accountId": 250, "ticketRequests": [
                  {"type": "ADULT", "ticketCount": 1},
                  {"type": "INFANT", "ticketCount": 2}
                ]}
                """, TOO_MANY_INFANTS);
    }

    //Invalid Input - 400
    @Test
    void zeroAccountId_returns400() throws Exception {
        assertRejected("""
                {"accountId": 0, "ticketRequests": [{"type": "ADULT", "ticketCount": 1}]}
                """, INVALID_ACCOUNT);
    }

    @Test
    void negativeAccountId_returns400() throws Exception {
        assertRejected("""
                {"accountId": -1, "ticketRequests": [{"type": "ADULT", "ticketCount": 1}]}
                """, INVALID_ACCOUNT);
    }

    @Test
    void missingAccountId_returns400() throws Exception {
        assertRejected("""
                {"ticketRequests": [{"type": "ADULT", "ticketCount": 1}]}
                """, INVALID_ACCOUNT);
    }

    @Test
    void missingTicketRequests_returns400() throws Exception {
        assertRejected("""
                {"accountId": 250}
                """, NO_TICKETS);
    }

    @Test
    void emptyTicketRequests_returns400() throws Exception {
        assertRejected("""
                {"accountId": 250, "ticketRequests": []}
                """, NO_TICKETS);
    }

    @Test
    void nullTicketRequestEntry_returns400() throws Exception {
        assertRejected("""
                {"accountId": 250, "ticketRequests": [null]}
                """, NULL_TICKET_REQUEST);
    }

    @Test
    void missingTicketType_returns400() throws Exception {
        assertRejected("""
                {"accountId": 250, "ticketRequests": [{"ticketCount": 1}]}
                """, MISSING_TICKET_TYPE);
    }

    @Test
    void negativeTicketCount_returns400() throws Exception {
        assertRejected("""
                {"accountId": 250, "ticketRequests": [{"type": "ADULT", "ticketCount": -5}]}
                """, NEGATIVE_TICKET_COUNT);
    }

    @Test
    void zeroTicketsInTotal_returns400() throws Exception {
        assertRejected("""
                {"accountId": 250, "ticketRequests": [{"type": "ADULT", "ticketCount": 0}]}
                """, ZERO_TICKETS);
    }

    @Test
    void unknownTicketType_returns400() throws Exception {
        postBooking("""
                {"accountId": 250, "ticketRequests": [{"type": "SENIOR", "ticketCount": 1}]}
                """)
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService, seatReservationService);
    }

    @Test
    void malformedJson_returns400() throws Exception {
        postBooking("{ this is not json")
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService, seatReservationService);
    }

    private ResultActions postBooking(String json) throws Exception {
        return mockMvc.perform(post(BOOKINGS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private void assertRejected(String json, String expectedDetail) throws Exception {
        postBooking(json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(expectedDetail));

        verifyNoInteractions(paymentService, seatReservationService);
    }
}
