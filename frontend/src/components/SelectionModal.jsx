import { useEffect } from 'react';
import DomainTabs from './DomainTabs';
import ResourcePicker from './ResourcePicker';
import ExtractorPicker from './ExtractorPicker';

function SelectionModal({
  domains,
  domainId,
  onSelectDomain,
  resources,
  groups,
  resourcesLoading,
  selectedResourceIds,
  onToggleResource,
  onToggleGroup,
  extractors,
  selectedExtractorIds,
  onToggleExtractor,
  onAdd,
  onClose,
  loading,
}) {
  useEffect(() => {
    function handleKeyDown(e) {
      if (e.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>Choisir des infos</h2>
          <button className="modal-close" onClick={onClose} title="Fermer">
            ✕
          </button>
        </div>

        <div className="modal-body">
          <DomainTabs domains={domains} activeId={domainId} onSelect={onSelectDomain} />

          <ResourcePicker
            resources={resources}
            groups={groups}
            loading={resourcesLoading}
            selectedIds={selectedResourceIds}
            onToggleResource={onToggleResource}
            onToggleGroup={onToggleGroup}
          />

          <ExtractorPicker
            extractors={extractors}
            selectedIds={selectedExtractorIds}
            onToggle={onToggleExtractor}
          />
        </div>

        <div className="modal-footer">
          <button
            className="show-btn"
            onClick={onAdd}
            disabled={selectedResourceIds.length === 0 || selectedExtractorIds.length === 0 || loading}
          >
            {loading ? 'Chargement...' : 'Ajouter'}
          </button>
        </div>
      </div>
    </div>
  );
}

export default SelectionModal;
