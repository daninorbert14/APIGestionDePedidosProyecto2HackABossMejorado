package com.empresa.gestionpedidos.repository;

import com.empresa.gestionpedidos.model.Terminal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerminalRepository extends JpaRepository<Terminal, Long> {
    boolean existsByNombre(String nombre);
}
