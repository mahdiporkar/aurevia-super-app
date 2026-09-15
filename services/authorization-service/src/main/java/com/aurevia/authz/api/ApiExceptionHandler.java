package com.aurevia.authz.api;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice public class ApiExceptionHandler {
 @ExceptionHandler(OptimisticLockingFailureException.class) ResponseEntity<Map<String,String>> conflict(Exception e){return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code","OPTIMISTIC_LOCK_CONFLICT","message",e.getMessage()));}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<Map<String,String>> integrity(Exception e){return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code","DATA_CONFLICT","message","The requested change conflicts with existing data"));}
 @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<Map<String,String>> badRequest(Exception e){return ResponseEntity.badRequest().body(Map.of("code","INVALID_REQUEST","message",e.getMessage()));}
}
