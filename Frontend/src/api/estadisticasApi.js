import { API_URL } from "../utils/constants";
import { fetchJSON } from "./fetchHelper";

// Estadisticas es solo lectura: un GET sin parámetros. Hoy solo se expone el
// endpoint que tiene pantalla que lo consume (top de productos); el ranking
// de terminales existe en el backend pero el frontend todavía no lo usa
const ESTADISTICAS_URL = `${API_URL}/estadisticas`;

export async function obtenerProductosMasVendidos() {
    return fetchJSON(`${ESTADISTICAS_URL}/productos-mas-vendidos`);
}
