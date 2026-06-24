package com.secretsanta.wishlist.controller;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.secretsanta.wishlist.dto.WishlistErrorResponse;
import com.secretsanta.wishlist.exception.WishlistException;

@RestControllerAdvice
public class WishlistExceptionHandler {

    @ExceptionHandler(WishlistException.class)
    ResponseEntity<WishlistErrorResponse> handleWishlistException(WishlistException exception) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(new WishlistErrorResponse(exception.getErrorCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<WishlistErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new WishlistErrorResponse("WISHLIST_VALIDATION_FAILED", message));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class
    })
    ResponseEntity<WishlistErrorResponse> handleBadRequest(Exception exception) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new WishlistErrorResponse("WISHLIST_VALIDATION_FAILED", exception.getMessage()));
    }
}
