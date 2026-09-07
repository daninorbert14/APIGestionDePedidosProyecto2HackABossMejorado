package com.empresa.gestionpedidos.service;

import com.empresa.gestionpedidos.dto.CrearProductoDto;
import com.empresa.gestionpedidos.dto.ProductoDto;
import com.empresa.gestionpedidos.exception.PedidoStateException;
import com.empresa.gestionpedidos.model.Categoria;
import com.empresa.gestionpedidos.model.Producto;
import com.empresa.gestionpedidos.repository.CategoriaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CategoriaServiceTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private CategoriaService categoriaService;


}
