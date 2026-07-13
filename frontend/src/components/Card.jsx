function Card({ widget, onRefresh, onRemove, refreshing }) {
  return (
    <div className="card">
      <div className="card-header">
        <div>
          <h3>{widget.title}</h3>
          {widget.resourceName && <p className="card-subtitle">{widget.resourceName}</p>}
        </div>
        <div className="card-actions">
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
        ) : (
          <table>
            <tbody>
              {Object.entries(widget.data).map(([key, value]) => (
                <tr key={key}>
                  <td className="label">{key}</td>
                  <td>{String(value)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

export default Card;
