import os from 'node:os';

function formatBytes(bytes) {
  return `${(bytes / 1024 ** 3).toFixed(2)} Go`;
}

function formatDuration(seconds) {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  return `${h}h ${m}min`;
}

export default {
  id: 'system',
  name: 'Infos système',
  description: 'Mémoire, CPU et uptime (une sélection => plusieurs cards)',

  async fetch() {
    const totalMem = os.totalmem();
    const freeMem = os.freemem();
    const cpus = os.cpus();

    return [
      {
        id: 'system-memory',
        title: 'Mémoire',
        data: {
          Totale: formatBytes(totalMem),
          Utilisée: formatBytes(totalMem - freeMem),
          Libre: formatBytes(freeMem),
        },
      },
      {
        id: 'system-cpu',
        title: 'CPU',
        data: {
          Modèle: cpus[0]?.model ?? 'inconnu',
          Cœurs: cpus.length,
          'Charge (1 min)': os.loadavg()[0].toFixed(2),
        },
      },
      {
        id: 'system-uptime',
        title: 'Uptime',
        data: {
          Système: formatDuration(os.uptime()),
          Processus: formatDuration(process.uptime()),
        },
      },
    ];
  },
};
