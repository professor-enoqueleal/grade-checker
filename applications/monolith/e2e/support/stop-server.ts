import { execSync } from 'child_process';

/**
 * Global teardown: stops the `mvn jetty:run` started by the suite with SIGTERM and waits for it to exit.
 *
 * Playwright 1.47 always stops config.webServer with SIGKILL, which skips the application shutdown: the H2
 * database is then never closed and the last writes may be lost (openspec change fechar-banco-ao-desligar).
 * Only the process listening on E2E_PORT whose command line is the suite's `jetty:run -Djetty.http.port=<port>`
 * is stopped; any other server (e.g. the IntelliJ instance on 8080) is left alone.
 */
export default async function stopServer(): Promise<void> {
  const port = process.env.E2E_PORT ?? '8099';

  let pids: number[];
  try {
    pids = execSync(`lsof -ti tcp:${port} -sTCP:LISTEN`, { stdio: ['ignore', 'pipe', 'ignore'] })
      .toString().trim().split(/\s+/).filter(Boolean).map(Number);
  } catch {
    return; // nothing listening, or lsof not available
  }

  for (const pid of pids) {
    let command = '';
    try {
      command = execSync(`ps -o command= -p ${pid}`, { stdio: ['ignore', 'pipe', 'ignore'] }).toString();
    } catch {
      continue;
    }
    if (!command.includes('jetty:run') || !command.includes(`-Djetty.http.port=${port}`)) continue;

    process.kill(pid, 'SIGTERM');
    for (let i = 0; i < 30 && alive(pid); i++) await new Promise((r) => setTimeout(r, 500));
  }
}

function alive(pid: number): boolean {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}
