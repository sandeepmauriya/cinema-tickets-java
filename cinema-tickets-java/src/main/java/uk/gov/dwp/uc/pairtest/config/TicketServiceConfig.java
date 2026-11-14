package uk.gov.dwp.uc.pairtest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.paymentgateway.TicketPaymentServiceImpl;
import thirdparty.seatbooking.SeatReservationService;
import thirdparty.seatbooking.SeatReservationServiceImpl;
import uk.gov.dwp.uc.pairtest.TicketService;
import uk.gov.dwp.uc.pairtest.TicketServiceImpl;

/**
 * Wires things up with @Bean methods rather than annotations, because the
 * thirdparty classes can't be changed and the service stays a plain class.
 */
@Configuration
public class TicketServiceConfig {

    @Bean
    public TicketPaymentService ticketPaymentService() {
        return new TicketPaymentServiceImpl();
    }

    @Bean
    public SeatReservationService seatReservationService() {
        return new SeatReservationServiceImpl();
    }

    @Bean
    public TicketService ticketService(TicketPaymentService ticketPaymentService,
                                       SeatReservationService seatReservationService) {
        return new TicketServiceImpl(ticketPaymentService, seatReservationService);
    }

}
