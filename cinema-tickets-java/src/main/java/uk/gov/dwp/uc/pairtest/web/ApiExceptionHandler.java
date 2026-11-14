package uk.gov.dwp.uc.pairtest.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidPurchaseException.class)
    public ProblemDetail handleInvalidPurchaseException(final InvalidPurchaseException ex) {
        final ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setDetail(ex.getMessage());
        return problemDetail;
    }

}
