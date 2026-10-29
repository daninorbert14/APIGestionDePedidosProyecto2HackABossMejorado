package gestionpedidos.controller;

import gestionpedidos.dto.ProductoMasVendidoDto;
import gestionpedidos.dto.TerminalMasUtilizadaDto;
import gestionpedidos.service.EstadisticasService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/estadisticas")
@RequiredArgsConstructor
public class EstadisticasController {

    private final EstadisticasService estadisticasService;

    // GET de listado → array directo, sin sobre: misma forma que el resto de
    // listados de la API (regla fijada en AGENTS.md)

    /**
     * Ranking completo de terminales por uso, de más a menos. Sin parámetros
     * ni paginación: es un listado pequeño. Hoy no lo consume ninguna
     * pantalla del frontend; queda expuesto para Swagger.
     */
    @GetMapping("/ranking-terminales")
    public ResponseEntity<List<TerminalMasUtilizadaDto>> obtenerRankingDeTerminales() {
        return ResponseEntity.ok(estadisticasService.obtenerRankingDeTerminales());
    }

    /**
     * Productos vendidos, de más a menos. Devuelve la lista completa ordenada;
     * el corte a los 5 primeros (lo que muestra la carta) lo hace el frontend.
     */
    @GetMapping("/productos-mas-vendidos")
    public ResponseEntity<List<ProductoMasVendidoDto>> obtenerProductosMasVendidos() {
        return ResponseEntity.ok(estadisticasService.obtenerProductosMasVendidos());
    }
}
