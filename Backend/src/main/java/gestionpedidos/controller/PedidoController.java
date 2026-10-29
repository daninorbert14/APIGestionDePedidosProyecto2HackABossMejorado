package gestionpedidos.controller;

import gestionpedidos.dto.*;
import gestionpedidos.model.EstadoPedido;
import gestionpedidos.service.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor // Lombok genera el constructor con los campos final
public class PedidoController {

    private final PedidoService pedidoService;

    // Forma de la respuesta, fijada en la spec y en AGENTS.md: GET de listados →
    // array directo; POST/PUT de entidad de primer nivel → {"mensaje", "data"};
    // sub-recursos de pedido (productos, código, estado) → DTO directo, sin sobre.

    /**
     * Listado de pedidos. El filtro por estado es opcional (required=false)
     * para poder pedir la lista completa sin parámetros.
     */
    @GetMapping
    public ResponseEntity<List<PedidoDto>> listarPedidosYPorEstado(@RequestParam(required = false) EstadoPedido estado) {
        return ResponseEntity.ok(pedidoService.listarPedidos(estado));
    }

    /**
     * Registro de pedido nuevo. Responde 201 con sobre {"mensaje", "data"}
     * por ser entidad de primer nivel; el DTO "pelado" se reserva a sub-recursos.
     * @Valid dispara el manejador de excepciones (400 con "detalles") si el DTO no valida.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> registrarPedido(@Valid @RequestBody CrearPedidoDto crearPedidoDto) {
        PedidoDto pedidoDto = pedidoService.registrarPedido(crearPedidoDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "mensaje", "Pedido registrado correctamente",
                "data", pedidoDto));
    }

    /**
     * Añade unidades de un producto al pedido (crea la línea o suma a la
     * existente). El service exige que el pedido siga en CREADO.
     */
    @PostMapping("/{pedidoId}/productos")
    public ResponseEntity<ProductosPedidoDto> agregarProductoAPedido(@PathVariable Long pedidoId, @Valid @RequestBody PedidoProductoRequestDto dto) {
        return ResponseEntity.ok(pedidoService.agregarProductosAPedido(pedidoId, dto));
    }

    /**
     * Quita unidades de una línea. Si el pedido se queda sin líneas, el service
     * lo elimina y devuelve null → aquí se responde 204: un pedido vacío no existe
     * (regla de negocio de la spec 001).
     */
    @DeleteMapping("/{pedidoId}/eliminar-producto/{productoId}")
    public ResponseEntity<ProductosPedidoDto> eliminarProductoDePedido(@PathVariable Long pedidoId, @PathVariable Long productoId, @RequestParam Integer cantidad) {
        ProductosPedidoDto resultado = pedidoService.eliminarProductoDePedido(pedidoId, productoId, cantidad);
        if (resultado == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(resultado);
    }

    /**
     * Búsqueda puntual por código (path param). El código lo teclea el usuario,
     * por eso el service lo trimmea y busca sin distinguir mayúsculas.
     */
    @GetMapping("/codigo/{codigo}")
    public ResponseEntity<PedidoDto> obtenerPedidoPorCodigo(@PathVariable String codigo) {
        return ResponseEntity.ok(pedidoService.obtenerPedidoPorCodigo(codigo));
    }

    /**
     * Cambio de estado con PATCH (solo se envía el campo estado, es una
     * actualización parcial). Las transiciones fuera del orden
     * CREADO → PREPARACION → LISTO → PAGADO → ENTREGADO las rechaza el service con 400.
     */
    @PatchMapping("/{pedidoId}/estado")
    public ResponseEntity<PedidoDto> cambiarEstadoDelPedido(@PathVariable Long pedidoId, @RequestBody EstadoPedidoRequestDto dto) {
        return ResponseEntity.ok(pedidoService.cambiarEstadoDelPedido(pedidoId, dto.getEstado()));
    }
}
