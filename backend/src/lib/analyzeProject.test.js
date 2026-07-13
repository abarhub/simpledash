import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { analyzeProject } from './analyzeProject.js';

async function withTempDir(fn) {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'analyzeProject-'));
  try {
    await fn(dir);
  } finally {
    await fs.rm(dir, { recursive: true, force: true });
  }
}

test('répertoire sans pom.xml ni package.json', async () => {
  await withTempDir(async (dir) => {
    const result = await analyzeProject(dir);
    assert.equal(result.pom, null);
    assert.equal(result.npm, null);
    assert.deepEqual(result.summary, {});
    assert.deepEqual(result.modules, []);
  });
});

test('package.json seul', async () => {
  await withTempDir(async (dir) => {
    await fs.writeFile(
      path.join(dir, 'package.json'),
      JSON.stringify({
        name: 'my-frontend',
        version: '0.1.0',
        dependencies: { '@angular/core': '17.0.2' },
      })
    );

    const result = await analyzeProject(dir);
    assert.equal(result.pom, null);
    assert.equal(result.npm.name, 'my-frontend');
    assert.equal(result.npm.version, '0.1.0');
    assert.deepEqual(result.summary.angularVersion, ['17.0.2']);
  });
});

test('pom.xml simple sans parent', async () => {
  await withTempDir(async (dir) => {
    await fs.writeFile(
      path.join(dir, 'pom.xml'),
      `<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>simple-app</artifactId>
  <version>1.2.3</version>
  <properties>
    <java.version>17</java.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.apache.commons</groupId>
      <artifactId>commons-lang3</artifactId>
      <version>3.14.0</version>
    </dependency>
  </dependencies>
</project>`
    );

    const result = await analyzeProject(dir);
    assert.equal(result.pom.parent, null);
    assert.equal(result.pom.groupId, 'com.example');
    assert.equal(result.pom.artifactId, 'simple-app');
    assert.equal(result.pom.version, '1.2.3');
    assert.equal(result.pom.properties['java.version'], '17');
    assert.deepEqual(result.pom.dependencies, [
      { groupId: 'org.apache.commons', artifactId: 'commons-lang3', version: '3.14.0', scope: null },
    ]);
    assert.deepEqual(result.summary.javaVersion, ['17']);
    assert.deepEqual(result.modules, []);
  });
});

test('pom.xml avec parent spring-boot-starter-parent', async () => {
  await withTempDir(async (dir) => {
    await fs.writeFile(
      path.join(dir, 'pom.xml'),
      `<project>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.1</version>
  </parent>
  <artifactId>spring-app</artifactId>
  <properties>
    <java.version>21</java.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
  </dependencies>
</project>`
    );

    const result = await analyzeProject(dir);
    assert.equal(result.pom.parent.artifactId, 'spring-boot-starter-parent');
    // groupId/version hérités du parent car absents du pom enfant
    assert.equal(result.pom.groupId, 'org.springframework.boot');
    assert.equal(result.pom.version, '3.2.1');
    assert.equal(result.pom.artifactId, 'spring-app');
    assert.equal(result.pom.dependencies[0].version, null);
    assert.deepEqual(result.summary.springBootVersion, ['3.2.1']);
    assert.deepEqual(result.summary.javaVersion, ['21']);
  });
});

test('pom.xml multi-module avec un module contenant un package.json', async () => {
  await withTempDir(async (dir) => {
    await fs.writeFile(
      path.join(dir, 'pom.xml'),
      `<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>multi-module</artifactId>
  <version>1.0.0</version>
  <packaging>pom</packaging>
  <modules>
    <module>module-core</module>
    <module>module-web</module>
  </modules>
</project>`
    );

    await fs.mkdir(path.join(dir, 'module-core'));
    await fs.writeFile(
      path.join(dir, 'module-core', 'pom.xml'),
      `<project>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>multi-module</artifactId>
    <version>1.0.0</version>
  </parent>
  <artifactId>module-core</artifactId>
</project>`
    );

    await fs.mkdir(path.join(dir, 'module-web'));
    await fs.writeFile(
      path.join(dir, 'module-web', 'pom.xml'),
      `<project>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>multi-module</artifactId>
    <version>1.0.0</version>
  </parent>
  <artifactId>module-web</artifactId>
</project>`
    );
    await fs.writeFile(
      path.join(dir, 'module-web', 'package.json'),
      JSON.stringify({
        name: 'module-web-frontend',
        version: '0.1.0',
        dependencies: { '@angular/core': '17.0.2' },
      })
    );

    const result = await analyzeProject(dir);
    assert.deepEqual(result.pom.modules, ['module-core', 'module-web']);
    assert.equal(result.modules.length, 2);

    const [core, web] = result.modules;
    assert.equal(core.pom.artifactId, 'module-core');
    assert.equal(core.pom.groupId, 'com.example');
    assert.equal(core.pom.version, '1.0.0');
    assert.equal(core.npm, null);

    assert.equal(web.pom.artifactId, 'module-web');
    assert.equal(web.npm.name, 'module-web-frontend');
    assert.deepEqual(web.summary.angularVersion, ['17.0.2']);

    // la racine (pom seul, pas de package.json) remonte quand même
    // l'Angular détecté dans le sous-module
    assert.deepEqual(result.summary.angularVersion, ['17.0.2']);
  });
});

test('summary : une version Java différente sur un sous-module vient s\'ajouter à celle de la racine', async () => {
  await withTempDir(async (dir) => {
    await fs.writeFile(
      path.join(dir, 'pom.xml'),
      `<project>
  <groupId>com.example</groupId>
  <artifactId>multi-module</artifactId>
  <version>1.0.0</version>
  <packaging>pom</packaging>
  <properties>
    <java.version>21</java.version>
  </properties>
  <modules>
    <module>module-a</module>
    <module>module-b</module>
  </modules>
</project>`
    );

    await fs.mkdir(path.join(dir, 'module-a'));
    await fs.writeFile(
      path.join(dir, 'module-a', 'pom.xml'),
      `<project>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>multi-module</artifactId>
    <version>1.0.0</version>
  </parent>
  <artifactId>module-a</artifactId>
  <properties>
    <java.version>21</java.version>
  </properties>
</project>`
    );

    await fs.mkdir(path.join(dir, 'module-b'));
    await fs.writeFile(
      path.join(dir, 'module-b', 'pom.xml'),
      `<project>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>multi-module</artifactId>
    <version>1.0.0</version>
  </parent>
  <artifactId>module-b</artifactId>
  <properties>
    <java.version>25</java.version>
  </properties>
</project>`
    );

    const result = await analyzeProject(dir);
    assert.deepEqual(result.summary.javaVersion, ['21', '25']);
  });
});

test('Cargo.toml seul', async () => {
  await withTempDir(async (dir) => {
    await fs.writeFile(
      path.join(dir, 'Cargo.toml'),
      `[package]
name = "my-crate"
version = "0.3.1"
rust-version = "1.75"

[dependencies]
serde = "1.0"
tokio = { version = "1.35", features = ["full"] }
`
    );

    const result = await analyzeProject(dir);
    assert.equal(result.rust.name, 'my-crate');
    assert.equal(result.rust.version, '0.3.1');
    assert.equal(result.rust.rustVersion, '1.75');
    assert.deepEqual(result.rust.dependencies, { serde: '1.0', tokio: '1.35' });
    assert.deepEqual(result.summary.rustVersion, ['1.75']);
    assert.deepEqual(result.modules, []);
  });
});

test('go.mod seul, avec require en bloc et en ligne simple', async () => {
  await withTempDir(async (dir) => {
    await fs.writeFile(
      path.join(dir, 'go.mod'),
      `module github.com/example/my-service

go 1.21

require github.com/gin-gonic/gin v1.9.1

require (
    github.com/foo/bar v1.2.3
    github.com/baz/qux v0.5.0 // indirect
)
`
    );

    const result = await analyzeProject(dir);
    assert.equal(result.go.module, 'github.com/example/my-service');
    assert.equal(result.go.goVersion, '1.21');
    assert.deepEqual(result.go.dependencies, {
      'github.com/gin-gonic/gin': 'v1.9.1',
      'github.com/foo/bar': 'v1.2.3',
      'github.com/baz/qux': 'v0.5.0',
    });
    assert.deepEqual(result.summary.goVersion, ['1.21']);
  });
});

test('workspaces npm : un membre déclaré littéralement est analysé', async () => {
  await withTempDir(async (dir) => {
    await fs.writeFile(
      path.join(dir, 'package.json'),
      JSON.stringify({ name: 'root', version: '1.0.0', workspaces: ['packages/pkg-a'] })
    );
    await fs.mkdir(path.join(dir, 'packages', 'pkg-a'), { recursive: true });
    await fs.writeFile(
      path.join(dir, 'packages', 'pkg-a', 'package.json'),
      JSON.stringify({ name: 'pkg-a', version: '0.1.0' })
    );

    const result = await analyzeProject(dir);
    assert.equal(result.modules.length, 1);
    assert.equal(result.modules[0].npm.name, 'pkg-a');
  });
});

test('members Cargo workspace : un membre déclaré littéralement est analysé', async () => {
  await withTempDir(async (dir) => {
    await fs.writeFile(
      path.join(dir, 'Cargo.toml'),
      `[workspace]
members = ["crates/crate-a"]
`
    );
    await fs.mkdir(path.join(dir, 'crates', 'crate-a'), { recursive: true });
    await fs.writeFile(
      path.join(dir, 'crates', 'crate-a', 'Cargo.toml'),
      `[package]
name = "crate-a"
version = "0.1.0"
`
    );

    const result = await analyzeProject(dir);
    assert.equal(result.modules.length, 1);
    assert.equal(result.modules[0].rust.name, 'crate-a');
  });
});

test('go.work : les modules listés via "use" sont analysés, même sans go.mod à la racine', async () => {
  await withTempDir(async (dir) => {
    await fs.writeFile(
      path.join(dir, 'go.work'),
      `go 1.21

use (
    ./service-a
    ./service-b
)
`
    );
    await fs.mkdir(path.join(dir, 'service-a'));
    await fs.writeFile(path.join(dir, 'service-a', 'go.mod'), 'module github.com/example/service-a\n\ngo 1.21\n');
    await fs.mkdir(path.join(dir, 'service-b'));
    await fs.writeFile(path.join(dir, 'service-b', 'go.mod'), 'module github.com/example/service-b\n\ngo 1.21\n');

    const result = await analyzeProject(dir);
    assert.equal(result.go, null);
    assert.equal(result.modules.length, 2);
    assert.deepEqual(
      result.modules.map((m) => m.go.module).sort(),
      ['github.com/example/service-a', 'github.com/example/service-b']
    );
  });
});

test('un pattern glob dans workspaces n\'est pas résolu', async () => {
  await withTempDir(async (dir) => {
    await fs.writeFile(
      path.join(dir, 'package.json'),
      JSON.stringify({ name: 'root', version: '1.0.0', workspaces: ['packages/*'] })
    );
    await fs.mkdir(path.join(dir, 'packages', 'pkg-a'), { recursive: true });
    await fs.writeFile(
      path.join(dir, 'packages', 'pkg-a', 'package.json'),
      JSON.stringify({ name: 'pkg-a', version: '0.1.0' })
    );

    const result = await analyzeProject(dir);
    assert.deepEqual(result.modules, []);
  });
});
