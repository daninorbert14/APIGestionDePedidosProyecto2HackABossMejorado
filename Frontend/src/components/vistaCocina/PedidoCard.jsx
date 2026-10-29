import { useState } from "react";
import { ESTADOS_PEDIDO } from "../../utils/constants";
import PedidoLineaInput from "./PedidoLineaInput";
import { Play, Check } from "lucide-react";

export default function PedidoCard({ pedido, onAvanzar, onRefetch }) {
  // Estado local de "petición en vuelo": mientras onAvanzar no resuelve, el
  // botón queda deshabilitado (evita doble click y estados duplicados)
  const [advancing, setAdvancing] = useState(false);

  async function handleAvanzar() {
    setAdvancing(true);
    try {
      await onAvanzar(pedido.id, pedido.estado);
    } finally {
      setAdvancing(false);
    }
  }

  // Mismo botón, dos acciones según el estado: cocina solo gestiona estos dos
  // saltos (CREADO → PREPARACION → LISTO); el resto de transiciones no es suyo
  const labelBoton =
    pedido.estado === ESTADOS_PEDIDO.CREADO ? (
      <>
        <Play size={16} /> Iniciar preparación
      </>
    ) : (
      <>
        <Check size={16} /> Marcar como listo
      </>
    );

  return (
    <div className={`pedido-card pedido-card--${pedido.estado}`}>
      <header className="pedido-card__header">
        <span className="pedido-card__codigo">{pedido.codigo}</span>
        <span className={`pedido-card__badge pedido-card__badge--${pedido.estado.toLowerCase()}`}>
          {pedido.estado}
        </span>
      </header>

      <ul className="pedido-card__productos">
        {/* ?. en productos: un pedido recién creado o uno cuya línea se acaba de
            quitar puede llegar sin líneas; sin optional chaining el render revienta */}
        {pedido.productos?.map((p) => (
          <li key={p.productoId} className="pedido-card__producto">
            <PedidoLineaInput
              pedidoId={pedido.id}
              productoId={p.productoId}
              cantidadActual={p.cantidad}
              pedidoEstado={pedido.estado}
              onCambio={onRefetch}
            />
            <span className="pedido-card__nombre">{p.nombreProducto}</span>
          </li>
        ))}
      </ul>

      <button
        className="pedido-card__btn"
        onClick={handleAvanzar}
        disabled={advancing}
      >
        {advancing ? "Actualizando…" : labelBoton}
      </button>
    </div>
  );
}
