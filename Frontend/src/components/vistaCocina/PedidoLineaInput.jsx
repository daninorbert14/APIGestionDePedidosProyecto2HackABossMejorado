import { useState } from "react";
import { agregarProductoAPedido, eliminarProductoDePedido } from "../../api/pedidosApi";
import { ESTADOS_PEDIDO } from "../../utils/constants";
import { toast } from "sonner";
import { Minus, Plus } from "lucide-react";

export default function PedidoLineaInput({ pedidoId, productoId, cantidadActual, pedidoEstado, onCambio }) {
  const [modificando, setModificando] = useState(false);

  // Se envía la DIFERENCIA de unidades (delta), no el total: el endpoint de
  // añadir suma y el de eliminar resta; pedir el total provocaría dobles conteos.
  const modificarCantidad = async (nuevaCantidad) => {
    // Doble cinturón: el backend ya rechaza el cambio fuera de CREADO (400);
    // comprobarlo aquí evita la petición inútil y el toast de error.
    if (pedidoEstado !== ESTADOS_PEDIDO.CREADO) {
      toast.error("No se pueden modificar pedidos que ya avanzaron de estado");
      return;
    }
    if (nuevaCantidad === cantidadActual) return; // sin cambio real, no hay petición
    setModificando(true);
    try {
      if (nuevaCantidad > cantidadActual) {
        await agregarProductoAPedido(pedidoId, { productoId, cantidad: nuevaCantidad - cantidadActual });
        toast.success(`Unidad(es) actualizada(s) en producto: +${nuevaCantidad - cantidadActual}`);
      } else {
        await eliminarProductoDePedido(pedidoId, productoId, cantidadActual - nuevaCantidad);
        toast.success(`Unidad(es) actualizada(s) en producto: -${cantidadActual - nuevaCantidad}`);
      }
      onCambio();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Error al actualizar");
    } finally {
      setModificando(false);
    }
  };

  /* Solo se pinta la cantidad en estados avanzados: sin botones no hay nada
     que deshabilitar, y la columna "En preparación" queda de solo lectura */
  if (pedidoEstado !== ESTADOS_PEDIDO.CREADO) {
    return (
      <div className="pedido-linea-input-disabled">
        <span className="pedido-linea-input-cantidad"> {cantidadActual} ×</span>
      </div>
    );
  }

  return (
    <div className="pedido-linea-input">
      <button
        className="pedido-linea-input-btn-plus"
        onClick={() => modificarCantidad(cantidadActual + 1)}
        disabled={modificando}
        title="Añadir unidad"
      >
        <Plus size={14} />
      </button>
      {/* Math.max(0, …): el suelo evita cantidades negativas; al llegar a 0,
          la siguiente pulsación quita la línea entera (endpoint de eliminar) */}
      <button
        className="pedido-linea-input-btn-minus"
        onClick={() => modificarCantidad(Math.max(0, cantidadActual - 1))}
        disabled={modificando}
        title="Quitar unidad"
      >
        <Minus size={14} />
      </button>
      <span className="pedido-linea-input-cantidad">{cantidadActual} ×</span>
    </div>
  );
}
