import { useState, useEffect } from "react";
import { obtenerProductosMasVendidos } from "../api/estadisticasApi";

// Estadísticas puntuales: se cargan una sola vez al montar (deps []), sin
// polling como la lista de pedidos; es un panel informativo y para verlo
// actualizado basta con recargar la carta
export function useProductosMasVendidos() {
  // loading nace en true: la sección "Los más vendidos" no debe mostrarse
  // vacía mientras llegan los datos (evita el parpadeo de "No hay productos")
  const [datos, setDatos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    obtenerProductosMasVendidos()
      .then((data) => {
        setDatos(data);
        setLoading(false);
      })
      .catch(() => {
        // En error, datos a vacío y error seteado: la página puede ocultar la
        // sección de más vendidos sin que el resto de la carta deje de funcionar
        setDatos([]);
        setLoading(false);
        setError("No se pudieron cargar las estadísticas");
      });
  }, []);

  return { datos, loading, error };
}
