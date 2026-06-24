package com.secretsanta.wishlist.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class WishlistException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public WishlistException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static WishlistException unauthorized(String message) {
        return new WishlistException(HttpStatus.UNAUTHORIZED, "WISHLIST_UNAUTHORIZED", message);
    }

    public static WishlistException forbidden(String message) {
        return new WishlistException(HttpStatus.FORBIDDEN, "WISHLIST_FORBIDDEN", message);
    }

    public static WishlistException notFound(String errorCode, String message) {
        return new WishlistException(HttpStatus.NOT_FOUND, errorCode, message);
    }

    public static WishlistException conflict(String errorCode, String message) {
        return new WishlistException(HttpStatus.CONFLICT, errorCode, message);
    }

    public static WishlistException badRequest(String message) {
        return new WishlistException(HttpStatus.BAD_REQUEST, "WISHLIST_VALIDATION_FAILED", message);
    }
}
