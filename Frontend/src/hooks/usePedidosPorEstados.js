import { useState, useEffect, useCallback } from "react";
import { listarPedidosYPorEstado } from "../api/pedidosApi";

const POLL_INTERVAL_MS = 15000; // refresco automático cada 15 segundos

// Hook genérico que carga pedidos de uno o varios estados y los refresca cada 15s.
// Recibe un array de estados: usePedidosPorEstados(['CREADO', 'PREPARACION'])
export function usePedidosPorEstados(estados) {
  const [pedidos, setPedidos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [lastUpdated, setLastUpdated] = useState(null);

  const fetchPedidos = useCallback(async () => {
    try {
      // Lanza una llamada por cada estado en paralelo y fusiona los resultados
      const listas = await Promise.all(estados.map(listarPedidosYPorEstado));
      setPedidos(listas.flat());
      setError(null);
      setLastUpdated(new Date());
    } catch (err) {
      setError("Servidor no disponible. Inténtalo de nuevo más tarde.");
    } finally {
      setLoading(false);
    }
  }, [estados]);

  // Polling automático cada 15 s
  useEffect(() => {
    fetchPedidos();
    const interval = setInterval(fetchPedidos, POLL_INTERVAL_MS);
    return () => clearInterval(interval); // limpia al desmontar
  }, [fetchPedidos]);

  return { pedidos, loading, error, lastUpdated, refetch: fetchPedidos };
}