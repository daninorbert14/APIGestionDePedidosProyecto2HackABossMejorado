package gestionpedidos.service;

import gestionpedidos.dto.ProductoMasVendidoDto;
import gestionpedidos.dto.TerminalMasUtilizadaDto;
import gestionpedidos.repository.PedidoRepository;
import gestionpedidos.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EstadisticasServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @InjectMocks
    private EstadisticasService estadisticasService;

    @Test
    void obtenerRankingDeTerminalesDeberiaDevolverLoQueDaElRepositorio() {
        TerminalMasUtilizadaDto dto = new TerminalMasUtilizadaDto("Terminal 1", 20L);

        when(pedidoRepository.obtenerRankingDeTerminales()).thenReturn(List.of(dto));

        List<TerminalMasUtilizadaDto> resultado = estadisticasService.obtenerRankingDeTerminales();

        // System.out.println(mockingDetails(pedidoRepository).printInvocations());

        /* containsExactly() compara el objeto completo. Al no haber ningún cálculo que reproducir,
         es válido y no cae en el riesgo de tautología */
        assertThat(resultado).containsExactly(dto);
    }

    @Test
    void obtenerProductosMasVendidosDeberiaDevolverLoQueDaElRepositorio() {
        ProductoMasVendidoDto dto = new ProductoMasVendidoDto("Hamburguesa clásica", 25L);

        when(productoRepository.obtenerProductosMasVendidos()).thenReturn(List.of(dto));

        List<ProductoMasVendidoDto> resultado = estadisticasService.obtenerProductosMasVendidos();

        // System.out.println(mockingDetails(productoRepository).printInvocations());

        assertThat(resultado).containsExactly(dto);
    }
}
