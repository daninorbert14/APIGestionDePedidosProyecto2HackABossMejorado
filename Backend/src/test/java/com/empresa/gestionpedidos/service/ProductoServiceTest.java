package com.empresa.gestionpedidos.service;

import com.empresa.gestionpedidos.dto.CrearProductoDto;
import com.empresa.gestionpedidos.dto.ProductoDto;
import com.empresa.gestionpedidos.exception.PedidoStateException;
import com.empresa.gestionpedidos.exception.ResourceNotFoundException;
import com.empresa.gestionpedidos.model.Categoria;
import com.empresa.gestionpedidos.model.Producto;
import com.empresa.gestionpedidos.repository.CategoriaRepository;
import com.empresa.gestionpedidos.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private ProductoService productoService;

    // Test caso de éxito del método crearProducto
    @Test
    void crearProductoDeberiaGuardarloCuandoNombreNoExiste() {
        // Arrange: preparamos los datos de entrada y le decimos a los mocks qué responder
        CrearProductoDto dto = new CrearProductoDto();
        dto.setNombre("Hamburguesa clásica");
        dto.setPrecio(new BigDecimal("8.50"));
        dto.setActivo(true);
        dto.setCategoriaId(1L);

        Categoria categoria = new Categoria();
        categoria.setId(1L);
        categoria.setNombre("Hamburguesas");

        when(productoRepository.existsByNombre(dto.getNombre())).thenReturn(false);
        when(categoriaRepository.findById(categoria.getId())).thenReturn(Optional.of(categoria));
        /* El objeto se construye dentro del método que se prueba y no hay forma de referenciarlo desde fuera
           por lo que se accede a él mediante thenAnswer(invocation) */
        when(productoRepository.save(any(Producto.class))).thenAnswer(invocation -> {
            Producto p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        // Act: llamamos al método real que estamos probando
        ProductoDto resultado = productoService.crearProducto(dto);

        // Assert: comprobamos que el resultado es el esperado
        assertThat(resultado.getNombre()).isEqualTo(dto.getNombre());
        assertThat(resultado.getPrecio()).isEqualByComparingTo(dto.getPrecio());
        assertThat(resultado.getNombreCategoria()).isEqualTo(categoria.getNombre());
        verify(productoRepository).save(any(Producto.class));
    }

    // Test caso de error del método crearProducto. Nombre ya existente
    @Test
    void crearProductoDeberiaLanzarExcepcionCuandoNombreYaExiste() {
        CrearProductoDto dto = new CrearProductoDto();
        dto.setNombre("Hamburguesa clásica");
        dto.setPrecio(new BigDecimal("8.50"));
        dto.setActivo(true);
        dto.setCategoriaId(1L);

        when(productoRepository.existsByNombre(dto.getNombre())).thenReturn(true);

        assertThatThrownBy(() -> productoService.crearProducto(dto))
                .isInstanceOf(PedidoStateException.class)
                // Los mensajes de las excepciones se copian literalmente del Service
                .hasMessageContaining("Ya existe un producto con el nombre");

        // Verificamos que, al fallar la validación, nunca se llega a guardar nada
        verify(productoRepository, never()).save(any());
    }

    // Test listarProductos: filtra por categoría además de por estado activo
    @Test
    void listarProductosDeberiaFiltrarPorActivoYCategoria() {
        // Arrange
        Categoria hamburguesas = new Categoria();
        hamburguesas.setId(1L);
        hamburguesas.setNombre("Hamburguesas");

        Categoria bebidas = new Categoria();
        bebidas.setId(2L);
        bebidas.setNombre("Bebidas");

        Producto p1 = new Producto();
        p1.setNombre("Hamburguesa clásica");
        p1.setPrecio(new BigDecimal("8.50"));
        p1.setActivo(true);
        p1.setCategoria(hamburguesas);

        Producto p2 = new Producto();
        p2.setNombre("Hamburguesa vegana");
        p2.setPrecio(new BigDecimal("9.50"));
        p2.setActivo(false); // inactivo: no debería aparecer
        p2.setCategoria(hamburguesas);

        Producto p3 = new Producto();
        p3.setNombre("Coca-Cola");
        p3.setPrecio(new BigDecimal("2.00"));
        p3.setActivo(true);
        p3.setCategoria(bebidas); // otra categoría: no debería aparecer

        when(productoRepository.findAll()).thenReturn(List.of(p1, p2, p3));

        // Act: pedimos solo los activos de la categoría 1 (Hamburguesas)
        List<ProductoDto> resultado = productoService.listarProductos(true, 1L, null, null);

        // Assert
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getNombre()).isEqualTo(p1.getNombre());
    }

    // Test listarProductos: ordena por precio ascendente por defecto
    @Test
    void listarProductosDeberiaOrdenarPorPrecioAscendentePorDefecto() {
        Producto barato = new Producto();
        barato.setNombre("Patatas");
        barato.setPrecio(new BigDecimal("3.00"));
        barato.setActivo(true);
        barato.setCategoria(new Categoria());

        Producto caro = new Producto();
        caro.setNombre("Menú completo");
        caro.setPrecio(new BigDecimal("15.00"));
        caro.setActivo(true);
        caro.setCategoria(new Categoria());

        // Los devolvemos "desordenados" a propósito, para comprobar que el método sí ordena
        when(productoRepository.findAll()).thenReturn(List.of(caro, barato));

        List<ProductoDto> resultado = productoService.listarProductos(null, null, "precio", null);

        assertThat(resultado).extracting(ProductoDto::getNombre)
                .containsExactly("Patatas", "Menú completo");
    }

    // Test listarProductos: ordena por precio, pero en descendente cuando se pide DESC
    @Test
    void listarProductosDeberiaOrdenarPorPrecioDescendenteCuandoSePide() {
        Producto barato = new Producto();
        barato.setNombre("Patatas");
        barato.setPrecio(new BigDecimal("3.00"));
        barato.setActivo(true);
        barato.setCategoria(new Categoria());

        Producto caro = new Producto();
        caro.setNombre("Menú completo");
        caro.setPrecio(new BigDecimal("15.00"));
        caro.setActivo(true);
        caro.setCategoria(new Categoria());

        when(productoRepository.findAll()).thenReturn(List.of(barato, caro));

        List<ProductoDto> resultado = productoService.listarProductos(null, null, "precio", "DESC");

        assertThat(resultado).extracting(ProductoDto::getNombre)
                .containsExactly("Menú completo", "Patatas");
    }

    // Test caso de éxito del método actualizarProducto
    @Test
    void actualizarProductoDeberiaGuardarloCuandoIdExiste() {
        // Datos "nuevos" que llegan en el DTO para actualizar
        CrearProductoDto dto = new CrearProductoDto();
        dto.setNombre("Hamburguesa premium");
        dto.setPrecio(new BigDecimal("12.50"));
        dto.setActivo(true);
        dto.setCategoriaId(1L);

        // El producto tal y como estaba guardado ANTES de la actualización
        Categoria categoriaAntigua = new Categoria();
        categoriaAntigua.setId(2L);
        categoriaAntigua.setNombre("Sándwiches");

        Producto productoExistente = new Producto();
        productoExistente.setId(1L);
        productoExistente.setNombre("Hamburguesa clásica");
        productoExistente.setPrecio(new BigDecimal("8.50"));
        productoExistente.setActivo(true);
        productoExistente.setCategoria(categoriaAntigua);

        // La categoría que el DTO pide como NUEVA categoría
        Categoria categoriaNueva = new Categoria();
        categoriaNueva.setId(1L);
        categoriaNueva.setNombre("Hamburguesas");

        when(productoRepository.findById(productoExistente.getId())).thenReturn(Optional.of(productoExistente));
        when(categoriaRepository.findById(dto.getCategoriaId())).thenReturn(Optional.of(categoriaNueva));
        when(productoRepository.save(any(Producto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductoDto resultado = productoService.actualizarProducto(productoExistente.getId(), dto);

        assertThat(resultado.getNombre()).isEqualTo(dto.getNombre());
        assertThat(resultado.getPrecio()).isEqualByComparingTo(dto.getPrecio());
        assertThat(resultado.getNombreCategoria()).isEqualTo(categoriaNueva.getNombre());
        verify(productoRepository).save(any(Producto.class));
    }


    // Test caso de error del método actualizarProducto. ID inexistente
    @Test
    void actualizarProductoDeberiaLanzarExcepcionCuandoIdNoExiste() {
        CrearProductoDto dto = new CrearProductoDto();
        dto.setNombre("Hamburguesa clásica");
        dto.setPrecio(new BigDecimal("8.50"));
        dto.setActivo(true);
        dto.setCategoriaId(1L);

        /* En los tests de "caso de error", el mock siempre debe simular el mismo id/nombre que usa el Act
        devolviendo el valor "vacío" correspondiente (Optional.empty(), false, etc.) — nunca un id distinto al que se llama */
        when(productoRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.actualizarProducto(1L, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Producto no encontrado");

        verify(productoRepository, never()).save(any());
    }

    // Test caso de éxito del método cambiarEstado
    @Test
    void cambiarEstadoDeberiaCambiarloCuandoExiste() {
        boolean nuevoEstado = false; // lo desactivamos, para que el cambio sea visible

        Producto producto = new Producto();
        producto.setId(1L);
        producto.setActivo(true); // estado inicial: activo

        when(productoRepository.findById(producto.getId())).thenReturn(Optional.of(producto));
        /* El objeto esparado por save() ya existe como variable creada antes del Act entonces se usa thenReturn */
        when(productoRepository.save(any(Producto.class))).thenReturn(producto);

        productoService.cambiarEstado(producto.getId(), nuevoEstado);

        // Mismo objeto, ya mutado. Al ser un método void, reutilizar la misma instancia que se mockeó sirve como comprobación
        assertThat(producto.isActivo()).isFalse();
        verify(productoRepository).save(producto);
    }

    // Test caso de error del método cambiarEstado
    @Test
    void cambiarEstadoDeberiaLanzarExcepcionCuandoNoExiste() {
        boolean activo = true;

        when(productoRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.cambiarEstado(1L, activo))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no encontrado");

        verify(productoRepository, never()).save(any());
    }
}
