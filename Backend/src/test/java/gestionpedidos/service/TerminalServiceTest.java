package gestionpedidos.service;

import gestionpedidos.dto.CrearTerminalDto;
import gestionpedidos.dto.TerminalDto;
import gestionpedidos.exception.PedidoStateException;
import gestionpedidos.exception.ResourceNotFoundException;
import gestionpedidos.model.Terminal;
import gestionpedidos.repository.TerminalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TerminalServiceTest {

    @Mock
    private TerminalRepository terminalRepository;

    @InjectMocks
    private TerminalService terminalService;

    // *** MÉTODOS FACTORÍA ***

    private Terminal crearTerminal(Long id, String nombre) {
        Terminal terminal = new Terminal();
        terminal.setId(id);
        terminal.setNombre(nombre);
        return terminal;
    }

    private CrearTerminalDto crearTerminalDto(String nombre) {
        CrearTerminalDto crearTerminalDto = new CrearTerminalDto();
        crearTerminalDto.setNombre(nombre);
        return crearTerminalDto;
    }

    // *** TESTS ***

    // Test del método listarTerminales
    @Test
    void listarTerminalesDeberiaDevolverTodas() {
        // Arrange
        Terminal terminal1 = crearTerminal(1L, "Terminal-1");
        Terminal terminal2 = crearTerminal(2L, "Terminal-2");

        when(terminalRepository.findAll()).thenReturn(List.of(terminal1, terminal2));

        // Act
        List<TerminalDto> resultado = terminalService.listarTerminales();

        // Assert
        // Nos aseguramos de que devuelva todas las terminales disponibles (2)
        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).getId()).isEqualTo(terminal1.getId());
        assertThat(resultado.get(1).getId()).isEqualTo(terminal2.getId());
    }


    // Test caso de éxito del método buscarTerminalPorId
    @Test
    void buscarTerminalPorIdDeberiaDevolverlaCuandoExiste() {
        Terminal terminal = crearTerminal(1L, "Terminal-1");

        when(terminalRepository.findById(terminal.getId())).thenReturn(Optional.of(terminal));

        TerminalDto resultado = terminalService.buscarTerminalPorId(terminal.getId());

        assertThat(resultado.getId()).isEqualTo(terminal.getId());
    }


    // Test caso de error del método buscarTerminalPorId. Terminal no encontrada
    @Test
    void buscarTerminalPorIdDeberiaLanzarExcepcionCuandoNoLaEncuentra() {
        Long terminalId = 1L;

        when(terminalRepository.findById(terminalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> terminalService.buscarTerminalPorId(terminalId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("La terminal con ID: " + terminalId + " no existe");
    }

    // Test caso de éxito del método guardarTerminal
    @Test
    void guardarTerminalDeberiaGuardarlaCuandoElNombreNoExiste() {
        CrearTerminalDto dto = crearTerminalDto("Terminal-1");

        when(terminalRepository.existsByNombre(dto.getNombre())).thenReturn(false);
        when(terminalRepository.save(any(Terminal.class))).thenAnswer(invocation -> {
            Terminal terminal = invocation.getArgument(0);
            terminal.setId(1L);
            return terminal;
        });

        TerminalDto resultado = terminalService.guardarTerminal(dto);

        assertThat(resultado.getNombre()).isEqualTo(dto.getNombre());

        verify(terminalRepository).save(any(Terminal.class));
    }

    // Test caso de error del método guardarTerminal. Nombre de terminal ya existente
    @Test
    void guardarTerminalDeberiaLanzarExcepcionCuandoElNombreYaExiste() {
        CrearTerminalDto dto = crearTerminalDto("Terminal-1");

        when(terminalRepository.existsByNombre(dto.getNombre())).thenReturn(true);

        assertThatThrownBy(() -> terminalService.guardarTerminal(dto))
                .isInstanceOf(PedidoStateException.class)
                .hasMessageContaining("Ya existe una terminal con el nombre: " + dto.getNombre());

        verify(terminalRepository, never()).save(any());
    }
}
