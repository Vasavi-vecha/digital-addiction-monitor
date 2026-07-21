package com.digitaladdictionmonitor.backend.exception;

import com.digitaladdictionmonitor.backend.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The MethodArgumentNotValidException / UserNotFoundException /
 * MlServiceUnavailableException branches are exercised more realistically
 * through MockMvc in LogSyncControllerTest and DashboardControllerTest. The
 * two branches below (IllegalArgumentException, generic Exception) aren't
 * naturally reachable through either real controller, so they're unit tested
 * directly here against fabricated exceptions.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void illegalArgumentExceptionMapsToBadRequest() {
        when(request.getRequestURI()).thenReturn("/api/some/path");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(
                new IllegalArgumentException("bad input"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getError()).isEqualTo("Bad Request");
        assertThat(body.getMessage()).isEqualTo("bad input");
        assertThat(body.getPath()).isEqualTo("/api/some/path");
        assertThat(body.getDetails()).isEmpty();
    }

    @Test
    void genericExceptionMapsToInternalServerError() {
        when(request.getRequestURI()).thenReturn("/api/dashboard/u0001");

        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(
                new RuntimeException("something broke"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getError()).isEqualTo("Internal Server Error");
        assertThat(body.getMessage()).isEqualTo("Unexpected error: something broke");
        assertThat(body.getPath()).isEqualTo("/api/dashboard/u0001");
    }
}
