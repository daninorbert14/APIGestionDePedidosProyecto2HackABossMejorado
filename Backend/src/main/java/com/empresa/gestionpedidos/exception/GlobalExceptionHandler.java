package com.empresa.gestionpedidos.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.List;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String,String>> handleResourceNotFound(ResourceNotFoundException ex){
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("Error: ", ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String,String>> handleOutOfStock(BadRequestException ex){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("Error: ", ex.getMessage()));
    }

    @ExceptionHandler(PedidoStateException.class)
    public ResponseEntity<Map<String,String>> handlePedidoState(PedidoStateException ex){
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("Error: ", ex.getMessage()));
    }

    // Captura los errores de @Valid
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {

        // getBindingResult() contiene todos los errores de validación encontrados.
        List<String> errores = ex.getBindingResult()
                // getFieldErrors() los filtra para obtener solo los errores de campos concretos
                .getFieldErrors()
                .stream()
                // Cada FieldError tiene el nombre del campo y el mensaje definido
                // en la anotación, por ejemplo: "nombre: El nombre no puede estar vacío"
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "Error", "Datos de entrada inválidos",
                "detalles", errores
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,String>> handleGlobalExceptions(Exception ex){
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("Error inesperado: ", ex.getMessage()));
    }
}
