import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);
const { default: gitInfo } = await import('./git-info.js');

async function withTempDir(fn) {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'git-info-'));
  try {
    await fn(dir);
  } finally {
    // maxRetries/retryDelay : sous Windows, un handle sur un sous-processus
    // git tout juste terminé peut brièvement retarder la suppression.
    await fs.rm(dir, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
  }
}

async function git(args, cwd) {
  await execFileAsync('git', args, { cwd });
}

async function initRepo(dir) {
  await git(['init', '-b', 'main'], dir);
  await git(['config', 'user.email', 'test@example.com'], dir);
  await git(['config', 'user.name', 'Test'], dir);
}

async function commit(dir, message) {
  await git(['add', '-A'], dir);
  await git(['commit', '-m', message], dir);
}

test('dépôt propre, sans remote', async () => {
  await withTempDir(async (dir) => {
    await initRepo(dir);
    await fs.writeFile(path.join(dir, 'a.txt'), 'hello');
    await commit(dir, 'Premier commit');

    const [widget] = await gitInfo.fetch({ path: dir });

    assert.equal(widget.title, 'Git');
    assert.equal(widget.data.Message, 'Premier commit');
    assert.match(widget.data['Dernier commit'], /^[0-9a-f]{7,}$/);
    assert.equal(widget.data.Branche, 'main');
    assert.equal(widget.data['Modifs non commitées'], 'Non');
    assert.equal(widget.data['Vs remote'], 'pas de remote suivi');
    assert.equal(widget.data['Branches contenant ce commit'], 'main');
    assert.equal(widget.url, undefined);
  });
});

test('détecte les modifications non commitées', async () => {
  await withTempDir(async (dir) => {
    await initRepo(dir);
    await fs.writeFile(path.join(dir, 'a.txt'), 'hello');
    await commit(dir, 'Premier commit');
    await fs.writeFile(path.join(dir, 'a.txt'), 'modifié');

    const [widget] = await gitInfo.fetch({ path: dir });
    assert.equal(widget.data['Modifs non commitées'], 'Oui');
  });
});

test('liste plusieurs branches quand le commit y est présent', async () => {
  await withTempDir(async (dir) => {
    await initRepo(dir);
    await fs.writeFile(path.join(dir, 'a.txt'), 'hello');
    await commit(dir, 'Premier commit');
    await git(['branch', 'feature-x'], dir);

    const [widget] = await gitInfo.fetch({ path: dir });
    const branches = widget.data['Branches contenant ce commit'].split(', ').sort();
    assert.deepEqual(branches, ['feature-x', 'main']);
  });
});

test('construit le lien web depuis un remote HTTPS', async () => {
  await withTempDir(async (dir) => {
    await initRepo(dir);
    await fs.writeFile(path.join(dir, 'a.txt'), 'hello');
    await commit(dir, 'Premier commit');
    await git(['remote', 'add', 'origin', 'https://github.com/abarhub/simpledash.git'], dir);

    const [widget] = await gitInfo.fetch({ path: dir });
    assert.equal(widget.url, 'https://github.com/abarhub/simpledash');
  });
});

test('construit le lien web depuis un remote SSH', async () => {
  await withTempDir(async (dir) => {
    await initRepo(dir);
    await fs.writeFile(path.join(dir, 'a.txt'), 'hello');
    await commit(dir, 'Premier commit');
    await git(['remote', 'add', 'origin', 'git@github.com:abarhub/simpledash.git'], dir);

    const [widget] = await gitInfo.fetch({ path: dir });
    assert.equal(widget.url, 'https://github.com/abarhub/simpledash');
  });
});

test('avance/retard par rapport à la branche amont suivie', async () => {
  await withTempDir(async (bareDir) => {
    await git(['init', '--bare', '-b', 'main'], bareDir);

    await withTempDir(async (dir) => {
      await initRepo(dir);
      await fs.writeFile(path.join(dir, 'a.txt'), 'hello');
      await commit(dir, 'Premier commit');
      await git(['remote', 'add', 'origin', bareDir], dir);
      await git(['push', '-u', 'origin', 'main'], dir);

      await fs.writeFile(path.join(dir, 'b.txt'), 'world');
      await commit(dir, 'Deuxième commit');

      const [widget] = await gitInfo.fetch({ path: dir });
      assert.equal(widget.data['Vs remote'], '1 en avance, 0 en retard');
    });
  });
});

test("rejette si le répertoire n'est pas un dépôt git", async () => {
  await withTempDir(async (dir) => {
    await assert.rejects(() => gitInfo.fetch({ path: dir }));
  });
});
