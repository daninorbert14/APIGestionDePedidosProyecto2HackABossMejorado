package gestionpedidos.repository;

import gestionpedidos.dto.ProductoMasVendidoDto;
import gestionpedidos.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ProductoRepositoryTest {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private TerminalRepository terminalRepository;

    // *** MÉTODOS FACTORÍA ***

    // Solo con los argumentos extrictamente necesarios para hacer el test y los obligatorios aunque la query no los use
    private Categoria crearCategoria(String nombre, List<Producto> productos) {
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        categoria.setProductos(productos);
        return categoria;
    }

    private Pedido crearPedido(String codigo, Terminal terminal) {
        Pedido pedido = new Pedido();
        pedido.setCodigo(codigo);
        pedido.setTerminal(terminal);
        pedido.setLineasPedido(new ArrayList<>()); // mutable: las líneas se añaden a esta misma lista
        return pedido;
    }

    private Producto crearProducto(String nombre, String precio, boolean activo, Categoria categoria) {
        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setPrecio(new BigDecimal(precio));
        producto.setActivo(activo);
        producto.setCategoria(categoria);
        return producto;
    }

    private Terminal crearTerminal(String nombre) {
        Terminal terminal = new Terminal();
        terminal.setNombre(nombre);
        return terminal;
    }

    // Rellena los DOS lados de la relación: la línea apunta al pedido (lado dueño de la FK) y el pedido contiene la línea (activa la cascada)
    private void agregarLineaPedido(Pedido pedido, Producto producto, int cantidad) {
        PedidoProducto linea = new PedidoProducto();
        linea.setPedido(pedido);
        linea.setProducto(producto);
        linea.setCantidad(cantidad);
        linea.setPrecioUnitario(producto.getPrecio()); // obligatorio (nullable = false), aunque la query no lo use
        pedido.getLineasPedido().add(linea);
    }

    // *** TESTS ***

    @Test
    void obtenerProductosMasVendidosDeberiaOrdenarPorCantidadTotalDescendente() {
        // Arrange: aquí SÍ hay que crear y persistir datos reales, no mocks
        Categoria categoriaHamburguesas = categoriaRepository.save(crearCategoria("Hamburguesas", List.of()));
        Categoria categoriaPatatas = categoriaRepository.save(crearCategoria("Patatas", List.of()));

        /* El menos vendido se guarda primero y el más vendido después (id mayor):
        si el ORDER BY fallara, el orden natural por id no coincidiría con el esperado y el test lo detectaría */
        Producto patatasFritas = productoRepository.save(crearProducto("Patatas fritas", "3.00", true, categoriaPatatas));
        Producto hamburguesaClasica = productoRepository.save(crearProducto("Hamburguesa clásica", "8.50", true, categoriaHamburguesas));

        Terminal terminal = terminalRepository.save(crearTerminal("Terminal 1"));

        /* La hamburguesa aparece en dos pedidos (3 + 2 = 5) para que el SUM agregue de verdad
        varias líneas del mismo producto y no una sola */
        Pedido pedido1 = crearPedido("PED-0001", terminal);
        agregarLineaPedido(pedido1, hamburguesaClasica, 3);
        agregarLineaPedido(pedido1, patatasFritas, 1);

        Pedido pedido2 = crearPedido("PED-0002", terminal);
        agregarLineaPedido(pedido2, hamburguesaClasica, 2);

        // Un único save por pedido, con las líneas ya dentro: la cascada las guarda
        pedidoRepository.save(pedido1);
        pedidoRepository.save(pedido2);

        // Act: llamamos al método REAL, no mockeado
        List<ProductoMasVendidoDto> resultado = productoRepository.obtenerProductosMasVendidos();

        // Assert
        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).getNombreProducto()).isEqualTo(hamburguesaClasica.getNombre());
        // El totalVendido es Long de verdad aquí (no mockeado), así que compara contra 5L, no 5
        assertThat(resultado.get(0).getTotalVendido()).isEqualTo(5L);
        assertThat(resultado.get(1).getNombreProducto()).isEqualTo(patatasFritas.getNombre());
        assertThat(resultado.get(1).getTotalVendido()).isEqualTo(1L);
    }
}