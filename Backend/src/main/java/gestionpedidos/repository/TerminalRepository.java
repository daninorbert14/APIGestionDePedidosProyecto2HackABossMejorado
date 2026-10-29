package gestionpedidos.repository;

import gestionpedidos.model.Terminal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerminalRepository extends JpaRepository<Terminal, Long> {
    // Unicidad insensible a mayúsculas (spec 004): el nombre se guarda y se
    // muestra tal como se escribió, pero "Cocina 1" y "COCINA 1" cuentan como la misma
    boolean existsByNombreIgnoreCase(String nombre);
}
