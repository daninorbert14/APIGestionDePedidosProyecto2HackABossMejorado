package com.empresa.gestionpedidos.controller;

import com.empresa.gestionpedidos.dto.*;
import com.empresa.gestionpedidos.model.EstadoPedido;
import com.empresa.gestionpedidos.service.PedidoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {
    @Autowired
    private PedidoService pedidoService;

    @PostMapping
    public ResponseEntity<PedidoDto> registrarNuevoPedido(@Valid @RequestBody CrearPedidoDto crearPedidoDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.registrarPedido(crearPedidoDto));
    }

    @PostMapping("/{pedidoId}/productos")
    public ResponseEntity<ProductosPedidoDto> agregarProductoAPedido(@PathVariable Long pedidoId, @Valid @RequestBody PedidoProductoRequestDto dto) {
        return ResponseEntity.ok(pedidoService.agregarProductosAPedido(pedidoId, dto));
    }

    @DeleteMapping("/{pedidoId}/productos/{productoId}")
    public ResponseEntity<ProductosPedidoDto> eliminarProductoDePedido(@PathVariable Long pedidoId, @PathVariable Long productoId, @RequestParam Integer cantidad) {
        return ResponseEntity.ok(pedidoService.eliminarProductoDePedido(pedidoId, productoId, cantidad));
    }

    @PatchMapping("/{pedidoId}/estado")
    public ResponseEntity<PedidoDto> cambiarEstadoDelPedido(@PathVariable Long pedidoId, @RequestBody EstadoPedidoRequestDto dto) {
        return ResponseEntity.ok(pedidoService.gestionarEstadoDelPedido(pedidoId, dto.getEstado()));
    }

    @GetMapping("/codigo/{codigo}")
    public ResponseEntity<PedidoDto> obtenerPedidoPorCodigo(@PathVariable String codigo) {
        return ResponseEntity.ok(pedidoService.obtenerPedidoPorCodigo(codigo));
    }

    @GetMapping
    public ResponseEntity<List<PedidoDto>> listarPedidosYPorEstado(@RequestParam(required = false) EstadoPedido estado) {
        return ResponseEntity.ok(pedidoService.listarPedidos(estado));
    }
}
