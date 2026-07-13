import fs from 'node:fs/promises';
import path from 'node:path';

export const PROJECT_MARKERS = {
  'pom.xml': 'maven',
  'package.json': 'npm',
  'Cargo.toml': 'rust',
  'go.mod': 'go',
};

const DEFAULT_IGNORE_DIRS = [
  'node_modules',
  'target',
  'dist',
  'build',
  'out',
  '.git',
  'venv',
  '.venv',
  'env',
  '__pycache__',
];

// Cherche récursivement, à partir de rootDir, les répertoires contenant un
// fichier marqueur de projet (pom.xml, package.json, Cargo.toml, go.mod).
// Une fois un marqueur trouvé dans un répertoire, ses sous-répertoires ne
// sont pas explorés.
export async function findProjects(rootDir, { extraIgnoreDirs = [] } = {}) {
  const ignoreDirs = new Set([...DEFAULT_IGNORE_DIRS, ...extraIgnoreDirs]);
  const results = [];
  await walk(rootDir, ignoreDirs, results);
  return results;
}

async function walk(dir, ignoreDirs, results) {
  let entries;
  try {
    entries = await fs.readdir(dir, { withFileTypes: true });
  } catch {
    return;
  }

  const foundMarkers = entries
    .filter((entry) => entry.isFile() && entry.name in PROJECT_MARKERS)
    .map((entry) => entry.name);

  if (foundMarkers.length > 0) {
    results.push({ dir, files: foundMarkers });
    return;
  }

  const subDirs = entries.filter((entry) => entry.isDirectory() && !ignoreDirs.has(entry.name));
  for (const subDir of subDirs) {
    await walk(path.join(dir, subDir.name), ignoreDirs, results);
  }
}
