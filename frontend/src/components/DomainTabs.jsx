function DomainTabs({ domains, activeId, onSelect }) {
  if (domains.length === 0) return null;
  return (
    <div className="domain-tabs">
      {domains.map((d) => (
        <button
          key={d.id}
          className={d.id === activeId ? 'domain-tab active' : 'domain-tab'}
          onClick={() => onSelect(d.id)}
        >
          {d.name}
        </button>
      ))}
    </div>
  );
}

export default DomainTabs;
