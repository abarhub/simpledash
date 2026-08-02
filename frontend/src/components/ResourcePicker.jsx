function ResourcePicker({ resources, groups, selectedIds, onToggleResource, onToggleGroup, loading }) {
  if (loading) {
    return (
      <div className="picker">
        <h2>Ressources</h2>
        <p className="picker-loading">Recherche des ressources en cours...</p>
      </div>
    );
  }

  if (resources.length === 0) return null;

  return (
    <div className="picker">
      <h2>Ressources</h2>
      {groups.length > 0 && (
        <div className="group-list">
          {groups.map((g) => (
            <button key={g.id} className="group-btn" onClick={() => onToggleGroup(g)}>
              {g.name} ({g.resourceIds.length})
            </button>
          ))}
        </div>
      )}
      <div className="item-list">
        {resources.map((r) => (
          <label key={r.id} className="item">
            <input
              type="checkbox"
              checked={selectedIds.includes(r.id)}
              onChange={() => onToggleResource(r.id)}
            />
            {r.name}
            <span className="type-badge">{r.types.join(', ')}</span>
          </label>
        ))}
      </div>
    </div>
  );
}

export default ResourcePicker;
