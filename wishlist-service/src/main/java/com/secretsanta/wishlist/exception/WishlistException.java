package com.secretsanta.wishlist.exception;

import lombok.Getter;

@Getter
public class WishlistException extends RuntimeException {

    private final String errorCode;

    public WishlistException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static WishlistException unauthorized(String message) {
        return new WishlistException("WISHLIST_UNAUTHORIZED", message);
    }

    public static WishlistException forbidden(String message) {
        return new WishlistException("WISHLIST_FORBIDDEN", message);
    }

    public static WishlistException notFound(String errorCode, String message) {
        return new WishlistException(errorCode, message);
    }

    public static WishlistException conflict(String errorCode, String message) {
        return new WishlistException(errorCode, message);
    }

    public static WishlistException badRequest(String message) {
        return new WishlistException("WISHLIST_VALIDATION_FAILED", message);
    }
}
