package com.empresa.gestionpedidos.controller;

import com.empresa.gestionpedidos.dto.CrearTerminalDto;
import com.empresa.gestionpedidos.dto.TerminalDto;
import com.empresa.gestionpedidos.service.TerminalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/terminales")
@RequiredArgsConstructor // Lombok genera el constructor con los campos final
public class TerminalController {
    private final TerminalService terminalService;

    @GetMapping
    public ResponseEntity<List<TerminalDto>> listarTerminales() {
        return ResponseEntity.ok(terminalService.listarTerminales());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TerminalDto> terminalPorId(@PathVariable Long id) {
        return ResponseEntity.ok(terminalService.buscarTerminalPorId(id));
    }

    @PostMapping
    public ResponseEntity<TerminalDto> crearTerminal(@Valid @RequestBody CrearTerminalDto nuevaTerminalDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(terminalService.guardaTerminal(nuevaTerminalDto));
    }
}
