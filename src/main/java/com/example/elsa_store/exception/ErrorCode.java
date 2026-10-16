package com.example.elsa_store.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(999, "Unexpected error", HttpStatus.INTERNAL_SERVER_ERROR),
    UN_AUTHENTICATED(1001, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    UN_AUTHORIZED(1002, "You do not have permission", HttpStatus.FORBIDDEN),
    INTERNAL_ERROR(1003, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(1004, "Invalid message key", HttpStatus.BAD_REQUEST),
    INVALID_USERNAME(1005, "Username must be at least {min} character", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(1006, "password must be al least {min} characters", HttpStatus.BAD_REQUEST),
    INVALID_DOB(1007, "Your age must be at least {min}", HttpStatus.BAD_REQUEST),
    INCORRECT_ACCOUNT_OR_PASSWORD(1008, "Incorrect account or password", HttpStatus.BAD_REQUEST),
    NOT_FOUND(1009, "Resource not found", HttpStatus.NOT_FOUND),
    BAD_REQUEST(1010, "Bad request", HttpStatus.BAD_REQUEST),
    STORE_NOT_APPROVED(1011, "Store is not approved yet", HttpStatus.FORBIDDEN),
    ADMIN_READ_ONLY(1012, "Admin can only view this data", HttpStatus.FORBIDDEN),
    REGISTRATION_NOT_PENDING(1013, "Registration is not waiting for review", HttpStatus.BAD_REQUEST),
    STORE_REQUIRED(1014, "This account does not belong to any store", HttpStatus.FORBIDDEN),
    AI_UNAVAILABLE(1015, "AI assistant is unavailable right now", HttpStatus.SERVICE_UNAVAILABLE),
    AI_RATE_LIMITED(1016, "Too many AI requests, try again in a few minutes", HttpStatus.TOO_MANY_REQUESTS),
    AI_OVERLOADED(1017, "AI model is overloaded, try again shortly", HttpStatus.SERVICE_UNAVAILABLE),
    AI_QUOTA_EXCEEDED(1018, "AI quota exceeded, try again later", HttpStatus.TOO_MANY_REQUESTS),
    DATA_IN_USE(1019, "Data is in use or already exists", HttpStatus.CONFLICT),
    CONCURRENT_UPDATE(1020, "Data was changed by someone else, please try again", HttpStatus.CONFLICT),
    TOO_MANY_LOGIN_ATTEMPTS(
            1021, "Too many failed login attempts, try again in 15 minutes", HttpStatus.TOO_MANY_REQUESTS),
    INCORRECT_CURRENT_PASSWORD(1022, "Current password is incorrect", HttpStatus.BAD_REQUEST),
    OUT_OF_STOCK(1023, "Not enough stock", HttpStatus.CONFLICT),
    INVALID_ORDER_TRANSITION(1024, "This order status change is not allowed", HttpStatus.BAD_REQUEST),
    PURCHASE_ORDER_LOCKED(1025, "Purchase order is no longer a draft", HttpStatus.CONFLICT),
    RESET_CODE_INVALID(1026, "Reset code is invalid or has expired", HttpStatus.BAD_REQUEST),
    TOO_MANY_RESET_REQUESTS(1027, "Too many password reset requests, try again later", HttpStatus.TOO_MANY_REQUESTS),
    REVIEW_NOT_ALLOWED(1028, "Only customers who received this product can review it", HttpStatus.FORBIDDEN),
    TOO_MANY_PENDING_QUESTIONS(
            1029, "Too many unanswered questions for this product, wait for the store to reply", HttpStatus.TOO_MANY_REQUESTS);

    private int code;
    private String message;
    private HttpStatusCode httpStatusCode;

    ErrorCode(int code, String message, HttpStatusCode httpStatusCode) {
        this.code = code;
        this.message = message;
        this.httpStatusCode = httpStatusCode;
    }
}
