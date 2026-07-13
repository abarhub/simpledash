import fs from 'node:fs/promises';
import path from 'node:path';
import { XMLParser } from 'fast-xml-parser';

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
  return {
    name: pkg.name ?? null,
    version: pkg.version ?? null,
    dependencies: pkg.dependencies ?? {},
    devDependencies: pkg.devDependencies ?? {},
  };
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

// Analyse un répertoire de projet : lit pom.xml et/ou package.json s'ils sont
// présents, et suit récursivement les <modules> déclarés dans le pom (un
// module peut lui-même contenir un package.json, ex: frontend embarqué).
// Ne résout pas l'héritage Maven complet (pas d'effective-pom) : les valeurs
// pilotées par des propriétés ou un BOM peuvent rester non résolues.
export async function analyzeProject(dir) {
  const pomPath = path.join(dir, 'pom.xml');
  const packageJsonPath = path.join(dir, 'package.json');

  const pom = (await fileExists(pomPath)) ? await parsePom(pomPath) : null;
  const npm = (await fileExists(packageJsonPath)) ? await parsePackageJson(packageJsonPath) : null;

  const modules = [];
  if (pom) {
    for (const moduleName of pom.modules) {
      const moduleDir = path.join(dir, moduleName);
      if (await fileExists(path.join(moduleDir, 'pom.xml'))) {
        modules.push(await analyzeProject(moduleDir));
      }
    }
  }

  return {
    dir,
    pom,
    npm,
    summary: buildSummary(pom, npm),
    modules,
  };
}
