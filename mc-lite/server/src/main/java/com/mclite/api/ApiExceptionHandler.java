package com.mclite.api;

import com.mclite.game.GameException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(GameException.class)
  public ResponseEntity<Dtos.ApiError> game(GameException e) {
    HttpStatus status = e.getCode().equals("NOT_FOUND") ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
    return ResponseEntity.status(status).body(new Dtos.ApiError(e.getCode(), e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Dtos.ApiError> invalid(MethodArgumentNotValidException e) {
    return ResponseEntity.badRequest().body(new Dtos.ApiError("BAD_REQUEST", "请求参数不对：" + e.getFieldError()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Dtos.ApiError> illegal(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(new Dtos.ApiError("BAD_REQUEST", e.getMessage()));
  }
}
