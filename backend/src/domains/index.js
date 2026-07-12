import system from './system/index.js';
import projects from './projects/index.js';
import servers from './servers/index.js';

const domains = [system, projects, servers];

export function listDomains() {
  return domains.map(({ id, name }) => ({ id, name }));
}

export function getDomain(id) {
  return domains.find((d) => d.id === id);
}

export function listResources(domainId) {
  const domain = getDomain(domainId);
  if (!domain) return null;
  return { resources: domain.resources, groups: domain.groups ?? [] };
}

export function listExtractors(domainId, resourceIds) {
  const domain = getDomain(domainId);
  if (!domain) return null;
  const types = new Set(
    domain.resources.filter((r) => resourceIds.includes(r.id)).map((r) => r.type)
  );
  return domain.extractors
    .filter((e) => e.compatibleTypes.some((t) => types.has(t)))
    .map(({ id, name, description, compatibleTypes }) => ({ id, name, description, compatibleTypes }));
}

export async function getWidgets(domainId, resourceIds, extractorIds) {
  const domain = getDomain(domainId);
  if (!domain) throw new Error(`Domaine inconnu: ${domainId}`);

  const resources = domain.resources.filter((r) => resourceIds.includes(r.id));
  const extractors = domain.extractors.filter((e) => extractorIds.includes(e.id));

  const promises = resources.flatMap((resource) =>
    extractors
      .filter((extractor) => extractor.compatibleTypes.includes(resource.type))
      .map(async (extractor) => {
        try {
          const widgets = await extractor.fetch(resource);
          return widgets.map((w) => ({
            ...w,
            id: `${resource.id}-${extractor.id}-${w.id}`,
            domainId: domain.id,
            resourceId: resource.id,
            resourceName: resource.name,
            extractorId: extractor.id,
            extractorName: extractor.name,
          }));
        } catch (err) {
          return [
            {
              id: `${resource.id}-${extractor.id}-error`,
              domainId: domain.id,
              resourceId: resource.id,
              resourceName: resource.name,
              extractorId: extractor.id,
              extractorName: extractor.name,
              title: extractor.name,
              error: err.message,
            },
          ];
        }
      })
  );

  return (await Promise.all(promises)).flat();
}
