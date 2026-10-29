import { useState, useEffect } from "react";
import { buscarPedidoPorCodigo } from "../api/pedidosApi";

// Hook de búsqueda puntual: busca solo cuando cambia el `codigo` que le pasa
// el padre; quien lo usa decide cuándo pasarle uno nuevo (p. ej. al pulsar Buscar).
export function usePedidoPorCodigo(codigo) {
  // loading nace en false: mientras no hay código no hay nada que cargar y el
  // spinner no debe aparecer solo por montar el componente
  const [pedido, setPedido] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!codigo) {
      // Borrar el código limpia el resultado anterior: si no, quedaría a la
      // vista un pedido que ya no corresponde, y la petición iría a una ruta vacía
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setPedido(null);
      setError(null);
      return;
    }

    setLoading(true);
    setError(null);
    buscarPedidoPorCodigo(codigo)
      .then((data) => {
        setPedido(data);
        setError(null);
      })
      .catch((err) => {
        // En error se vacía el pedido: la vista nunca debe mezclar el error
        // con los datos de una búsqueda anterior que sí salió bien
        setPedido(null);
        setError(err.message || "Error al buscar el pedido");
      })
      .finally(() => setLoading(false));
  }, [codigo]);

  return { pedido, loading, error };
}
