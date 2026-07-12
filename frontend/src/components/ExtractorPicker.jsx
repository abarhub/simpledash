function ExtractorPicker({ extractors, selectedIds, onToggle }) {
  if (extractors.length === 0) return null;

  return (
    <div className="picker">
      <h2>Infos à afficher</h2>
      <div className="item-list">
        {extractors.map((e) => (
          <label key={e.id} className="item" title={e.description}>
            <input
              type="checkbox"
              checked={selectedIds.includes(e.id)}
              onChange={() => onToggle(e.id)}
            />
            {e.name}
          </label>
        ))}
      </div>
    </div>
  );
}

export default ExtractorPicker;
