import { API_URL } from "../utils/constants";
import { fetchJSON } from "./fetchHelper";

const TERMINALES_URL = `${API_URL}/terminales`;

export async function listarTerminales() {
    return fetchJSON(`${TERMINALES_URL}`);
}

// Sin usar
/* export async function buscarTerminalPorId(id) {
    return fetchJSON(`${TERMINALES_URL}/${id}`);
} */

// Sin usar
/* export async function crearTerminal(nuevaTerminalDto) {
    return fetchJSON(`${TERMINALES_URL}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(nuevaTerminalDto)
    });
} */