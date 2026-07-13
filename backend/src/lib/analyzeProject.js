import fs from 'node:fs/promises';
import path from 'node:path';
import { XMLParser } from 'fast-xml-parser';
import { parse as parseToml } from 'smol-toml';

const PROJECT_MARKER_FILES = ['pom.xml', 'package.json', 'Cargo.toml', 'go.mod'];

const xmlParser = new XMLParser({
  ignoreAttributes: true,
  parseTagValue: false,
  isArray: (name, jpath) =>
    jpath === 'project.dependencies.dependency' || jpath === 'project.modules.module',
});

async function fileExists(filePath) {
  try {
    await fs.access(filePath);
    return true;
  } catch {
    return false;
  }
}

async function hasAnyProjectMarker(dir) {
  for (const marker of PROJECT_MARKER_FILES) {
    if (await fileExists(path.join(dir, marker))) return true;
  }
  return false;
}

// Un pattern comme "packages/*" n'est pas résolu (pas de moteur de glob) :
// on ne garde que les chemins littéraux déclarés.
function withoutGlobs(entries) {
  return entries.filter((entry) => !entry.includes('*'));
}

async function parsePom(pomPath) {
  const raw = await fs.readFile(pomPath, 'utf-8');
  const project = xmlParser.parse(raw).project ?? {};

  const parent = project.parent
    ? {
        groupId: project.parent.groupId ?? null,
        artifactId: project.parent.artifactId ?? null,
        version: project.parent.version ?? null,
      }
    : null;

  const dependencies = (project.dependencies?.dependency ?? []).map((dep) => ({
    groupId: dep.groupId ?? null,
    artifactId: dep.artifactId ?? null,
    version: dep.version ?? null,
    scope: dep.scope ?? null,
  }));

  return {
    parent,
    groupId: project.groupId ?? parent?.groupId ?? null,
    artifactId: project.artifactId ?? null,
    version: project.version ?? parent?.version ?? null,
    properties: project.properties ?? {},
    dependencies,
    modules: project.modules?.module ?? [],
  };
}

async function parsePackageJson(packageJsonPath) {
  const raw = await fs.readFile(packageJsonPath, 'utf-8');
  const pkg = JSON.parse(raw);
  const workspaces = Array.isArray(pkg.workspaces)
    ? pkg.workspaces
    : (pkg.workspaces?.packages ?? []);

  return {
    name: pkg.name ?? null,
    version: pkg.version ?? null,
    dependencies: pkg.dependencies ?? {},
    devDependencies: pkg.devDependencies ?? {},
    workspaces,
  };
}

async function parseCargoToml(cargoTomlPath) {
  const raw = await fs.readFile(cargoTomlPath, 'utf-8');
  const data = parseToml(raw);

  const dependencies = Object.fromEntries(
    Object.entries(data.dependencies ?? {}).map(([name, value]) => [
      name,
      typeof value === 'string' ? value : (value.version ?? null),
    ])
  );

  return {
    name: data.package?.name ?? null,
    version: data.package?.version ?? null,
    dependencies,
    workspaceMembers: data.workspace?.members ?? [],
  };
}

// go.mod n'est ni du JSON ni du TOML : on le parse ligne à ligne, sans
// dépendance (le format est simple et stable).
async function parseGoMod(goModPath) {
  const raw = await fs.readFile(goModPath, 'utf-8');
  const lines = raw.split('\n').map((l) => l.trim());

  const moduleLine = lines.find((l) => l.startsWith('module '));
  const goLine = lines.find((l) => /^go\s+\d/.test(l));

  const dependencies = {};
  let inRequireBlock = false;
  for (const line of lines) {
    if (line.startsWith('require (')) {
      inRequireBlock = true;
      continue;
    }
    if (inRequireBlock) {
      if (line === ')') {
        inRequireBlock = false;
        continue;
      }
      const [modPath, version] = line.split(/\s+/);
      if (modPath && version) dependencies[modPath] = version;
      continue;
    }
    const singleLineMatch = line.match(/^require\s+(\S+)\s+(\S+)/);
    if (singleLineMatch) {
      dependencies[singleLineMatch[1]] = singleLineMatch[2];
    }
  }

  return {
    module: moduleLine ? moduleLine.replace('module ', '').trim() : null,
    goVersion: goLine ? goLine.replace('go ', '').trim() : null,
    dependencies,
  };
}

// go.work déclare les modules d'un workspace via des directives `use`.
async function parseGoWorkMembers(goWorkPath) {
  const raw = await fs.readFile(goWorkPath, 'utf-8');
  const lines = raw.split('\n').map((l) => l.trim());

  const members = [];
  let inUseBlock = false;
  for (const line of lines) {
    if (line.startsWith('use (')) {
      inUseBlock = true;
      continue;
    }
    if (inUseBlock) {
      if (line === ')') {
        inUseBlock = false;
        continue;
      }
      if (line) members.push(line);
      continue;
    }
    const singleLineMatch = line.match(/^use\s+(\S+)/);
    if (singleLineMatch) members.push(singleLineMatch[1]);
  }

  return members;
}

function buildSummary(pom, npm) {
  const summary = {};

  if (pom) {
    const javaVersion =
      pom.properties['java.version'] ??
      pom.properties['maven.compiler.release'] ??
      pom.properties['maven.compiler.source'] ??
      null;
    if (javaVersion) summary.javaVersion = javaVersion;

    const springBootDep = pom.dependencies.find(
      (dep) => dep.groupId === 'org.springframework.boot' && dep.version
    );
    const springBootVersion =
      pom.parent?.artifactId === 'spring-boot-starter-parent'
        ? pom.parent.version
        : (pom.properties['spring-boot.version'] ?? springBootDep?.version ?? null);
    if (springBootVersion) summary.springBootVersion = springBootVersion;
  }

  if (npm) {
    const angularVersion = npm.dependencies?.['@angular/core'];
    if (angularVersion) summary.angularVersion = angularVersion;
  }

  return summary;
}

// Analyse un répertoire de projet : lit pom.xml, package.json, Cargo.toml
// et/ou go.mod s'ils sont présents (plusieurs peuvent coexister, ex: un
// module Maven avec un frontend npm à côté), et suit récursivement les
// sous-modules déclarés par chaque écosystème :
//   - Maven   : <modules> du pom.xml
//   - npm     : champ "workspaces" du package.json
//   - Cargo   : [workspace].members du Cargo.toml
//   - Go      : directives "use" d'un go.work, s'il existe
// Les patterns glob ("packages/*") ne sont pas résolus, seuls les chemins
// littéraux sont suivis. Ne résout pas l'héritage Maven complet (pas
// d'effective-pom) : les valeurs pilotées par des propriétés ou un BOM
// peuvent rester non résolues.
export async function analyzeProject(dir) {
  const pomPath = path.join(dir, 'pom.xml');
  const packageJsonPath = path.join(dir, 'package.json');
  const cargoTomlPath = path.join(dir, 'Cargo.toml');
  const goModPath = path.join(dir, 'go.mod');
  const goWorkPath = path.join(dir, 'go.work');

  const pom = (await fileExists(pomPath)) ? await parsePom(pomPath) : null;
  const npm = (await fileExists(packageJsonPath)) ? await parsePackageJson(packageJsonPath) : null;
  const rust = (await fileExists(cargoTomlPath)) ? await parseCargoToml(cargoTomlPath) : null;
  const go = (await fileExists(goModPath)) ? await parseGoMod(goModPath) : null;
  const goWorkMembers = (await fileExists(goWorkPath)) ? await parseGoWorkMembers(goWorkPath) : [];

  const declaredModulePaths = new Set([
    ...(pom?.modules ?? []),
    ...withoutGlobs(npm?.workspaces ?? []),
    ...withoutGlobs(rust?.workspaceMembers ?? []),
    ...withoutGlobs(goWorkMembers),
  ]);

  const modules = [];
  for (const modulePath of declaredModulePaths) {
    const moduleDir = path.join(dir, modulePath);
    if (await hasAnyProjectMarker(moduleDir)) {
      modules.push(await analyzeProject(moduleDir));
    }
  }

  return {
    dir,
    pom,
    npm,
    rust,
    go,
    summary: buildSummary(pom, npm),
    modules,
  };
}
