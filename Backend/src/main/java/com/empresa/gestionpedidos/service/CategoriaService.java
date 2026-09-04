package com.empresa.gestionpedidos.service;

import com.empresa.gestionpedidos.dto.CategoriaDto;
import com.empresa.gestionpedidos.dto.CrearCategoriaDto;
import com.empresa.gestionpedidos.exception.PedidoStateException;
import com.empresa.gestionpedidos.exception.ResourceNotFoundException;
import com.empresa.gestionpedidos.model.Categoria;
import com.empresa.gestionpedidos.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor // Lombok genera el constructor con los campos final
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    //    Listar categorias
    public List<CategoriaDto> listarCategorias() {
        return categoriaRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    //    Crear categorias
    public CategoriaDto guardarCategoria(CrearCategoriaDto crearCategoriaDto) {
        // Evita categorías con el mismo nombre
        if (categoriaRepository.existsByNombre(crearCategoriaDto.getNombre())) {
            throw new PedidoStateException("Ya existe una categoría con el nombre: " + crearCategoriaDto.getNombre());
        }

        Categoria nuevaCategoria = new Categoria();
        nuevaCategoria.setNombre(crearCategoriaDto.getNombre());
        Categoria categoriaGuardada = categoriaRepository.save(nuevaCategoria);
        return toDto(categoriaGuardada);    //Este dto devolvemos
    }

    //    Obtener categorias por id
    public CategoriaDto obtenerPorId(Long id) {
        return toDto(categoriaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Categoria no encontrada")));
    }

    // *** MÉTODOS DE MAPEO ***

    private CategoriaDto toDto(Categoria categoria) {
        CategoriaDto dto = new CategoriaDto();
        dto.setId(categoria.getId());
        dto.setNombre(categoria.getNombre());
        return dto;
    }
}
