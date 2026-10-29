package gestionpedidos.repository;

import gestionpedidos.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    // Unicidad insensible a mayúsculas (spec 004): el nombre se guarda y se
    // muestra tal como se escribió, pero "Bebidas" y "BEBIDAS" cuentan como la misma
    boolean existsByNombreIgnoreCase(String nombre);
}
