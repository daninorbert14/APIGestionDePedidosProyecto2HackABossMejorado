package com.empresa.gestionpedidos.service;

import com.empresa.gestionpedidos.dto.CrearTerminalDto;
import com.empresa.gestionpedidos.dto.TerminalDto;
import com.empresa.gestionpedidos.exception.PedidoStateException;
import com.empresa.gestionpedidos.exception.ResourceNotFoundException;
import com.empresa.gestionpedidos.model.Terminal;
import com.empresa.gestionpedidos.repository.TerminalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TerminalService {
    private final TerminalRepository terminalRepository;

    //Método para listar todas las terminales
    public List<TerminalDto> listarTerminales() {
        return terminalRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    //Método para buscar una terminal mediante su Id
    public TerminalDto buscarTerminalPorId(Long id) {
        Terminal terminal = terminalRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("La terminal con id: " + id + " no existe"));
        return toDto(terminal);
    }

    //Método para guardar una nueva terminal
    //Recibe un dto y devuelve otro de salida
    public TerminalDto guardaTerminal(CrearTerminalDto nuevaTerminalDto) {
        // Evita terminales con el mismo nombre
        if (terminalRepository.existsByNombre(nuevaTerminalDto.getNombre())) {
            throw new PedidoStateException("Ya existe una terminal con el nombre: " + nuevaTerminalDto.getNombre());
        }

        Terminal terminal = new Terminal();
        terminal.setNombre(nuevaTerminalDto.getNombre());
        Terminal guardada = terminalRepository.save(terminal);
        return toDto(guardada);
    }


    // *** MÉTODO DE MAPEO ***
    private TerminalDto toDto(Terminal terminal) {
        TerminalDto dto = new TerminalDto();
        dto.setId(terminal.getId());
        dto.setNombre(terminal.getNombre());
        return dto;
    }

}
