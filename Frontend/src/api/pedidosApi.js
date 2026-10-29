import { API_URL } from "../utils/constants";
import { fetchJSON } from "./fetchHelper";

const PEDIDOS_URL = `${API_URL}/pedidos`;

export async function registrarPedido(crearPedidoDto) {
    return fetchJSON(PEDIDOS_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(crearPedidoDto)
    });
}

export async function agregarProductoAPedido(pedidoId, dto) {
    return fetchJSON(`${PEDIDOS_URL}/${pedidoId}/productos`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(dto)
    });
}

// Cada endpoint es una función: la URL y el método quedan centralizados y
// cualquier cambio de ruta se hace en un solo sitio. fetchJSON unifica el
// parseo del cuerpo y la conversión de errores (una sola forma de fallar).

// La cantidad va en la query, no en el body: el DELETE solo acepta el
// parámetro `cantidad` (así lo define el contrato del backend, RF-15)
export async function eliminarProductoDePedido(pedidoId, productoId, cantidad) {
    return fetchJSON(
        `${PEDIDOS_URL}/${pedidoId}/eliminar-producto/${productoId}?cantidad=${cantidad}`,
        { method: "DELETE" }
    );
}

// PATCH y no PUT: solo se envía el campo estado (actualización parcial);
// el backend rechaza con 400 las transiciones fuera del orden permitido
export async function cambiarEstadoDelPedido(pedidoId, dto) {
    return fetchJSON(`${PEDIDOS_URL}/${pedidoId}/estado`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(dto)
    });
}

export async function buscarPedidoPorCodigo(codigo) {
    return fetchJSON(`${PEDIDOS_URL}/codigo/${codigo}`);
}

export async function listarPedidosYPorEstado(estado) {
    // En función de si recibe el query param que no es obligatorio, la url es una u otra
    const url = estado ? `${PEDIDOS_URL}?estado=${estado}` : PEDIDOS_URL;

    return fetchJSON(url);
}
