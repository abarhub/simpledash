function CardTable({ table }) {
  return (
    <table className="widget-table">
      <thead>
        <tr>
          {table.columns.map((col) => (
            <th key={col}>{col}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {table.rows.map((row, i) => (
          <tr key={i}>
            {row.cells.map((cell, j) => (
              <td key={j} title={String(cell)}>
                {j === 0 && row.url ? (
                  <a href={row.url} target="_blank" rel="noopener noreferrer">
                    {cell}
                  </a>
                ) : (
                  String(cell)
                )}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function CardKeyValue({ data }) {
  return (
    <table>
      <tbody>
        {Object.entries(data).map(([key, value]) => (
          <tr key={key}>
            <td className="label">{key}</td>
            <td title={String(value)}>{String(value)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function Card({ widget, onRefresh, onRemove, refreshing }) {
  return (
    <div className={widget.table ? 'card has-table' : 'card'}>
      <div className="card-header">
        <div className="card-heading">
          <h3 title={widget.title}>{widget.title}</h3>
          {widget.resourceName && <p className="card-subtitle">{widget.resourceName}</p>}
        </div>
        <div className="card-actions">
          {widget.url && (
            <a
              className="link-btn"
              href={widget.url}
              target="_blank"
              rel="noopener noreferrer"
              title="Ouvrir sur le serveur distant"
            >
              ↗
            </a>
          )}
          <button
            className="refresh-btn"
            onClick={onRefresh}
            disabled={refreshing}
            title="Rafraîchir"
          >
            {refreshing ? '...' : '⟳'}
          </button>
          <button className="remove-btn" onClick={onRemove} title="Retirer">
            ✕
          </button>
        </div>
      </div>
      <div className="card-body">
        {widget.error ? (
          <p className="error">Erreur : {widget.error}</p>
        ) : widget.table ? (
          <CardTable table={widget.table} />
        ) : (
          <CardKeyValue data={widget.data} />
        )}
      </div>
    </div>
  );
}

export default Card;
