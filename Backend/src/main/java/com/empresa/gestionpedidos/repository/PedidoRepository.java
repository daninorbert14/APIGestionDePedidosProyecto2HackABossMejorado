package com.empresa.gestionpedidos.repository;

import com.empresa.gestionpedidos.model.EstadoPedido;
import com.empresa.gestionpedidos.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    List<Pedido> findAllByOrderByFechaAsc();

    List<Pedido> findByEstadoPedidoOrderByFechaAsc(EstadoPedido estado);

    Optional<Pedido> findByCodigo(String codigo);
}
