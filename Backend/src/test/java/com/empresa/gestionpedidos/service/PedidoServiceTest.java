package com.empresa.gestionpedidos.service;

import com.empresa.gestionpedidos.dto.CrearPedidoDto;
import com.empresa.gestionpedidos.dto.PedidoDto;
import com.empresa.gestionpedidos.model.EstadoPedido;
import com.empresa.gestionpedidos.model.Pedido;
import com.empresa.gestionpedidos.model.Terminal;
import com.empresa.gestionpedidos.repository.PedidoRepository;
import com.empresa.gestionpedidos.repository.ProductoRepository;
import com.empresa.gestionpedidos.repository.TerminalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private TerminalRepository terminalRepository;

    @InjectMocks
    private PedidoService pedidoService;

    // Test listarPedidos sin filtrar
    @Test
    void listarPedidosDeberiaDevolverTodosLosPedidosSinFiltrar() {
        // Arrange
        Terminal terminal = new Terminal();
        terminal.setId(1L);
        terminal.setNombre("Terminal 1");

        Pedido p1 = new Pedido();
        p1.setCodigo("PED-374837");
        p1.setFecha(LocalDateTime.of(2026, 8, 8, 12, 0));
        p1.setTotal(new BigDecimal("15.50"));
        p1.setEstadoPedido(EstadoPedido.PREPARACION);
        p1.setTerminal(terminal);
        p1.setLineasPedido(List.of()); // sin líneas: no afecta a este test

        when(pedidoRepository.findAllByOrderByFechaAsc()).thenReturn(List.of(p1));

        // Act
        List<PedidoDto> resultado = pedidoService.listarPedidos(null);

        // Assert
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getCodigo()).isEqualTo("PED-374837");
        verify(pedidoRepository).findAllByOrderByFechaAsc();
        /* Para verificar que un método que recibe un argumento no debería llamarse,
         se usa any() (o el matcher específico del tipo, como anyLong(), anyString()) en vez de un valor concreto */
        verify(pedidoRepository, never()).findByEstadoPedidoOrderByFechaAsc(any());
    }

    // Test listarPedidos filtrados por el valor de "estado"
    @Test
    void listarPedidosDeberiaFiltrarPorEstado() {
        // Arrange
        Terminal terminal = new Terminal();
        terminal.setId(1L);
        terminal.setNombre("Terminal 1");

        Pedido p1 = new Pedido();
        p1.setCodigo("PED-374837");
        p1.setFecha(LocalDateTime.of(2026, 8, 8, 12, 0));
        p1.setTotal(new BigDecimal("15.50"));
        p1.setEstadoPedido(EstadoPedido.PREPARACION);
        p1.setTerminal(terminal);
        p1.setLineasPedido(List.of()); // sin líneas: no afecta a este test

        when(pedidoRepository.findByEstadoPedidoOrderByFechaAsc(EstadoPedido.PREPARACION))
                .thenReturn(List.of(p1));

        // Act
        List<PedidoDto> resultado = pedidoService.listarPedidos(EstadoPedido.PREPARACION);

        // Assert
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getCodigo()).isEqualTo("PED-374837");
        /* Verifico estadoPedido.name() como texto (.isEqualTo("PREPARACION")) en vez de comparar el enum directamente
         porque PedidoDto.estado es un String, no un EstadoPedido.
         Así compruebo que el conversor pedidoToPedidoDto hace bien la conversión enum -> String */
        assertThat(resultado.get(0).getEstado()).isEqualTo("PREPARACION");
        verify(pedidoRepository).findByEstadoPedidoOrderByFechaAsc(EstadoPedido.PREPARACION);
        verify(pedidoRepository, never()).findAllByOrderByFechaAsc();
    }

    // Test caso de éxito del método registrarPedido
    @Test
    void registrarPedidoDeberiaGuardarlo() {
        CrearPedidoDto crearPedidoDto = new CrearPedidoDto();
        crearPedidoDto.setTerminalId(1L);
        crearPedidoDto.setProductosComprados(Map<Long, Integer>);

        Terminal terminalUsada = new Terminal();
        terminalUsada.setId(1L);
        terminalUsada.setNombre("Terminal 1");
        terminalUsada.setListaPedidos(List.of());

        Pedido nuevoPedido = new Pedido();
        nuevoPedido.setCodigo("PED-374837");
        nuevoPedido.setFecha(LocalDateTime.of(2026, 8, 8, 12, 0));
        nuevoPedido.setTerminal(terminalUsada);

        when(pedidoRepository.save(any(Pedido.class))).thenReturn(nuevoPedido);

        PedidoDto resultado = pedidoService.registrarPedido(crearPedidoDto);

        assertThat(resultado.getCodigo()).isEqualTo("PED-374837");
        assertThat(resultado.getTerminalId()).isEqualTo(1L);
        verify(pedidoRepository).save(any(Pedido.class));
    }
}
