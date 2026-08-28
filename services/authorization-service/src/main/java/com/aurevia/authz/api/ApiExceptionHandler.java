package com.aurevia.authz.api;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice public class ApiExceptionHandler {
 @ExceptionHandler(OptimisticLockingFailureException.class) ResponseEntity<Map<String,String>> conflict(Exception e){return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code","OPTIMISTIC_LOCK_CONFLICT","message",e.getMessage()));}
}
