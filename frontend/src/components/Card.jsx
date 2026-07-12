function Card({ widget, onRefresh, refreshing }) {
  return (
    <div className="card">
      <div className="card-header">
        <h3>{widget.title}</h3>
        <button
          className="refresh-btn"
          onClick={onRefresh}
          disabled={refreshing}
          title="Rafraîchir"
        >
          {refreshing ? '...' : '⟳'}
        </button>
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
