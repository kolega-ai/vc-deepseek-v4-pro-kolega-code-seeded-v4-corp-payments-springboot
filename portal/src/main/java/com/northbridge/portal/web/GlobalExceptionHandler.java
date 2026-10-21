package com.northbridge.portal.web;

import com.northbridge.portal.client.PaymentApiException;
import com.northbridge.portal.security.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ModelAndView handlePortal(ApiException ex) {
        return switch (ex.getCode()) {
            case "NOT_FOUND" -> errorView(404, "Not found", ex.getMessage());
            case "FORBIDDEN" -> errorView(403, "Access denied", ex.getMessage());
            case "UNAUTHORIZED" -> errorView(401, "Authentication required", ex.getMessage());
            default -> errorView(400, "Request could not be processed", ex.getMessage());
        };
    }

    @ExceptionHandler(PaymentApiException.class)
    public ModelAndView handlePaymentApi(PaymentApiException ex) {
        if (ex.getStatus() == 404) {
            return errorView(404, "Not found",
                    "The record does not exist or belongs to a different company.");
        }
        if (ex.getStatus() == 403) {
            return errorView(403, "Access denied", ex.getMessage());
        }
        return errorView(ex.getStatus() >= 500 ? 502 : 400,
                ex.getStatus() >= 500 ? "Decision tier unavailable" : "Request could not be processed",
                ex.getMessage());
    }

    @ExceptionHandler(org.springframework.security.authorization.AuthorizationDeniedException.class)
    public ModelAndView handleDenied(org.springframework.security.authorization.AuthorizationDeniedException ex) {
        return errorView(403, "Access denied", "Your role does not permit this action.");
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleOther(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return errorView(500, "Something went wrong", "An unexpected error occurred. Please try again.");
    }

    private ModelAndView errorView(int status, String title, String message) {
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(HttpStatus.valueOf(status));
        mav.addObject("status", status);
        mav.addObject("title", title);
        mav.addObject("message", message);
        return mav;
    }
}
