package gestionpedidos.controller;

import gestionpedidos.dto.ProductoMasVendidoDto;
import gestionpedidos.dto.TerminalMasUtilizadaDto;
import gestionpedidos.service.EstadisticasService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EstadisticasController.class)
public class EstadisticasControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EstadisticasService estadisticasService;

    @Test
    void obtenerRankingDeTerminalesDeberiaDevolverloConEstadoOk() throws Exception {
        TerminalMasUtilizadaDto dto = new TerminalMasUtilizadaDto("Terminal 1", 20L);

        when(estadisticasService.obtenerRankingDeTerminales()).thenReturn(List.of(dto));

        mvc.perform(get("/api/estadisticas/ranking-terminales"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].nombreTerminal").value("Terminal 1"))
                .andExpect(jsonPath("$[0].totalPedidos").value(20))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void obtenerProductosMasVendidosDeberiaDevolverloConEstadoOk() throws Exception {
        ProductoMasVendidoDto dto = new ProductoMasVendidoDto("Hamburguesa clásica", 25L);

        when(estadisticasService.obtenerProductosMasVendidos()).thenReturn(List.of(dto));

        mvc.perform(get("/api/estadisticas/productos-mas-vendidos"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].nombreProducto").value("Hamburguesa clásica"))
                .andExpect(jsonPath("$[0].totalVendido").value(25))
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
